package com.ortussolutions.intellijboxlang.debug;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.execution.ParametersListUtil;
import com.ortussolutions.intellijboxlang.runtime.BoxLangDebuggerBootstrapService;
import com.ortussolutions.intellijboxlang.runtime.BoxLangLspBootstrapService;
import com.ortussolutions.intellijboxlang.runtime.LspBootstrapResult;
import com.ortussolutions.intellijboxlang.settings.BoxLangResolvedSettings;
import com.ortussolutions.intellijboxlang.settings.BoxLangSettingsResolver;
import org.eclipse.lsp4j.debug.*;
import org.eclipse.lsp4j.debug.launch.DSPLauncher;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Service that manages the DAP (Debug Adapter Protocol) connection to the BoxLang debugger.
 * This is NOT a project-level service - instances are created per debug session.
 *
 * Follows a similar pattern to BoxLangLspClientService but is designed for debug sessions
 * which have a defined lifecycle (start -> debug -> stop).
 */
public class BoxLangDapService implements Disposable {

	private static final Logger				LOG						= Logger.getInstance( BoxLangDapService.class );
	private static final int				CONNECT_TIMEOUT_MS		= 10000;
	private static final int				INITIALIZE_TIMEOUT_MS	= 20000;
	private static final String				NOT_CONNECTED_MESSAGE	= "DAP server not connected";

	private final Project					project;
	private final List<DapEventListener>	eventListeners			= new CopyOnWriteArrayList<>();

	private volatile Process				serverProcess;
	private volatile Socket					socket;
	private volatile IDebugProtocolServer	debugServer;
	private volatile Capabilities			serverCapabilities;
	private volatile boolean				initialized				= false;
	private volatile boolean				configurationReady		= false;
	private volatile boolean				terminated				= false;

	public BoxLangDapService( @NotNull Project project ) {
		this.project = project;
	}

	/**
	 * Adds a listener for DAP events (stopped, output, terminated, etc.)
	 */
	public void addEventListener( @NotNull DapEventListener listener ) {
		eventListeners.add( listener );
	}

	/**
	 * Removes a DAP event listener.
	 */
	public void removeEventListener( @NotNull DapEventListener listener ) {
		eventListeners.remove( listener );
	}

	/**
	 * Returns true if the DAP server is connected and initialized.
	 */
	public boolean isConnected() {
		return initialized && !terminated && debugServer != null;
	}

	/**
	 * Returns true if the DAP server has sent the 'initialized' event
	 * and is ready to receive breakpoint configuration.
	 */
	public boolean isConfigurationReady() {
		return configurationReady && isConnected();
	}

	/**
	 * Returns the DAP server proxy for sending requests.
	 */
	public @Nullable IDebugProtocolServer getServer() {
		return debugServer;
	}

	/**
	 * Returns the server capabilities received during initialization.
	 */
	public @Nullable Capabilities getServerCapabilities() {
		return serverCapabilities;
	}

	/**
	 * Starts the DAP server and establishes connection.
	 * This should be called at the beginning of a debug session.
	 */
	public void start() throws Exception {
		if ( initialized ) {
			LOG.warn( "DAP service already started" );
			return;
		}

		LOG.info( "Starting BoxLang DAP server" );

		BoxLangResolvedSettings	settings			= BoxLangSettingsResolver.resolve( project );
		LspBootstrapResult		bootstrap			= BoxLangLspBootstrapService.prepare( project );
		int						port				= allocatePort();

		// Resolve the debugger module directory (parent of bx-debugger/) using bootstrap service
		Path					debuggerModulesDir	= BoxLangDebuggerBootstrapService.ensureDebugger( project, bootstrap );
		if ( debuggerModulesDir == null ) {
			throw new IllegalStateException(
			    "BoxLang Debugger module not found. Please configure the Debugger Version in BoxLang settings, " +
			        "or ensure the bx-debugger module is installed in BoxLang home." );
		}

		GeneralCommandLine commandLine = new GeneralCommandLine( resolveJavaExecutable( settings ) );
		commandLine.withCharset( StandardCharsets.UTF_8 );

		// Set up environment to match how the LSP is launched
		commandLine.withEnvironment( "BOXLANG_HOME", bootstrap.lspBoxLangHome.toString() );
		commandLine.withEnvironment( "BOXLANG_MODULESDIRECTORY", debuggerModulesDir.toString() );
		commandLine.withEnvironment( "CLASSPATH", bootstrap.boxLangJarPath.toString() );
		if ( settings.javaHome != null && !settings.javaHome.isBlank() ) {
			commandLine.withEnvironment( "JAVA_HOME", settings.javaHome );
		}

		// Launch via BoxRunner as a module, matching how bx-lsp is invoked
		commandLine.addParameters( buildJvmArgs( settings ) );
		commandLine.addParameter( "ortus.boxlang.runtime.BoxRunner" );
		commandLine.addParameter( "module:bx-debugger" );
		commandLine.addParameter( String.valueOf( port ) );

		LOG.info( "Starting DAP server process" );
		serverProcess = commandLine.createProcess();
		try {
			// Start threads to capture and log the debugger server's stdout/stderr
			startOutputCapture( serverProcess );

			socket = connectWithRetries( port );
			LOG.info( "Connected to DAP server on port " + port );

			BoxLangDapClient	client		= new BoxLangDapClient( this );
			var					launcher	= DSPLauncher.createClientLauncher( client, socket.getInputStream(), socket.getOutputStream() );
			debugServer = launcher.getRemoteProxy();
			launcher.startListening();

			// Perform DAP initialization handshake
			InitializeRequestArguments initArgs = new InitializeRequestArguments();
			initArgs.setClientID( "intellij-boxlang" );
			initArgs.setClientName( "IntelliJ BoxLang Plugin" );
			initArgs.setAdapterID( "boxlang" );
			initArgs.setPathFormat( "path" );
			initArgs.setLinesStartAt1( true );
			initArgs.setColumnsStartAt1( true );
			initArgs.setSupportsVariableType( true );
			initArgs.setSupportsVariablePaging( false );
			initArgs.setSupportsRunInTerminalRequest( false );
			initArgs.setSupportsMemoryReferences( false );
			initArgs.setSupportsProgressReporting( false );
			initArgs.setSupportsInvalidatedEvent( true );

			serverCapabilities	= debugServer.initialize( initArgs )
			    .get( INITIALIZE_TIMEOUT_MS, TimeUnit.MILLISECONDS );

			initialized			= true;
			LOG.info( "DAP server initialized" );
		} catch ( Exception e ) {
			LOG.warn( "Failed to start DAP server, cleaning up partial startup", e );
			cleanupFailedStart();
			throw e;
		}
	}

	/**
	 * Sends a launch request to start debugging a script.
	 */
	public CompletableFuture<Void> launch( @NotNull String scriptPath, @Nullable String workingDirectory,
	    @Nullable List<String> args, boolean stopOnEntry ) {
		Map<String, Object> launchArgs = new HashMap<>();
		launchArgs.put( "program", prepareProgramPathForLaunch( scriptPath ) );
		launchArgs.put( "stopOnEntry", stopOnEntry );

		if ( workingDirectory != null && !workingDirectory.isBlank() ) {
			launchArgs.put( "cwd", workingDirectory );
		}

		if ( args != null && !args.isEmpty() ) {
			launchArgs.put( "args", args );
		}

		launchArgs.put( "noDebug", false );

		return callVoid( server -> server.launch( launchArgs ) );
	}

	static @NotNull String prepareProgramPathForLaunch( @NotNull String rawScriptPath ) {
		String scriptPath = stripWrappingQuotes( rawScriptPath.trim() );
		scriptPath = expandUserHomePath( scriptPath );

		if ( containsWhitespace( scriptPath ) ) {
			String aliasedPath = createWhitespaceSafeAliasPath( scriptPath );
			if ( aliasedPath != null ) {
				return aliasedPath;
			}
		}

		return scriptPath;
	}

	private static @NotNull String stripWrappingQuotes( @NotNull String path ) {
		if ( path.length() < 2 ) {
			return path;
		}
		char	first	= path.charAt( 0 );
		char	last	= path.charAt( path.length() - 1 );
		if ( ( first == '"' && last == '"' ) || ( first == '\'' && last == '\'' ) ) {
			return path.substring( 1, path.length() - 1 );
		}
		return path;
	}

	private static @NotNull String expandUserHomePath( @NotNull String path ) {
		if ( !path.startsWith( "~" ) ) {
			return path;
		}

		String userHome = System.getProperty( "user.home" );
		if ( userHome == null || userHome.isBlank() ) {
			return path;
		}

		if ( path.length() == 1 ) {
			return userHome;
		}

		char second = path.charAt( 1 );
		if ( second == '/' || second == '\\' ) {
			return userHome + path.substring( 1 );
		}

		return path;
	}

	private static boolean containsWhitespace( @NotNull String value ) {
		for ( int i = 0; i < value.length(); i++ ) {
			if ( Character.isWhitespace( value.charAt( i ) ) ) {
				return true;
			}
		}
		return false;
	}

	private static @Nullable String createWhitespaceSafeAliasPath( @NotNull String scriptPath ) {
		try {
			Path originalPath = Path.of( scriptPath ).toAbsolutePath().normalize();
			if ( !Files.exists( originalPath ) ) {
				return null;
			}

			Path originalParent = originalPath.getParent();
			if ( originalParent == null ) {
				return null;
			}

			Path aliasRoot = resolveAliasRoot();
			Files.createDirectories( aliasRoot );

			String	parentHash	= Integer.toHexString( originalParent.toString().hashCode() );
			Path	aliasDir	= aliasRoot.resolve( "dir-" + parentHash );
			ensureDirectoryAlias( aliasDir, originalParent );

			Path	aliasedScriptPath		= aliasDir.resolve( originalPath.getFileName().toString() );
			String	aliasedScriptPathString	= aliasedScriptPath.toString();
			if ( containsWhitespace( aliasedScriptPathString ) ) {
				return null;
			}

			if ( Files.exists( aliasedScriptPath ) ) {
				return aliasedScriptPathString;
			}

			return null;
		} catch ( Exception e ) {
			LOG.warn( "Unable to create whitespace-safe launch path alias for: " + scriptPath, e );
			return null;
		}
	}

	private static void ensureDirectoryAlias( @NotNull Path aliasDir, @NotNull Path targetDir ) throws IOException {
		if ( Files.exists( aliasDir, LinkOption.NOFOLLOW_LINKS ) ) {
			if ( !Files.isSymbolicLink( aliasDir ) ) {
				throw new IOException( "Alias path exists but is not a symbolic link: " + aliasDir );
			}
			Path	existingTarget			= Files.readSymbolicLink( aliasDir );
			Path	resolvedExistingTarget	= aliasDir.getParent() != null
			    ? aliasDir.getParent().resolve( existingTarget ).normalize()
			    : existingTarget.normalize();
			if ( resolvedExistingTarget.equals( targetDir.normalize() ) ) {
				return;
			}
			Files.delete( aliasDir );
		}

		Files.createSymbolicLink( aliasDir, targetDir );
	}

	private static @NotNull Path resolveAliasRoot() {
		List<Path> candidates = new ArrayList<>();
		if ( !SystemInfo.isWindows ) {
			candidates.add( Path.of( "/tmp" ) );
		}

		String tmpDir = System.getProperty( "java.io.tmpdir" );
		if ( tmpDir != null && !tmpDir.isBlank() ) {
			candidates.add( Path.of( tmpDir ) );
		}

		String userHome = System.getProperty( "user.home" );
		if ( userHome != null && !userHome.isBlank() ) {
			candidates.add( Path.of( userHome ) );
		}

		for ( Path candidate : candidates ) {
			if ( !containsWhitespace( candidate.toString() ) ) {
				return candidate.resolve( "intellij-boxlang-debug" );
			}
		}

		if ( tmpDir != null && !tmpDir.isBlank() ) {
			return Path.of( tmpDir ).resolve( "intellij-boxlang-debug" );
		}

		return Path.of( "intellij-boxlang-debug" ).toAbsolutePath();
	}

	/**
	 * Sends an attach request to connect to an already-running BoxLang process via JDWP.
	 * The bx-debugger will use JDI SocketAttach to connect to the target VM's JDWP agent.
	 *
	 * @param host       optional host of the target VM
	 * @param serverPort the JDWP port on the target VM
	 * @param localRoot  optional local filesystem root for path mapping (may be null)
	 * @param remoteRoot optional remote filesystem root for path mapping (may be null)
	 */
	public CompletableFuture<Void> attach( @Nullable String host, int serverPort, @Nullable String localRoot,
	    @Nullable String remoteRoot ) {
		Map<String, Object> attachArgs = new HashMap<>();
		attachArgs.put( "serverPort", serverPort );
		if ( host != null && !host.isBlank() ) {
			attachArgs.put( "host", host );
			attachArgs.put( "serverHost", host );
		}

		if ( localRoot != null && !localRoot.isBlank() ) {
			attachArgs.put( "localRoot", localRoot );
		}
		if ( remoteRoot != null && !remoteRoot.isBlank() ) {
			attachArgs.put( "remoteRoot", remoteRoot );
		}

		return callVoid( server -> server.attach( attachArgs ) );
	}

	/**
	 * Sends a configurationDone request to indicate client is done with initial configuration.
	 */
	public CompletableFuture<Void> configurationDone() {
		ConfigurationDoneArguments args = new ConfigurationDoneArguments();
		return callVoid( server -> server.configurationDone( args ) );
	}

	/**
	 * Sets breakpoints for a source file.
	 */
	public CompletableFuture<SetBreakpointsResponse> setBreakpoints( @NotNull String sourcePath,
	    @NotNull List<SourceBreakpoint> breakpoints ) {
		SetBreakpointsArguments	args	= new SetBreakpointsArguments();
		Source					source	= new Source();
		source.setPath( sourcePath );
		source.setName( Path.of( sourcePath ).getFileName().toString() );
		args.setSource( source );
		args.setBreakpoints( breakpoints.toArray( new SourceBreakpoint[ 0 ] ) );
		return call( server -> server.setBreakpoints( args ) );
	}

	/**
	 * Requests the current threads.
	 */
	public CompletableFuture<ThreadsResponse> threads() {
		return call( IDebugProtocolServer::threads );
	}

	/**
	 * Requests the stack trace for a thread.
	 */
	public CompletableFuture<StackTraceResponse> stackTrace( int threadId ) {
		StackTraceArguments args = new StackTraceArguments();
		args.setThreadId( threadId );
		return call( server -> server.stackTrace( args ) );
	}

	/**
	 * Requests the scopes for a stack frame.
	 */
	public CompletableFuture<ScopesResponse> scopes( int frameId ) {
		ScopesArguments args = new ScopesArguments();
		args.setFrameId( frameId );
		return call( server -> server.scopes( args ) );
	}

	/**
	 * Requests variables for a scope or variable reference.
	 */
	public CompletableFuture<VariablesResponse> variables( int variablesReference ) {
		VariablesArguments args = new VariablesArguments();
		args.setVariablesReference( variablesReference );
		return call( server -> server.variables( args ) );
	}

	/**
	 * Continues execution of a thread.
	 */
	public CompletableFuture<ContinueResponse> continueExecution( int threadId ) {
		ContinueArguments args = new ContinueArguments();
		args.setThreadId( threadId );
		return call( server -> server.continue_( args ) );
	}

	/**
	 * Steps over (next) in a thread.
	 */
	public CompletableFuture<Void> stepOver( int threadId ) {
		NextArguments args = new NextArguments();
		args.setThreadId( threadId );
		return callVoid( server -> server.next( args ) );
	}

	/**
	 * Steps into in a thread.
	 */
	public CompletableFuture<Void> stepInto( int threadId ) {
		StepInArguments args = new StepInArguments();
		args.setThreadId( threadId );
		return callVoid( server -> server.stepIn( args ) );
	}

	/**
	 * Steps out of the current function in a thread.
	 */
	public CompletableFuture<Void> stepOut( int threadId ) {
		StepOutArguments args = new StepOutArguments();
		args.setThreadId( threadId );
		return callVoid( server -> server.stepOut( args ) );
	}

	/**
	 * Pauses execution of a thread.
	 */
	public CompletableFuture<Void> pause( int threadId ) {
		PauseArguments args = new PauseArguments();
		args.setThreadId( threadId );
		return callVoid( server -> server.pause( args ) );
	}

	/**
	 * Evaluates an expression in the context of a stack frame.
	 */
	public CompletableFuture<EvaluateResponse> evaluate( String expression, int frameId, String context ) {
		EvaluateArguments args = new EvaluateArguments();
		args.setExpression( expression );
		args.setFrameId( frameId );
		args.setContext( context ); // "watch", "repl", "hover", etc.
		return call( server -> server.evaluate( args ) );
	}

	/**
	 * Disconnects from the DAP server and terminates the debug session.
	 */
	public CompletableFuture<Void> disconnect( boolean terminateDebuggee ) {
		if ( debugServer == null ) {
			return CompletableFuture.completedFuture( null );
		}

		DisconnectArguments args = new DisconnectArguments();
		args.setTerminateDebuggee( terminateDebuggee );
		return debugServer.disconnect( args ).thenApply( response -> null );
	}

	@Override
	public void dispose() {
		LOG.info( "Disposing BoxLang DAP service" );
		terminated = true;

		try {
			if ( debugServer != null ) {
				disconnect( true ).get( 5, TimeUnit.SECONDS );
			}
		} catch ( Exception e ) {
			LOG.debug( "Error during DAP disconnect", e );
		}

		try {
			if ( socket != null && !socket.isClosed() ) {
				socket.close();
			}
		} catch ( IOException e ) {
			LOG.debug( "Error closing DAP socket", e );
		}

		if ( serverProcess != null && serverProcess.isAlive() ) {
			serverProcess.destroyForcibly();
		}

		eventListeners.clear();
		debugServer		= null;
		socket			= null;
		serverProcess	= null;
		initialized		= false;
	}

	// Event handlers called by BoxLangDapClient

	void handleStopped( StoppedEventArguments args ) {
		notifyListeners( "stopped", listener -> listener.onStopped( args ) );
	}

	void handleContinued( ContinuedEventArguments args ) {
		notifyListeners( "continued", listener -> listener.onContinued( args ) );
	}

	void handleExited( ExitedEventArguments args ) {
		notifyListeners( "exited", listener -> listener.onExited( args ) );
	}

	void handleTerminated( TerminatedEventArguments args ) {
		terminated = true;
		notifyListeners( "terminated", listener -> listener.onTerminated( args ) );
	}

	void handleThread( ThreadEventArguments args ) {
		notifyListeners( "thread", listener -> listener.onThread( args ) );
	}

	void handleOutput( OutputEventArguments args ) {
		notifyListeners( "output", listener -> listener.onOutput( args ) );
	}

	void handleBreakpoint( BreakpointEventArguments args ) {
		notifyListeners( "breakpoint", listener -> listener.onBreakpoint( args ) );
	}

	void handleCapabilities( CapabilitiesEventArguments args ) {
		if ( args.getCapabilities() != null ) {
			this.serverCapabilities = args.getCapabilities();
		}
	}

	void handleInitialized() {
		configurationReady = true;
		notifyListeners( "initialized", DapEventListener::onInitialized );
	}

	// Private helper methods

	private <T> CompletableFuture<T> call( @NotNull Function<IDebugProtocolServer, CompletableFuture<T>> operation ) {
		IDebugProtocolServer server = debugServer;
		if ( !isConnected() || server == null ) {
			return CompletableFuture.failedFuture( new IllegalStateException( NOT_CONNECTED_MESSAGE ) );
		}
		return operation.apply( server );
	}

	private CompletableFuture<Void> callVoid( @NotNull Function<IDebugProtocolServer, CompletableFuture<?>> operation ) {
		return call( server -> operation.apply( server ).thenApply( ignore -> null ) );
	}

	private void notifyListeners( @NotNull String eventName, @NotNull Consumer<DapEventListener> eventHandler ) {
		for ( DapEventListener listener : eventListeners ) {
			try {
				eventHandler.accept( listener );
			} catch ( Exception e ) {
				LOG.warn( "Error in DAP event listener for " + eventName, e );
			}
		}
	}

	/**
	 * Starts threads to capture and log the debugger server's stdout and stderr output.
	 */
	private void startOutputCapture( Process process ) {
		startOutputCaptureThread( process.getInputStream(), "bx-debugger-stdout", line -> LOG.debug( "[bx-debugger] " + line ) );
		startOutputCaptureThread( process.getErrorStream(), "bx-debugger-stderr", line -> LOG.warn( "[bx-debugger] " + line ) );
	}

	private void startOutputCaptureThread( java.io.InputStream inputStream,
	    @NotNull String threadName,
	    @NotNull Consumer<String> lineLogger ) {
		java.lang.Thread thread = new java.lang.Thread( () -> {
			try ( java.io.BufferedReader reader = new java.io.BufferedReader(
			    new java.io.InputStreamReader( inputStream, StandardCharsets.UTF_8 ) ) ) {
				String line;
				while ( ( line = reader.readLine() ) != null ) {
					lineLogger.accept( line );
				}
			} catch ( IOException e ) {
				if ( !terminated ) {
					LOG.debug( "Error reading " + threadName + ": " + e.getMessage() );
				}
			}
		}, threadName );
		thread.setDaemon( true );
		thread.start();
	}

	private void cleanupFailedStart() {
		try {
			if ( socket != null && !socket.isClosed() ) {
				socket.close();
			}
		} catch ( IOException e ) {
			LOG.debug( "Error closing DAP socket after failed startup", e );
		}

		if ( serverProcess != null && serverProcess.isAlive() ) {
			serverProcess.destroyForcibly();
		}

		debugServer			= null;
		socket				= null;
		serverProcess		= null;
		serverCapabilities	= null;
		initialized			= false;
		configurationReady	= false;
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
					java.lang.Thread.sleep( 200 );
				} catch ( InterruptedException interrupted ) {
					java.lang.Thread.currentThread().interrupt();
					break;
				}
			}
		}
		if ( lastError != null ) {
			throw lastError;
		}
		throw new IOException( "Unable to connect to BoxLang DAP server" );
	}

	private String resolveJavaExecutable( BoxLangResolvedSettings settings ) {
		String javaExecutable = SystemInfo.isWindows ? "java.exe" : "java";

		// First, try the configured Java home from settings
		if ( settings.javaHome != null && !settings.javaHome.isBlank() ) {
			Path javaPath = Path.of( settings.javaHome, "bin", javaExecutable );
			if ( javaPath.toFile().exists() ) {
				return javaPath.toString();
			}
		}

		// Second, try JAVA_HOME environment variable
		String javaHomeEnv = System.getenv( "JAVA_HOME" );
		if ( javaHomeEnv != null && !javaHomeEnv.isBlank() ) {
			Path javaPath = Path.of( javaHomeEnv, "bin", javaExecutable );
			if ( javaPath.toFile().exists() ) {
				return javaPath.toString();
			}
		}

		// Third, try to find Java in the current process (IntelliJ's JDK)
		String currentJavaHome = System.getProperty( "java.home" );
		if ( currentJavaHome != null && !currentJavaHome.isBlank() ) {
			Path javaPath = Path.of( currentJavaHome, "bin", javaExecutable );
			if ( javaPath.toFile().exists() ) {
				return javaPath.toString();
			}
		}

		// Last resort - use "java" from PATH
		return "java";
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

	/**
	 * Interface for listening to DAP events.
	 */
	public interface DapEventListener {

		default void onInitialized() {
		}

		default void onStopped( StoppedEventArguments args ) {
		}

		default void onContinued( ContinuedEventArguments args ) {
		}

		default void onExited( ExitedEventArguments args ) {
		}

		default void onTerminated( TerminatedEventArguments args ) {
		}

		default void onThread( ThreadEventArguments args ) {
		}

		default void onOutput( OutputEventArguments args ) {
		}

		default void onBreakpoint( BreakpointEventArguments args ) {
		}
	}
}
