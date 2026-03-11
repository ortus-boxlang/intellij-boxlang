package com.ortussolutions.intellijboxlang.lsp;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerListener;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.execution.ParametersListUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.ortussolutions.intellijboxlang.file.BoxLangFileUtil;
import com.ortussolutions.intellijboxlang.runtime.BoxLangLspBootstrapService;
import com.ortussolutions.intellijboxlang.runtime.LspBootstrapResult;
import com.ortussolutions.intellijboxlang.settings.BoxLangResolvedSettings;
import com.ortussolutions.intellijboxlang.settings.BoxLangSettingsResolver;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.DeclarationParams;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensCapabilities;
import org.eclipse.lsp4j.SemanticTokensClientCapabilitiesRequests;
import org.eclipse.lsp4j.SemanticTokensLegend;
import org.eclipse.lsp4j.SemanticTokensParams;
import org.eclipse.lsp4j.TextDocumentClientCapabilities;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageServer;
import org.jetbrains.annotations.Nullable;

@Service( Service.Level.PROJECT )
public final class BoxLangLspClientService {

	private static final Logger										LOG									= Logger.getInstance( BoxLangLspClientService.class );
	private static final int										CONNECT_TIMEOUT_MS					= 10000;
	private static final int										REQUEST_TIMEOUT_MS					= 5000;
	private static final int										INITIALIZE_TIMEOUT_MS				= 20000;
	private static final List<String>								SUPPORTED_SEMANTIC_TOKEN_TYPES		= List.of(
	    "namespace",
	    "type",
	    "class",
	    "enum",
	    "interface",
	    "struct",
	    "typeParameter",
	    "parameter",
	    "variable",
	    "property",
	    "enumMember",
	    "event",
	    "function",
	    "method",
	    "macro",
	    "keyword",
	    "modifier",
	    "comment",
	    "string",
	    "number",
	    "regexp",
	    "operator",
	    "decorator",
	    "tag"
	);
	private static final List<String>								SUPPORTED_SEMANTIC_TOKEN_MODIFIERS	= List.of(
	    "declaration",
	    "definition",
	    "readonly",
	    "static",
	    "deprecated",
	    "abstract",
	    "async",
	    "modification",
	    "documentation",
	    "defaultLibrary"
	);

	private final Project											project;
	private final Map<String, Integer>								documentVersions					= new ConcurrentHashMap<>();
	private final Map<String, Long>									documentStamps						= new ConcurrentHashMap<>();
	private final Map<String, CachedTokens>							tokenCache							= new ConcurrentHashMap<>();
	private final Map<String, CachedSymbols>						symbolCache							= new ConcurrentHashMap<>();
	private final Map<String, List<org.eclipse.lsp4j.Diagnostic>>	diagnostics							= new ConcurrentHashMap<>();
	private final java.util.concurrent.atomic.AtomicBoolean			starting							= new java.util.concurrent.atomic.AtomicBoolean(
	    false );
	private final Object											startLock							= new Object();
	private boolean													diagnosticPullSupported				= false;
	private boolean													semanticTokensSupported				= false;
	private boolean													workspaceSymbolsSupported			= false;
	private boolean													workspaceSymbolsDisabledLogged		= false;
	private boolean													declarationSupported				= false;
	private boolean													declarationDisabledLogged			= false;

	private Process													process;
	private Socket													socket;
	private LanguageServer											server;
	private SemanticTokensLegend									legend;

	public BoxLangLspClientService( Project project ) {
		this.project = project;
		MessageBusConnection connection = project.getMessageBus().connect();
		connection.subscribe( FileDocumentManagerListener.TOPIC, new FileDocumentManagerListener() {

			@Override
			public void beforeDocumentSaving( Document document ) {
				VirtualFile file = FileDocumentManager.getInstance().getFile( document );
				if ( file != null && isBoxLangFile( file ) ) {
					notifyDidSave( file, document );
				}
			}
		} );
	}

	public static BoxLangLspClientService getInstance( Project project ) {
		return project.getService( BoxLangLspClientService.class );
	}

	public SemanticTokensLegend getLegend() {
		return legend;
	}

	public SemanticTokens requestSemanticTokens( VirtualFile file, Document document ) {
		if ( !ensureStarted() ) {
			LOG.info( "Skipping semantic tokens request: LSP server not started yet" );
			return null;
		}
		if ( !semanticTokensSupported || legend == null ) {
			LOG.info(
			    "Skipping semantic tokens request: supported=" + semanticTokensSupported
			        + ", legendPresent=" + ( legend != null )
			);
			return null;
		}
		String uri = safeToUri( file );
		if ( uri == null ) {
			LOG.info( "Skipping semantic tokens request: unable to resolve document URI from VirtualFile" );
			return null;
		}
		CachedTokens cached = tokenCache.get( uri );
		if ( cached != null && cached.stamp == document.getModificationStamp() ) {
			LOG.info(
			    "Using cached semantic tokens: uri='" + uri + "', tuples=" + tokenTupleCount( cached.tokens )
			        + ", stamp=" + cached.stamp
			);
			return cached.tokens;
		}
		syncDocument( uri, document );
		SemanticTokensParams params = new SemanticTokensParams( new TextDocumentIdentifier( uri ) );
		try {
			CompletableFuture<SemanticTokens>	future	= server.getTextDocumentService().semanticTokensFull( params );
			SemanticTokens						tokens	= future.get( REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS );
			if ( tokens != null ) {
				tokenCache.put( uri, new CachedTokens( document.getModificationStamp(), tokens ) );
				int	legendTypes	= legend.getTokenTypes() != null ? legend.getTokenTypes().size() : 0;
				int	legendMods	= legend.getTokenModifiers() != null ? legend.getTokenModifiers().size() : 0;
				LOG.info(
				    "LSP semanticTokens/full success: uri='" + uri + "', tuples=" + tokenTupleCount( tokens )
				        + ", ints=" + tokens.getData().size() + ", legendTypes=" + legendTypes + ", legendModifiers="
				        + legendMods
				);
			} else {
				LOG.info( "LSP semanticTokens/full returned null: uri='" + uri + "'" );
			}
			return tokens;
		} catch ( Exception e ) {
			logLspRequestFailure( "textDocument/semanticTokens/full", "uri='" + uri + "'", e );
			return null;
		}
	}

	@SuppressWarnings( "deprecation" )
	public List<org.eclipse.lsp4j.WorkspaceSymbol> requestWorkspaceSymbols( String query ) {
		if ( !ensureStarted() ) {
			return List.of();
		}
		if ( !workspaceSymbolsSupported ) {
			return List.of();
		}
		org.eclipse.lsp4j.WorkspaceSymbolParams params = new org.eclipse.lsp4j.WorkspaceSymbolParams( query );
		try {
			var																												future	= server
			    .getWorkspaceService().symbol( params );
			Either<List<? extends org.eclipse.lsp4j.SymbolInformation>, List<? extends org.eclipse.lsp4j.WorkspaceSymbol>>	result	= future
			    .get( REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS );
			if ( result == null ) {
				LOG.debug( "LSP workspace/symbol returned null result for query='" + query + "'" );
				return List.of();
			}
			List<org.eclipse.lsp4j.WorkspaceSymbol> symbols = new ArrayList<>();
			if ( result.isLeft() ) {
				for ( org.eclipse.lsp4j.SymbolInformation legacy : result.getLeft() ) {
					org.eclipse.lsp4j.WorkspaceSymbol mapped = toWorkspaceSymbol( legacy );
					if ( mapped != null ) {
						symbols.add( mapped );
					}
				}
			} else if ( result.isRight() ) {
				symbols.addAll( result.getRight() );
			}
			logWorkspaceSymbolsResponse( query, result, symbols );
			return symbols;
		} catch ( Exception e ) {
			if ( isUnsupportedWorkspaceSymbolError( e ) ) {
				workspaceSymbolsSupported = false;
				if ( !workspaceSymbolsDisabledLogged ) {
					workspaceSymbolsDisabledLogged = true;
					LOG.warn(
					    "LSP workspace/symbol appears unsupported by the running server instance; disabling symbol requests for this session."
					);
				}
			}
			logLspRequestFailure( "workspace/symbol", "query='" + query + "'", e );
			return List.of();
		}
	}

	public List<org.eclipse.lsp4j.DocumentSymbol> requestDocumentSymbols( VirtualFile file, Document document ) {
		if ( !ensureStarted() ) {
			return List.of();
		}
		String uri = safeToUri( file );
		if ( uri == null ) {
			return List.of();
		}
		CachedSymbols cached = symbolCache.get( uri );
		if ( cached != null && cached.stamp == document.getModificationStamp() ) {
			return cached.symbols;
		}
		syncDocument( uri, document );
		org.eclipse.lsp4j.DocumentSymbolParams params = new org.eclipse.lsp4j.DocumentSymbolParams( new TextDocumentIdentifier( uri ) );
		try {
			var										future	= server.getTextDocumentService().documentSymbol( params );
			var										result	= future.get( REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS );
			List<org.eclipse.lsp4j.DocumentSymbol>	symbols	= mapDocumentSymbols( result );
			symbolCache.put( uri, new CachedSymbols( document.getModificationStamp(), symbols ) );
			return symbols;
		} catch ( Exception e ) {
			LOG.debug( "Failed to request document symbols", e );
			return List.of();
		}
	}

	public List<Location> requestDefinition( VirtualFile file, Document document, int offset ) {
		if ( !ensureStarted() ) {
			return List.of();
		}
		String uri = safeToUri( file );
		if ( uri == null ) {
			return List.of();
		}
		syncDocument( uri, document );
		Position position = positionAt( document, offset );

		try {
			DefinitionParams	params		= new DefinitionParams( new TextDocumentIdentifier( uri ), position );
			List<Location>		locations	= flattenLocations(
			    server.getTextDocumentService().definition( params ).get( REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS ) );
			if ( !locations.isEmpty() ) {
				LOG.debug( "LSP textDocument/definition success: uri='" + uri + "', offset=" + offset + ", locations=" + locations.size() );
				return locations;
			}
			LOG.debug( "LSP textDocument/definition returned no locations: uri='" + uri + "', offset=" + offset + "'" );
		} catch ( Exception e ) {
			logLspRequestFailure( "textDocument/definition", "uri='" + uri + "', offset=" + offset, e );
		}

		if ( !declarationSupported ) {
			return List.of();
		}

		try {
			DeclarationParams	params		= new DeclarationParams( new TextDocumentIdentifier( uri ), position );
			List<Location>		locations	= flattenLocations(
			    server.getTextDocumentService().declaration( params ).get( REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS ) );
			if ( !locations.isEmpty() ) {
				LOG.debug( "LSP textDocument/declaration success: uri='" + uri + "', offset=" + offset + ", locations=" + locations.size() );
			} else {
				LOG.debug( "LSP textDocument/declaration returned no locations: uri='" + uri + "', offset=" + offset + "'" );
			}
			return locations;
		} catch ( Exception e ) {
			if ( isUnsupportedDeclarationError( e ) ) {
				declarationSupported = false;
				if ( !declarationDisabledLogged ) {
					declarationDisabledLogged = true;
					LOG.warn(
					    "LSP textDocument/declaration appears unsupported by the running server instance; disabling declaration fallback for this session."
					);
				}
			}
			logLspRequestFailure( "textDocument/declaration", "uri='" + uri + "', offset=" + offset, e );
			return List.of();
		}
	}

	private boolean ensureStarted() {
		if ( server != null ) {
			return true;
		}
		if ( ApplicationManager.getApplication().isDispatchThread()
		    || ApplicationManager.getApplication().isReadAccessAllowed() ) {
			ensureStartedAsync();
			return false;
		}
		synchronized ( startLock ) {
			if ( server != null ) {
				return true;
			}
			try {
				startServer();
				return server != null;
			} catch ( Exception e ) {
				LOG.warn( "Unable to start BoxLang LSP", e );
				return false;
			}
		}
	}

	public void ensureStartedAsync() {
		if ( server != null ) {
			return;
		}
		if ( !starting.compareAndSet( false, true ) ) {
			return;
		}
		LOG.info( "BoxLang LSP async start requested" );
		ApplicationManager.getApplication().executeOnPooledThread( () -> {
			synchronized ( startLock ) {
				if ( server != null ) {
					starting.set( false );
					return;
				}
				try {
					LOG.info( "Starting BoxLang LSP process" );
					startServer();
					if ( server != null ) {
						LOG.info( "BoxLang LSP started" );
						requestEditorRehighlight();
					}
				} catch ( Exception e ) {
					LOG.warn( "Unable to start BoxLang LSP", e );
				} finally {
					starting.set( false );
				}
			}
		} );
	}

	private void startServer() throws Exception {
		BoxLangResolvedSettings	settings	= BoxLangSettingsResolver.resolve( project );
		LspBootstrapResult		bootstrap	= BoxLangLspBootstrapService.tryPrepareForLspClient( project );
		if ( bootstrap == null ) {
			// LSP is not available yet (prompt was shown if applicable); skip startup quietly.
			return;
		}
		int					port		= allocatePort();

		GeneralCommandLine	commandLine	= new GeneralCommandLine( resolveJavaExecutable( settings ) );
		commandLine.withCharset( StandardCharsets.UTF_8 );
		commandLine.withEnvironment( "BOXLANG_HOME", bootstrap.lspBoxLangHome.toString() );
		commandLine.withEnvironment( "BOXLANG_MODULESDIRECTORY", bootstrap.lspModulePath.toString() );
		commandLine.withEnvironment( "CLASSPATH", bootstrap.boxLangJarPath.toString() );
		if ( settings.javaHome != null && !settings.javaHome.isBlank() ) {
			commandLine.withEnvironment( "JAVA_HOME", settings.javaHome );
		}

		commandLine.addParameters( buildJvmArgs( settings ) );
		commandLine.addParameter( "ortus.boxlang.runtime.BoxRunner" );
		commandLine.addParameter( "module:bx-lsp" );
		commandLine.addParameters( "--debug-server-port", String.valueOf( port ) );

		process	= commandLine.createProcess();
		socket	= connectWithRetries( port );

		BoxLangLspClient	client		= new BoxLangLspClient( this );
		var					launcher	= LSPLauncher.createClientLauncher( client, socket.getInputStream(), socket.getOutputStream() );
		server = launcher.getRemoteProxy();
		launcher.startListening();

		InitializeResult result = server.initialize( createInitializeParams() )
		    .get( INITIALIZE_TIMEOUT_MS, TimeUnit.MILLISECONDS );
		legend					= result.getCapabilities() != null && result.getCapabilities().getSemanticTokensProvider() != null
		    ? result.getCapabilities().getSemanticTokensProvider().getLegend()
		    : null;
		semanticTokensSupported	= result.getCapabilities() != null && result.getCapabilities().getSemanticTokensProvider() != null;
		int	legendTypes	= legend != null && legend.getTokenTypes() != null ? legend.getTokenTypes().size() : 0;
		int	legendMods	= legend != null && legend.getTokenModifiers() != null ? legend.getTokenModifiers().size() : 0;
		LOG.info(
		    "LSP semantic tokens capability: supported=" + semanticTokensSupported + ", legendTypes=" + legendTypes + ", legendModifiers="
		        + legendMods
		);
		workspaceSymbolsSupported	= result.getCapabilities() != null && result.getCapabilities().getWorkspaceSymbolProvider() != null;
		declarationSupported		= isDeclarationProviderSupported( result );
		LOG.info( "LSP declaration capability: supported=" + declarationSupported );
		diagnosticPullSupported = result.getCapabilities() != null
		    && result.getCapabilities().getDiagnosticProvider() != null;
		server.initialized( new InitializedParams() );
	}

	private InitializeParams createInitializeParams() {
		InitializeParams params = new InitializeParams();
		params.setWorkspaceFolders( getWorkspaceFolders() );
		params.setCapabilities( createClientCapabilities() );
		params.setProcessId( ( int ) ProcessHandle.current().pid() );
		return params;
	}

	private ClientCapabilities createClientCapabilities() {
		ClientCapabilities							capabilities	= new ClientCapabilities();
		TextDocumentClientCapabilities				textDocument	= new TextDocumentClientCapabilities();
		SemanticTokensCapabilities					semanticTokens	= new SemanticTokensCapabilities();
		SemanticTokensClientCapabilitiesRequests	requests		= new SemanticTokensClientCapabilitiesRequests();
		requests.setFull( Either.forLeft( true ) );
		requests.setRange( Either.forLeft( false ) );
		semanticTokens.setRequests( requests );
		semanticTokens.setTokenTypes( SUPPORTED_SEMANTIC_TOKEN_TYPES );
		semanticTokens.setTokenModifiers( SUPPORTED_SEMANTIC_TOKEN_MODIFIERS );
		semanticTokens.setFormats( List.of( "relative" ) );
		semanticTokens.setOverlappingTokenSupport( true );
		semanticTokens.setMultilineTokenSupport( true );
		textDocument.setSemanticTokens( semanticTokens );
		capabilities.setTextDocument( textDocument );
		return capabilities;
	}

	private void syncDocument( String uri, Document document ) {
		long	stamp		= document.getModificationStamp();
		Long	cachedStamp	= documentStamps.get( uri );
		if ( cachedStamp != null && cachedStamp == stamp ) {
			return;
		}

		int version = documentVersions.getOrDefault( uri, 0 ) + 1;
		documentVersions.put( uri, version );
		documentStamps.put( uri, stamp );

		if ( version == 1 ) {
			TextDocumentItem item = new TextDocumentItem( uri, "boxlang", version, document.getText() );
			server.getTextDocumentService().didOpen( new org.eclipse.lsp4j.DidOpenTextDocumentParams( item ) );
			notifyDidSave( uri, document );
		} else {
			VersionedTextDocumentIdentifier	identifier	= new VersionedTextDocumentIdentifier( uri, version );
			TextDocumentContentChangeEvent	change		= new TextDocumentContentChangeEvent( document.getText() );
			server.getTextDocumentService().didChange(
			    new org.eclipse.lsp4j.DidChangeTextDocumentParams( identifier, List.of( change ) )
			);
		}
	}

	private void requestEditorRehighlight() {
		ApplicationManager.getApplication().invokeLater( () -> {
			if ( project.isDisposed() ) {
				return;
			}
			DaemonCodeAnalyzer	analyzer	= DaemonCodeAnalyzer.getInstance( project );
			PsiManager			psiManager	= PsiManager.getInstance( project );
			for ( VirtualFile openFile : FileEditorManager.getInstance( project ).getOpenFiles() ) {
				PsiFile psiFile = psiManager.findFile( openFile );
				if ( psiFile != null ) {
					analyzer.restart( psiFile );
				}
			}
		} );
	}

	private int allocatePort() throws IOException {
		try ( ServerSocket socket = new ServerSocket( 0 ) ) {
			return socket.getLocalPort();
		}
	}

	private Socket connectWithRetries( int port ) throws IOException {
		IOException	lastError	= null;
		long		deadline	= System.currentTimeMillis() + CONNECT_TIMEOUT_MS;
		while ( System.currentTimeMillis() < deadline ) {
			try {
				Socket socket = new Socket();
				socket.connect( new InetSocketAddress( "127.0.0.1", port ), CONNECT_TIMEOUT_MS );
				return socket;
			} catch ( IOException e ) {
				lastError = e;
				try {
					Thread.sleep( 200 );
				} catch ( InterruptedException interrupted ) {
					Thread.currentThread().interrupt();
					break;
				}
			}
		}
		if ( lastError != null ) {
			throw lastError;
		}
		throw new IOException( "Unable to connect to BoxLang LSP" );
	}

	private String resolveJavaExecutable( BoxLangResolvedSettings settings ) {
		if ( settings.javaHome == null || settings.javaHome.isBlank() ) {
			return "java";
		}
		String javaExecutable = SystemInfo.isWindows ? "java.exe" : "java";
		return Path.of( settings.javaHome, "bin", javaExecutable ).toString();
	}

	private List<String> buildJvmArgs( BoxLangResolvedSettings settings ) {
		List<String>	args		= new ArrayList<>();
		int				heapSize	= settings.lspMaxHeapSize > 0 ? settings.lspMaxHeapSize : 512;
		args.add( "-Xmx" + heapSize + "m" );
		if ( settings.lspJvmArgs != null && !settings.lspJvmArgs.isBlank() ) {
			args.addAll( ParametersListUtil.parse( settings.lspJvmArgs ) );
		}
		return args;
	}

	private List<WorkspaceFolder> getWorkspaceFolders() {
		String basePath = project.getBasePath();
		if ( basePath == null ) {
			return List.of();
		}
		String uri = Path.of( basePath ).toUri().toString();
		return List.of( new WorkspaceFolder( uri, project.getName() ) );
	}

	public static @Nullable String safeToUri( @Nullable VirtualFile file ) {
		if ( file == null || !file.isInLocalFileSystem() ) {
			return null;
		}
		try {
			return file.toNioPath().toUri().toString();
		} catch ( UnsupportedOperationException | IllegalArgumentException e ) {
			LOG.debug( "Unable to map VirtualFile to NIO Path: " + file, e );
			return null;
		}
	}

	private boolean isBoxLangFile( VirtualFile file ) {
		return BoxLangFileUtil.isBoxLangFile( file );
	}

	public List<org.eclipse.lsp4j.Diagnostic> requestDiagnostics( VirtualFile file, Document document ) {
		if ( !ensureStarted() ) {
			return List.of();
		}
		String uri = safeToUri( file );
		if ( uri == null ) {
			return List.of();
		}
		syncDocument( uri, document );
		if ( diagnosticPullSupported ) {
			List<org.eclipse.lsp4j.Diagnostic> pulled = pullDiagnostics( uri );
			if ( pulled != null ) {
				return pulled;
			}
		}
		return diagnostics.getOrDefault( uri, List.of() );
	}

	void updateDiagnostics( String uri, List<org.eclipse.lsp4j.Diagnostic> list ) {
		if ( list == null ) {
			diagnostics.remove( uri );
		} else {
			diagnostics.put( uri, List.copyOf( list ) );
		}
	}

	private void notifyDidSave( VirtualFile file, Document document ) {
		if ( !ensureStarted() ) {
			return;
		}
		String uri = safeToUri( file );
		if ( uri == null ) {
			return;
		}
		syncDocument( uri, document );
		notifyDidSave( uri, document );
	}

	private void notifyDidSave( String uri, Document document ) {
		org.eclipse.lsp4j.DidSaveTextDocumentParams params = new org.eclipse.lsp4j.DidSaveTextDocumentParams();
		params.setTextDocument( new TextDocumentIdentifier( uri ) );
		params.setText( document.getText() );
		server.getTextDocumentService().didSave( params );
	}

	private List<org.eclipse.lsp4j.Diagnostic> pullDiagnostics( String uri ) {
		try {
			org.eclipse.lsp4j.DocumentDiagnosticParams params = new org.eclipse.lsp4j.DocumentDiagnosticParams();
			params.setTextDocument( new TextDocumentIdentifier( uri ) );
			org.eclipse.lsp4j.DocumentDiagnosticReport report = server.getTextDocumentService().diagnostic( params ).get( REQUEST_TIMEOUT_MS,
			    TimeUnit.MILLISECONDS );
			if ( report != null && report.isRelatedFullDocumentDiagnosticReport() ) {
				List<org.eclipse.lsp4j.Diagnostic> items = report.getRelatedFullDocumentDiagnosticReport().getItems();
				updateDiagnostics( uri, items );
				return items;
			}
			if ( report != null && report.isRelatedUnchangedDocumentDiagnosticReport() ) {
				return diagnostics.getOrDefault( uri, List.of() );
			}
		} catch ( Exception e ) {
			LOG.debug( "Failed to pull diagnostics", e );
		}
		return null;
	}

	@SuppressWarnings( "deprecation" )
	private void logWorkspaceSymbolsResponse(
	    String query,
	    Either<List<? extends org.eclipse.lsp4j.SymbolInformation>, List<? extends org.eclipse.lsp4j.WorkspaceSymbol>> result,
	    List<org.eclipse.lsp4j.WorkspaceSymbol> symbols ) {
		int		rawCount	= result.isLeft() ? result.getLeft().size() : result.getRight().size();
		String	variant		= result.isLeft() ? "SymbolInformation[]" : "WorkspaceSymbol[]";
		LOG.debug(
		    "LSP workspace/symbol query='" + query + "' variant=" + variant + ", rawCount=" + rawCount + ", mappedCount="
		        + symbols.size()
		);
	}

	private void logLspRequestFailure( String method, String context, Exception e ) {
		Throwable cause = e;
		if ( e instanceof ExecutionException exec && exec.getCause() != null ) {
			cause = exec.getCause();
		}
		if ( cause instanceof ResponseErrorException responseErrorException && responseErrorException.getResponseError() != null ) {
			var responseError = responseErrorException.getResponseError();
			LOG.warn(
			    "LSP " + method + " failed " + context + " code=" + responseError.getCode() + ", message='"
			        + responseError.getMessage() + "', data=" + responseError.getData()
			);
			LOG.debug( "LSP " + method + " exception details", e );
			return;
		}
		LOG.debug( "Failed to request " + method + " " + context, e );
	}

	private boolean isUnsupportedWorkspaceSymbolError( Exception e ) {
		Throwable cause = e;
		if ( e instanceof ExecutionException exec && exec.getCause() != null ) {
			cause = exec.getCause();
		}
		if ( ! ( cause instanceof ResponseErrorException responseErrorException ) || responseErrorException.getResponseError() == null ) {
			return false;
		}
		Object data = responseErrorException.getResponseError().getData();
		if ( data == null ) {
			return false;
		}
		String dataText = String.valueOf( data );
		return dataText.contains( "UnsupportedOperationException" ) && dataText.contains( "WorkspaceService.symbol" );
	}

	private boolean isUnsupportedDeclarationError( Exception e ) {
		Throwable cause = e;
		if ( e instanceof ExecutionException exec && exec.getCause() != null ) {
			cause = exec.getCause();
		}
		if ( ! ( cause instanceof ResponseErrorException responseErrorException ) || responseErrorException.getResponseError() == null ) {
			return false;
		}
		Object data = responseErrorException.getResponseError().getData();
		if ( data != null ) {
			String dataText = String.valueOf( data );
			if ( dataText.contains( "UnsupportedOperationException" ) && dataText.contains( "TextDocumentService.declaration" ) ) {
				return true;
			}
		}
		String message = responseErrorException.getResponseError().getMessage();
		return message != null && message.contains( "Method not found" ) && message.contains( "textDocument/declaration" );
	}

	private boolean isDeclarationProviderSupported( InitializeResult result ) {
		if ( result == null || result.getCapabilities() == null ) {
			return false;
		}
		Either<Boolean, org.eclipse.lsp4j.DeclarationRegistrationOptions> provider = result.getCapabilities().getDeclarationProvider();
		if ( provider == null ) {
			return false;
		}
		if ( provider.isLeft() ) {
			Boolean enabled = provider.getLeft();
			return Boolean.TRUE.equals( enabled );
		}
		return provider.getRight() != null;
	}

	/**
	 * Maps LSP document symbols to a list of DocumentSymbol objects.
	 * Handles both DocumentSymbol (preferred) and legacy SymbolInformation responses.
	 */
	@SuppressWarnings( "deprecation" )
	private List<org.eclipse.lsp4j.DocumentSymbol> mapDocumentSymbols(
	    List<org.eclipse.lsp4j.jsonrpc.messages.Either<org.eclipse.lsp4j.SymbolInformation, org.eclipse.lsp4j.DocumentSymbol>> result ) {
		if ( result == null ) {
			return List.of();
		}
		List<org.eclipse.lsp4j.DocumentSymbol> symbols = new ArrayList<>();
		for ( var entry : result ) {
			if ( entry.isRight() ) {
				symbols.add( entry.getRight() );
				continue;
			}
			org.eclipse.lsp4j.DocumentSymbol symbol = toDocumentSymbol( entry.getLeft() );
			if ( symbol != null ) {
				symbols.add( symbol );
			}
		}
		return symbols;
	}

	private org.eclipse.lsp4j.WorkspaceSymbol toWorkspaceSymbol( Object legacySymbol ) {
		String name = readField( legacySymbol, "name", String.class );
		if ( name == null || name.isBlank() ) {
			return null;
		}
		org.eclipse.lsp4j.SymbolKind		kind			= readField( legacySymbol, "kind", org.eclipse.lsp4j.SymbolKind.class );
		String								containerName	= readField( legacySymbol, "containerName", String.class );
		org.eclipse.lsp4j.Location			location		= readField( legacySymbol, "location", org.eclipse.lsp4j.Location.class );

		org.eclipse.lsp4j.WorkspaceSymbol	symbol			= new org.eclipse.lsp4j.WorkspaceSymbol();
		symbol.setName( name );
		symbol.setKind( kind );
		symbol.setContainerName( containerName );
		if ( location != null ) {
			symbol.setLocation( Either.forLeft( location ) );
		}
		return symbol;
	}

	private org.eclipse.lsp4j.DocumentSymbol toDocumentSymbol( Object legacySymbol ) {
		String name = readField( legacySymbol, "name", String.class );
		if ( name == null || name.isBlank() ) {
			return null;
		}

		org.eclipse.lsp4j.DocumentSymbol symbol = new org.eclipse.lsp4j.DocumentSymbol();
		symbol.setName( name );
		symbol.setKind( readField( legacySymbol, "kind", org.eclipse.lsp4j.SymbolKind.class ) );

		org.eclipse.lsp4j.Location location = readField( legacySymbol, "location", org.eclipse.lsp4j.Location.class );
		if ( location != null && location.getRange() != null ) {
			symbol.setRange( location.getRange() );
			symbol.setSelectionRange( location.getRange() );
		}
		return symbol;
	}

	private static Position positionAt( Document document, int offset ) {
		int	clamped		= Math.max( 0, Math.min( offset, document.getTextLength() ) );
		int	line		= document.getLineNumber( clamped );
		int	lineStart	= line >= 0 && line < document.getLineCount() ? document.getLineStartOffset( line ) : 0;
		return new Position( Math.max( line, 0 ), Math.max( clamped - lineStart, 0 ) );
	}

	private static List<Location> flattenLocations( Either<List<? extends Location>, List<? extends LocationLink>> result ) {
		if ( result == null ) {
			return List.of();
		}
		if ( result.isLeft() ) {
			return List.copyOf( result.getLeft() );
		}
		List<? extends LocationLink> links = result.getRight();
		if ( links == null || links.isEmpty() ) {
			return List.of();
		}
		List<Location> locations = new ArrayList<>( links.size() );
		for ( LocationLink link : links ) {
			if ( link == null || link.getTargetUri() == null ) {
				continue;
			}
			Range range = link.getTargetSelectionRange() != null ? link.getTargetSelectionRange() : link.getTargetRange();
			if ( range == null ) {
				continue;
			}
			locations.add( new Location( link.getTargetUri(), range ) );
		}
		return locations;
	}

	private static <T> T readField( Object source, String fieldName, Class<T> type ) {
		if ( source == null ) {
			return null;
		}
		Class<?> current = source.getClass();
		while ( current != null ) {
			try {
				Field field = current.getDeclaredField( fieldName );
				field.setAccessible( true );
				Object value = field.get( source );
				return type.isInstance( value ) ? type.cast( value ) : null;
			} catch ( NoSuchFieldException e ) {
				current = current.getSuperclass();
			} catch ( IllegalAccessException e ) {
				return null;
			}
		}
		return null;
	}

	private static int tokenTupleCount( SemanticTokens tokens ) {
		if ( tokens == null || tokens.getData() == null ) {
			return 0;
		}
		return tokens.getData().size() / 5;
	}

	private record CachedTokens( long stamp, SemanticTokens tokens ) {
	}

	private record CachedSymbols( long stamp, List<org.eclipse.lsp4j.DocumentSymbol> symbols ) {
	}
}
