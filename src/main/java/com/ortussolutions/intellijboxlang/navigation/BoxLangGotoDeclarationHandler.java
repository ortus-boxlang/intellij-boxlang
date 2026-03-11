package com.ortussolutions.intellijboxlang.navigation;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.ortussolutions.intellijboxlang.file.BoxLangFileType;
import com.ortussolutions.intellijboxlang.lsp.BoxLangLspClientService;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class BoxLangGotoDeclarationHandler implements GotoDeclarationHandler {

	@Override
	public PsiElement @Nullable [] getGotoDeclarationTargets( @Nullable PsiElement sourceElement, int offset, Editor editor ) {
		if ( sourceElement == null || editor == null ) {
			return null;
		}
		PsiFile sourceFile = sourceElement.getContainingFile();
		if ( sourceFile == null || sourceFile.getFileType() != BoxLangFileType.INSTANCE ) {
			return null;
		}

		Project		project				= sourceFile.getProject();
		VirtualFile	sourceVirtualFile	= sourceFile.getVirtualFile();
		if ( sourceVirtualFile == null ) {
			return null;
		}

		Document				document	= editor.getDocument();
		BoxLangLspClientService	lspService	= BoxLangLspClientService.getInstance( project );
		List<Location>			locations	= lspService.requestDefinition( sourceVirtualFile, document, offset );
		if ( locations.isEmpty() ) {
			locations = resolveFromDocumentSymbols( lspService, sourceVirtualFile, document, offset );
		}
		if ( locations.isEmpty() ) {
			return null;
		}

		List<PsiElement> targets = toPsiTargets( project, locations );
		return targets.isEmpty() ? null : targets.toArray( PsiElement.EMPTY_ARRAY );
	}

	@Override
	public @Nullable String getActionText( DataContext context ) {
		return null;
	}

	private List<PsiElement> toPsiTargets( @NotNull Project project, @NotNull List<Location> locations ) {
		Map<String, PsiElement>	deduped			= new LinkedHashMap<>();
		PsiManager				psiManager		= PsiManager.getInstance( project );
		PsiDocumentManager		documentManager	= PsiDocumentManager.getInstance( project );

		for ( Location location : locations ) {
			if ( location == null || location.getUri() == null ) {
				continue;
			}
			try {
				Path		path	= Path.of( new URI( location.getUri() ) );
				VirtualFile	vf		= LocalFileSystem.getInstance().findFileByNioFile( path );
				if ( vf == null ) {
					continue;
				}
				PsiFile psiFile = psiManager.findFile( vf );
				if ( psiFile == null ) {
					continue;
				}
				Document doc = documentManager.getDocument( psiFile );
				if ( doc == null ) {
					continue;
				}
				int			targetOffset	= offsetFor( doc, location.getRange() );
				PsiElement	atOffset		= targetOffset >= 0 && targetOffset < psiFile.getTextLength()
				    ? psiFile.findElementAt( targetOffset )
				    : null;
				PsiElement	target			= atOffset != null ? atOffset : psiFile;
				String		key				= vf.getPath() + ":" + targetOffset;
				deduped.putIfAbsent( key, target );
			} catch ( Exception ignored ) {
				// Ignore invalid locations and continue.
			}
		}

		return new ArrayList<>( deduped.values() );
	}

	private static List<Location> resolveFromDocumentSymbols(
	    @NotNull BoxLangLspClientService lspService,
	    @NotNull VirtualFile sourceFile,
	    @NotNull Document document,
	    int offset ) {
		String symbolName = identifierAt( document, offset );
		if ( symbolName == null || symbolName.isBlank() ) {
			return List.of();
		}
		String uri = BoxLangLspClientService.safeToUri( sourceFile );
		if ( uri == null ) {
			return List.of();
		}
		List<DocumentSymbol> symbols = lspService.requestDocumentSymbols( sourceFile, document );
		if ( symbols.isEmpty() ) {
			return List.of();
		}
		List<Location> matches = new ArrayList<>();
		collectSymbolLocations( symbols, symbolName, uri, matches );
		return matches;
	}

	private static void collectSymbolLocations(
	    @NotNull List<DocumentSymbol> symbols,
	    @NotNull String symbolName,
	    @NotNull String uri,
	    @NotNull List<Location> out ) {
		for ( DocumentSymbol symbol : symbols ) {
			if ( symbol == null || symbol.getName() == null ) {
				continue;
			}
			if ( isCallableSymbol( symbol )
			    && symbol.getName().equalsIgnoreCase( symbolName ) ) {
				Range range = symbol.getSelectionRange() != null ? symbol.getSelectionRange() : symbol.getRange();
				if ( range != null ) {
					out.add( new Location( uri, range ) );
				}
			}
			if ( symbol.getChildren() != null && !symbol.getChildren().isEmpty() ) {
				collectSymbolLocations( symbol.getChildren(), symbolName, uri, out );
			}
		}
	}

	private static boolean isCallableSymbol( @NotNull DocumentSymbol symbol ) {
		SymbolKind kind = symbol.getKind();
		return kind == SymbolKind.Function || kind == SymbolKind.Method;
	}

	private static @Nullable String identifierAt( @NotNull Document document, int offset ) {
		CharSequence text = document.getCharsSequence();
		if ( text.length() == 0 ) {
			return null;
		}
		int pos = Math.max( 0, Math.min( offset, text.length() - 1 ) );
		if ( !isIdentifierPart( text.charAt( pos ) ) && pos > 0 && isIdentifierPart( text.charAt( pos - 1 ) ) ) {
			pos--;
		}
		if ( !isIdentifierPart( text.charAt( pos ) ) ) {
			return null;
		}
		int start = pos;
		while ( start > 0 && isIdentifierPart( text.charAt( start - 1 ) ) ) {
			start--;
		}
		int end = pos + 1;
		while ( end < text.length() && isIdentifierPart( text.charAt( end ) ) ) {
			end++;
		}
		return text.subSequence( start, end ).toString();
	}

	private static boolean isIdentifierPart( char c ) {
		return Character.isLetterOrDigit( c ) || c == '_' || c == '$';
	}

	private static int offsetFor( @NotNull Document document, @Nullable Range range ) {
		if ( range == null || range.getStart() == null || document.getLineCount() == 0 ) {
			return 0;
		}
		int	line		= Math.max( 0, Math.min( range.getStart().getLine(), document.getLineCount() - 1 ) );
		int	character	= Math.max( 0, range.getStart().getCharacter() );
		return Math.min( document.getLineStartOffset( line ) + character, document.getTextLength() );
	}
}
