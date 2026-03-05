package com.ortussolutions.intellijboxlang.navigation;

import com.intellij.icons.AllIcons;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.eclipse.lsp4j.WorkspaceSymbolLocation;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.net.URI;
import java.nio.file.Path;

/**
 * Wraps an LSP {@link WorkspaceSymbol} as an IntelliJ {@link NavigationItem}
 * so it can appear in Search Everywhere and Go to Symbol results.
 */
public class BoxLangSymbolNavigationItem implements NavigationItem {

	private final Project			project;
	private final WorkspaceSymbol	symbol;

	public BoxLangSymbolNavigationItem( @NotNull Project project, @NotNull WorkspaceSymbol symbol ) {
		this.project	= project;
		this.symbol		= symbol;
	}

	/**
	 * Stable key for Search Everywhere deduplication across repeated contributor calls.
	 */
	public @NotNull String getDeduplicationKey() {
		StringBuilder key = new StringBuilder();
		key.append( symbol.getName() ).append( "|" );
		key.append( symbol.getKind() ).append( "|" );
		String uri = symbolUri();
		if ( uri != null ) {
			key.append( uri ).append( "|" );
			Range range = symbolRange();
			if ( range != null && range.getStart() != null ) {
				key.append( range.getStart().getLine() )
				    .append( ":" )
				    .append( range.getStart().getCharacter() );
			}
		}
		return key.toString();
	}

	@Override
	public @Nullable String getName() {
		return symbol.getName();
	}

	@Override
	public @Nullable ItemPresentation getPresentation() {
		return new ItemPresentation() {

			@Override
			public @Nullable String getPresentableText() {
				return symbol.getName();
			}

			@Override
			public @Nullable Icon getIcon( boolean unused ) {
				return iconFor( symbol.getKind() );
			}

			@Override
			public @Nullable String getLocationString() {
				String uri = symbolUri();
				if ( uri == null ) {
					return symbol.getContainerName();
				}
				try {
					String	path	= Path.of( new URI( uri ) ).toString();
					String	base	= project.getBasePath();
					if ( base != null && path.startsWith( base ) ) {
						path = path.substring( base.length() + 1 );
					}
					if ( symbol.getContainerName() != null && !symbol.getContainerName().isBlank() ) {
						return symbol.getContainerName() + " (" + path + ")";
					}
					return path;
				} catch ( Exception e ) {
					return symbol.getContainerName();
				}
			}
		};
	}

	@Override
	public void navigate( boolean requestFocus ) {
		String uri = symbolUri();
		if ( uri == null ) {
			return;
		}
		try {
			Path		filePath	= Path.of( new URI( uri ) );
			VirtualFile	vf			= LocalFileSystem.getInstance().findFileByNioFile( filePath );
			if ( vf == null ) {
				return;
			}
			PsiFile psiFile = PsiManager.getInstance( project ).findFile( vf );
			if ( psiFile == null ) {
				return;
			}
			Document document = PsiDocumentManager.getInstance( project ).getDocument( psiFile );
			if ( document == null ) {
				return;
			}
			Range	range		= symbolRange();
			int		line		= range != null && range.getStart() != null ? range.getStart().getLine() : 0;
			int		character	= range != null && range.getStart() != null ? range.getStart().getCharacter() : 0;
			if ( document.getLineCount() > 0 && line >= document.getLineCount() ) {
				line = document.getLineCount() - 1;
			}
			int offset = document.getLineCount() == 0
			    ? 0
			    : Math.min( document.getLineStartOffset( Math.max( line, 0 ) ) + Math.max( character, 0 ), document.getTextLength() );
			new OpenFileDescriptor( project, vf, offset ).navigate( requestFocus );
		} catch ( Exception e ) {
			// Ignore navigation failures
		}
	}

	@Override
	public boolean canNavigate() {
		return symbolUri() != null;
	}

	@Override
	public boolean canNavigateToSource() {
		return canNavigate();
	}

	private @Nullable String symbolUri() {
		Either<Location, WorkspaceSymbolLocation> location = symbol.getLocation();
		if ( location == null ) {
			return null;
		}
		return location.isLeft() ? location.getLeft().getUri() : location.getRight().getUri();
	}

	private @Nullable Range symbolRange() {
		Either<Location, WorkspaceSymbolLocation> location = symbol.getLocation();
		if ( location == null || !location.isLeft() ) {
			return null;
		}
		return location.getLeft().getRange();
	}

	private static Icon iconFor( SymbolKind kind ) {
		if ( kind == null ) {
			return AllIcons.Nodes.Variable;
		}
		return switch ( kind ) {
			case Class, Interface, Struct -> AllIcons.Nodes.Class;
			case Method, Function, Constructor -> AllIcons.Nodes.Method;
			case Property, Field -> AllIcons.Nodes.Field;
			case Variable, Constant -> AllIcons.Nodes.Variable;
			case Namespace, Package, Module -> AllIcons.Nodes.Package;
			case Enum -> AllIcons.Nodes.Enum;
			case EnumMember -> AllIcons.Nodes.Enum;
			case Event -> AllIcons.Nodes.EntryPoints;
			default -> AllIcons.Nodes.Variable;
		};
	}
}
