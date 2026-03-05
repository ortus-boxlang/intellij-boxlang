package com.ortussolutions.intellijboxlang.debug;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.ColoredTextContainer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.io.URLUtil;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XValueChildrenList;
import org.eclipse.lsp4j.debug.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents a single stack frame in the BoxLang debugger.
 * Maps DAP StackFrame data to IntelliJ's XStackFrame interface.
 *
 * This provides:
 * - Source position (file + line) so IntelliJ highlights the current line
 * - Frame display name in the Frames panel
 * - Variable computation (Phase 7 will add real variables)
 */
public class BoxLangStackFrame extends XStackFrame {

	private static final Logger			LOG						= Logger.getInstance( BoxLangStackFrame.class );
	private static final Pattern		FRAME_LOCATION_PATTERN	= Pattern.compile( "([\\w./\\\\-]+\\.(?:bx|bxs|bxm))(?::(\\d+))?" );

	private final BoxLangDebugProcess	debugProcess;
	private final StackFrame			dapFrame;
	private final XSourcePosition		sourcePosition;

	public BoxLangStackFrame( @NotNull BoxLangDebugProcess debugProcess,
	    @NotNull StackFrame dapFrame ) {
		this.debugProcess	= debugProcess;
		this.dapFrame		= dapFrame;
		this.sourcePosition	= computeSourcePosition( dapFrame );
	}

	@Override
	public @Nullable XSourcePosition getSourcePosition() {
		return sourcePosition;
	}

	@Override
	public @Nullable XDebuggerEvaluator getEvaluator() {
		return new BoxLangEvaluator( debugProcess, dapFrame.getId() );
	}

	@Override
	public void customizePresentation( @NotNull ColoredTextContainer component ) {
		String name = dapFrame.getName();
		if ( name == null || name.isEmpty() ) {
			name = "<unknown>";
		}

		// Display: "functionName() at file.bxs:line"
		component.append( name, SimpleTextAttributes.REGULAR_ATTRIBUTES );

		Source source = dapFrame.getSource();
		if ( source != null && source.getName() != null ) {
			component.append( "  ", SimpleTextAttributes.REGULAR_ATTRIBUTES );
			component.append( source.getName() + ":" + dapFrame.getLine(),
			    SimpleTextAttributes.GRAYED_ATTRIBUTES );
		}
	}

	@Override
	public void computeChildren( @NotNull XCompositeNode node ) {
		// Fetch scopes for this frame, then fetch variables for each scope
		BoxLangDapService dapService = debugProcess.getDapService();
		if ( !dapService.isConnected() ) {
			node.addChildren( XValueChildrenList.EMPTY, true );
			return;
		}

		dapService.scopes( dapFrame.getId() )
		    .thenAccept( scopesResponse -> {
			    if ( scopesResponse == null || scopesResponse.getScopes() == null ||
			        scopesResponse.getScopes().length == 0 ) {
				    node.addChildren( XValueChildrenList.EMPTY, true );
				    return;
			    }

			    Scope[] scopes = scopesResponse.getScopes();
			    // Fetch variables for all scopes and add them as children
			    fetchScopeVariables( node, dapService, scopes, 0 );
		    } )
		    .exceptionally( ex -> {
			    LOG.warn( "Failed to fetch scopes for frame " + dapFrame.getId(), ex );
			    node.addChildren( XValueChildrenList.EMPTY, true );
			    return null;
		    } );
	}

	/**
	 * Recursively fetches variables for each scope and adds them to the node.
	 * Scopes are processed sequentially so they appear in order in the UI.
	 */
	private void fetchScopeVariables( @NotNull XCompositeNode node,
	    @NotNull BoxLangDapService dapService,
	    @NotNull Scope[] scopes,
	    int index ) {
		if ( index >= scopes.length ) {
			// All scopes processed - we're done
			node.addChildren( XValueChildrenList.EMPTY, true );
			return;
		}

		Scope	scope		= scopes[ index ];
		boolean	isLastScope	= ( index == scopes.length - 1 );

		dapService.variables( scope.getVariablesReference() )
		    .thenAccept( variablesResponse -> {
			    XValueChildrenList children = new XValueChildrenList();

			    if ( variablesResponse != null && variablesResponse.getVariables() != null ) {
				    for ( Variable variable : variablesResponse.getVariables() ) {
					    children.add( new BoxLangNamedValue( debugProcess, variable ) );
				    }
			    }

			    // Add this scope's variables; isLast=true only for the final scope
			    node.addChildren( children, isLastScope );

			    if ( !isLastScope ) {
				    // Fetch next scope
				    fetchScopeVariables( node, dapService, scopes, index + 1 );
			    }
		    } )
		    .exceptionally( ex -> {
			    LOG.warn( "Failed to fetch variables for scope: " + scope.getName(), ex );
			    // Still try to process remaining scopes
			    if ( isLastScope ) {
				    node.addChildren( XValueChildrenList.EMPTY, true );
			    } else {
				    fetchScopeVariables( node, dapService, scopes, index + 1 );
			    }
			    return null;
		    } );
	}

	/**
	 * Computes the IntelliJ source position from a DAP StackFrame.
	 * Maps the DAP source path + line number to an IntelliJ XSourcePosition.
	 */
	private @Nullable XSourcePosition computeSourcePosition( @NotNull StackFrame frame ) {
		VirtualFile file = resolveSourceFile( frame );
		if ( file == null ) {
			return null;
		}

		// DAP uses 1-based lines, IntelliJ uses 0-based
		int lineNumber = frame.getLine();
		if ( lineNumber <= 0 ) {
			Integer inferredLine = inferLineFromFrameName( frame.getName() );
			if ( inferredLine != null ) {
				lineNumber = inferredLine;
			}
		}
		int line = lineNumber - 1;
		if ( line < 0 ) {
			line = 0;
		}

		return XDebuggerUtil.getInstance().createPosition( file, line );
	}

	private @Nullable VirtualFile resolveSourceFile( @NotNull StackFrame frame ) {
		Source source = frame.getSource();
		if ( source != null ) {
			VirtualFile fromSource = resolveSourceFile( source );
			if ( fromSource != null ) {
				return fromSource;
			}
		}

		String inferredPath = inferPathFromFrameName( frame.getName() );
		if ( inferredPath == null ) {
			return null;
		}

		Source inferredSource = new Source();
		inferredSource.setPath( inferredPath );
		inferredSource.setName( extractNameFromPath( FileUtil.toSystemIndependentName( inferredPath ) ) );
		return resolveSourceFile( inferredSource );
	}

	private @Nullable VirtualFile resolveSourceFile( @NotNull Source source ) {
		String		rawPath			= source.getPath();
		String		normalizedPath	= normalizeSourcePath( rawPath );
		VirtualFile	file			= findLocalFile( normalizedPath );
		if ( file != null ) {
			return file;
		}

		for ( String candidate : extractEmbeddedAbsolutePathCandidates( normalizedPath ) ) {
			file = findLocalFile( candidate );
			if ( file != null ) {
				return file;
			}
		}

		Project project = debugProcess.getSession().getProject();
		if ( normalizedPath != null ) {
			String projectBasePath = project.getBasePath();
			if ( projectBasePath != null ) {
				file = findLocalFile( FileUtil.toSystemIndependentName( projectBasePath + "/" + normalizedPath ) );
				if ( file != null ) {
					return file;
				}
			}
		}

		String sourceName = source.getName();
		if ( sourceName == null || sourceName.isBlank() ) {
			sourceName = extractNameFromPath( normalizedPath );
		}
		if ( sourceName == null || sourceName.isBlank() ) {
			return null;
		}

		VirtualFile[] matches = FilenameIndex.getVirtualFilesByName(
		    sourceName,
		    GlobalSearchScope.projectScope( project )
		).toArray( VirtualFile.EMPTY_ARRAY );

		if ( matches.length == 1 ) {
			return matches[ 0 ];
		}

		if ( normalizedPath != null ) {
			String lowerPath = normalizedPath.toLowerCase();
			for ( VirtualFile candidate : matches ) {
				String candidatePath = candidate.getPath();
				if ( candidatePath != null && candidatePath.toLowerCase().endsWith( lowerPath ) ) {
					return candidate;
				}
			}
		}

		return null;
	}

	private @NotNull List<String> extractEmbeddedAbsolutePathCandidates( @Nullable String path ) {
		if ( path == null || path.isBlank() ) {
			return List.of();
		}

		Set<String>	candidates	= new LinkedHashSet<>();
		int			start		= path.indexOf( '/' );
		while ( start >= 0 && start < path.length() - 1 ) {
			String candidate = path.substring( start );
			candidates.add( FileUtil.toSystemIndependentName( debugProcess.mapRemotePathToLocal( candidate ) ) );
			start = path.indexOf( '/', start + 1 );
		}

		Matcher windowsMatcher = Pattern.compile( "([A-Za-z]:/[^:*?\"<>|]+)" ).matcher( path );
		while ( windowsMatcher.find() ) {
			String candidate = windowsMatcher.group( 1 );
			candidates.add( FileUtil.toSystemIndependentName( debugProcess.mapRemotePathToLocal( candidate ) ) );
		}

		return new ArrayList<>( candidates );
	}

	private @Nullable VirtualFile findLocalFile( @Nullable String path ) {
		if ( path == null || path.isBlank() ) {
			return null;
		}

		LocalFileSystem	localFileSystem	= LocalFileSystem.getInstance();
		VirtualFile		file			= localFileSystem.findFileByPath( path );
		if ( file == null ) {
			file = localFileSystem.refreshAndFindFileByPath( path );
		}
		return file;
	}

	private @Nullable String extractNameFromPath( @Nullable String path ) {
		if ( path == null || path.isBlank() ) {
			return null;
		}
		int slash = path.lastIndexOf( '/' );
		if ( slash < 0 || slash == path.length() - 1 ) {
			return path;
		}
		return path.substring( slash + 1 );
	}

	private @Nullable String inferPathFromFrameName( @Nullable String frameName ) {
		if ( frameName == null || frameName.isBlank() ) {
			return null;
		}
		Matcher matcher = FRAME_LOCATION_PATTERN.matcher( frameName );
		if ( !matcher.find() ) {
			return null;
		}
		return matcher.group( 1 );
	}

	private @Nullable Integer inferLineFromFrameName( @Nullable String frameName ) {
		if ( frameName == null || frameName.isBlank() ) {
			return null;
		}
		Matcher matcher = FRAME_LOCATION_PATTERN.matcher( frameName );
		if ( !matcher.find() ) {
			return null;
		}
		String lineText = matcher.group( 2 );
		if ( lineText == null || lineText.isBlank() ) {
			return null;
		}
		try {
			return Integer.parseInt( lineText );
		} catch ( NumberFormatException ignored ) {
			return null;
		}
	}

	private @Nullable String normalizeSourcePath( @Nullable String rawPath ) {
		if ( rawPath == null || rawPath.isBlank() ) {
			return null;
		}

		String path = rawPath;
		if ( path.startsWith( "file:" ) ) {
			path = VfsUtilCore.urlToPath( path );
		}
		path	= URLUtil.unescapePercentSequences( path );
		path	= debugProcess.mapRemotePathToLocal( path );

		return FileUtil.toSystemIndependentName( path );
	}
}
