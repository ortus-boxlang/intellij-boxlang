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
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.net.URI;
import java.nio.file.Path;

/**
 * Wraps an LSP {@link SymbolInformation} as an IntelliJ {@link NavigationItem}
 * so it can appear in Search Everywhere and Go to Symbol results.
 */
@SuppressWarnings( "deprecation" )
public class BoxLangSymbolNavigationItem implements NavigationItem {

	private final Project			project;
	private final SymbolInformation	symbol;

	public BoxLangSymbolNavigationItem( @NotNull Project project, @NotNull SymbolInformation symbol ) {
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
		Location location = symbol.getLocation();
		if ( location != null ) {
			key.append( location.getUri() ).append( "|" );
			if ( location.getRange() != null && location.getRange().getStart() != null ) {
				key.append( location.getRange().getStart().getLine() )
				    .append( ":" )
				    .append( location.getRange().getStart().getCharacter() );
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
				Location location = symbol.getLocation();
				if ( location == null ) {
					return symbol.getContainerName();
				}
				try {
					String	path	= Path.of( new URI( location.getUri() ) ).toString();
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
		Location location = symbol.getLocation();
		if ( location == null ) {
			return;
		}
		try {
			Path		filePath	= Path.of( new URI( location.getUri() ) );
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
			int	line		= location.getRange().getStart().getLine();
			int	character	= location.getRange().getStart().getCharacter();
			if ( line >= document.getLineCount() ) {
				return;
			}
			int offset = Math.min( document.getLineStartOffset( line ) + character, document.getTextLength() );
			new OpenFileDescriptor( project, vf, offset ).navigate( requestFocus );
		} catch ( Exception e ) {
			// Ignore navigation failures
		}
	}

	@Override
	public boolean canNavigate() {
		return symbol.getLocation() != null;
	}

	@Override
	public boolean canNavigateToSource() {
		return canNavigate();
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
