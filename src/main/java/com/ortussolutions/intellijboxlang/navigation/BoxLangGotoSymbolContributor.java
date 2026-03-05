package com.ortussolutions.intellijboxlang.navigation;

import com.intellij.navigation.ChooseByNameContributorEx;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Processor;
import com.intellij.util.indexing.FindSymbolParameters;
import com.intellij.util.indexing.IdFilter;
import com.ortussolutions.intellijboxlang.file.BoxLangFileUtil;
import com.ortussolutions.intellijboxlang.lsp.BoxLangLspClientService;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.SymbolInformation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Contributes BoxLang symbols to the "Go to Symbol" / Search Everywhere Symbols tab.
 * Symbols are fetched from the BoxLang LSP server via the workspace/symbol request.
 */
@SuppressWarnings( "deprecation" )
public class BoxLangGotoSymbolContributor implements ChooseByNameContributorEx {

	private final AtomicLong							searchGeneration	= new AtomicLong( 0 );
	private volatile long								activeGeneration	= 0;
	private final Map<String, List<SymbolInformation>>	queryCache			= new ConcurrentHashMap<>();
	private final Map<String, Set<String>>				emittedKeysByQuery	= new ConcurrentHashMap<>();

	@Override
	public void processNames( @NotNull Processor<? super String> processor, @NotNull GlobalSearchScope scope, @Nullable IdFilter filter ) {
		Project project = scope.getProject();
		if ( project == null ) {
			return;
		}
		activeGeneration = searchGeneration.incrementAndGet();
		queryCache.clear();
		emittedKeysByQuery.clear();

		BoxLangLspClientService	service	= BoxLangLspClientService.getInstance( project );
		List<SymbolInformation>	symbols	= queryCache.computeIfAbsent( "", ignored -> service.requestWorkspaceSymbols( "" ) );
		Set<String>				seen	= new HashSet<>();
		for ( SymbolInformation symbol : symbols ) {
			String symbolName = symbol.getName();
			if ( symbolName == null || !seen.add( symbolName ) ) {
				continue;
			}
			if ( !processor.process( symbolName ) ) {
				return;
			}
		}
		for ( SymbolInformation symbol : collectOpenFileSymbols( project, service, null ) ) {
			String symbolName = symbol.getName();
			if ( symbolName == null || !seen.add( symbolName ) ) {
				continue;
			}
			if ( !processor.process( symbolName ) ) {
				return;
			}
		}
	}

	@Override
	public void processElementsWithName( @NotNull String name, @NotNull Processor<? super NavigationItem> processor,
	    @NotNull FindSymbolParameters parameters ) {
		Project					project	= parameters.getProject();
		BoxLangLspClientService	service	= BoxLangLspClientService.getInstance( project );
		List<SymbolInformation>	symbols	= queryCache.computeIfAbsent( name, service::requestWorkspaceSymbols );
		String					bucket	= activeGeneration + "|" + name;
		Set<String>				emitted	= emittedKeysByQuery.computeIfAbsent( bucket, ignored -> ConcurrentHashMap.newKeySet() );
		for ( SymbolInformation symbol : symbols ) {
			if ( symbol.getName().equals( name ) ) {
				if ( !emitted.add( symbolKey( symbol ) ) ) {
					continue;
				}
				if ( !processor.process( new BoxLangSymbolNavigationItem( project, symbol ) ) ) {
					return;
				}
			}
		}
		for ( SymbolInformation symbol : collectOpenFileSymbols( project, service, name ) ) {
			if ( !name.equals( symbol.getName() ) ) {
				continue;
			}
			if ( !emitted.add( symbolKey( symbol ) ) ) {
				continue;
			}
			if ( !processor.process( new BoxLangSymbolNavigationItem( project, symbol ) ) ) {
				return;
			}
		}
	}

	private static String symbolKey( SymbolInformation symbol ) {
		StringBuilder key = new StringBuilder();
		key.append( symbol.getName() ).append( "|" );
		key.append( symbol.getKind() ).append( "|" );
		if ( symbol.getLocation() != null ) {
			key.append( symbol.getLocation().getUri() ).append( "|" );
			if ( symbol.getLocation().getRange() != null && symbol.getLocation().getRange().getStart() != null ) {
				key.append( symbol.getLocation().getRange().getStart().getLine() )
				    .append( ":" )
				    .append( symbol.getLocation().getRange().getStart().getCharacter() );
			}
		}
		return key.toString();
	}

	private static List<SymbolInformation> collectOpenFileSymbols( Project project, BoxLangLspClientService service, @Nullable String nameFilter ) {
		List<SymbolInformation> result = new ArrayList<>();
		for ( VirtualFile file : FileEditorManager.getInstance( project ).getOpenFiles() ) {
			if ( !BoxLangFileUtil.isBoxLangFile( file ) ) {
				continue;
			}
			Document document = FileDocumentManager.getInstance().getDocument( file );
			if ( document == null ) {
				continue;
			}
			List<DocumentSymbol>	docSymbols	= service.requestDocumentSymbols( file, document );
			String					uri			= file.toNioPath().toUri().toString();
			addDocumentSymbols( result, docSymbols, uri, null, nameFilter );
		}
		return result;
	}

	private static void addDocumentSymbols(
	    List<SymbolInformation> out,
	    List<DocumentSymbol> symbols,
	    String uri,
	    @Nullable String container,
	    @Nullable String nameFilter ) {
		for ( DocumentSymbol symbol : symbols ) {
			if ( symbol == null || symbol.getName() == null ) {
				continue;
			}
			String name = symbol.getName();
			if ( nameFilter == null || name.equals( nameFilter ) ) {
				SymbolInformation info = new SymbolInformation();
				info.setName( name );
				info.setKind( symbol.getKind() );
				info.setContainerName( container );
				Range range = symbol.getSelectionRange() != null ? symbol.getSelectionRange() : symbol.getRange();
				if ( range != null ) {
					info.setLocation( new Location( uri, range ) );
				}
				out.add( info );
			}
			if ( symbol.getChildren() != null && !symbol.getChildren().isEmpty() ) {
				addDocumentSymbols( out, symbol.getChildren(), uri, name, nameFilter );
			}
		}
	}
}
