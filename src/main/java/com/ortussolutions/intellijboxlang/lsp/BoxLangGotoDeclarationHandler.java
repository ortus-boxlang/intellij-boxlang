package com.ortussolutions.intellijboxlang.lsp;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.ortussolutions.intellijboxlang.file.BoxLangFileUtil;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.jetbrains.annotations.Nullable;

/**
 * Bridges IntelliJ goto-declaration (Cmd/Ctrl+Click) to LSP definition results.
 */
public final class BoxLangGotoDeclarationHandler implements GotoDeclarationHandler {

	private static final Logger							LOG							= Logger.getInstance( BoxLangGotoDeclarationHandler.class );
	private static final BoxLangLspAppContextResolver	APP_CONTEXT_RESOLVER		= new BoxLangLspAppContextResolver();
	private static final List<String>					CLASS_EXTENSIONS			= List.of( ".bx", ".cfc", ".bxs", ".cfm", ".bxm" );
	private static final Pattern						EXTENDS_IMPLEMENTS_PATTERN	= Pattern.compile(
	    "(?i)\\b(extends|implements)\\s*=\\s*(['\"])([^'\"]+)\\2"
	);

	@Override
	public PsiElement @Nullable [] getGotoDeclarationTargets( PsiElement sourceElement, int offset, Editor editor ) {
		if ( sourceElement == null || editor == null ) {
			return null;
		}
		PsiFile psiFile = sourceElement.getContainingFile();
		if ( psiFile == null ) {
			return null;
		}
		VirtualFile sourceVirtualFile = psiFile.getVirtualFile();
		if ( sourceVirtualFile == null || !sourceVirtualFile.isInLocalFileSystem() ) {
			return null;
		}
		// Rely on extension checks so navigation still works if another plugin supplies the file type.
		if ( !BoxLangFileUtil.isBoxLangFile( sourceVirtualFile ) ) {
			return null;
		}

		Project					project		= psiFile.getProject();
		Document				document	= editor.getDocument();
		BoxLangLspClientService	service		= BoxLangLspClientService.getInstance( project );

		for ( int candidateOffset : buildCandidateOffsets( document, offset ) ) {
			PsiElement[] targets = resolveTargetsAtOffset( project, service, sourceVirtualFile, document, candidateOffset );
			if ( targets.length > 0 ) {
				return targets;
			}
		}
		return null;
	}

	private static PsiElement[] resolveTargetsAtOffset(
	    Project project,
	    BoxLangLspClientService service,
	    VirtualFile sourceVirtualFile,
	    Document sourceDocument,
	    int offset ) {
		BoxLangLspAppContext	context		= service.getAppContextForFile( sourceVirtualFile );
		List<Location>			locations	= service.requestDefinition( sourceVirtualFile, sourceDocument, offset );
		if ( locations.isEmpty() ) {
			PsiElement[] mappedFallbackTargets = resolveMappedFallbackTargets( project, context, sourceDocument, offset );
			if ( mappedFallbackTargets.length > 0 ) {
				LOG.info(
				    "Resolved definition via mapped fallback: source=" + sourceVirtualFile.getPath() + ", requestedOffset=" + offset
				        + ", targetCount=" + mappedFallbackTargets.length
				);
				return mappedFallbackTargets;
			}
			LOG.info(
			    "No LSP definition results for " + sourceVirtualFile.getPath() + " at offset " + offset
			);
			return PsiElement.EMPTY_ARRAY;
		}
		PsiManager					psiManager	= PsiManager.getInstance( project );
		LinkedHashSet<PsiElement>	targets		= new LinkedHashSet<>();
		for ( Location location : locations ) {
			VirtualFile targetFile = resolveMappedReferenceVirtualFile( context, location );
			if ( targetFile == null ) {
				targetFile = findVirtualFile( location.getUri() );
			}
			if ( targetFile == null ) {
				LOG.info( "Definition target VirtualFile not found for URI: " + location.getUri() );
				continue;
			}
			PsiFile targetPsiFile = psiManager.findFile( targetFile );
			if ( targetPsiFile == null ) {
				LOG.info( "Definition target PsiFile not found for file: " + targetFile.getPath() );
				continue;
			}
			PsiElement target = findTargetElement( targetPsiFile, targetFile, location.getRange() != null ? location.getRange().getStart() : null );
			targets.add( target != null ? target : targetPsiFile );
		}
		LOG.info(
		    "Definition resolution summary: source=" + sourceVirtualFile.getPath() + ", requestedOffset=" + offset
		        + ", locationCount=" + locations.size() + ", psiTargetCount=" + targets.size()
		);
		return targets.toArray( PsiElement[]::new );
	}

	private static @Nullable VirtualFile resolveMappedReferenceVirtualFile(
	    @Nullable BoxLangLspAppContext context,
	    Location location ) {
		if ( context == null ) {
			return null;
		}
		String uriText = location != null ? location.getUri() : null;
		if ( uriText == null || uriText.isBlank() ) {
			return null;
		}
		String classReference = extractClassReferenceFromUriPath( uriText );
		if ( classReference == null || classReference.isBlank() ) {
			return null;
		}
		Path mappedClassPath = APP_CONTEXT_RESOLVER.resolveMappedClassPath( context, classReference );
		if ( mappedClassPath == null ) {
			return null;
		}
		LOG.info( "Mapped path expanded in plugin context: ref=" + classReference + ", path=" + mappedClassPath );
		return LocalFileSystem.getInstance().refreshAndFindFileByPath( mappedClassPath.toString() );
	}

	private static PsiElement[] resolveMappedFallbackTargets(
	    Project project,
	    @Nullable BoxLangLspAppContext context,
	    Document sourceDocument,
	    int offset ) {
		if ( context == null ) {
			return PsiElement.EMPTY_ARRAY;
		}
		String classReference = extractClassReferenceAtOffset( sourceDocument, offset );
		if ( classReference == null || classReference.isBlank() ) {
			return PsiElement.EMPTY_ARRAY;
		}

		Path mappedClassPath = APP_CONTEXT_RESOLVER.resolveMappedClassPath( context, classReference );
		if ( mappedClassPath == null ) {
			return PsiElement.EMPTY_ARRAY;
		}

		VirtualFile targetFile = LocalFileSystem.getInstance().refreshAndFindFileByPath( mappedClassPath.toString() );
		if ( targetFile == null ) {
			return PsiElement.EMPTY_ARRAY;
		}
		PsiFile targetPsiFile = PsiManager.getInstance( project ).findFile( targetFile );
		if ( targetPsiFile == null ) {
			return PsiElement.EMPTY_ARRAY;
		}
		PsiElement target = findTargetElement( targetPsiFile, targetFile, new Position( 0, 0 ) );
		return new PsiElement[] { target != null ? target : targetPsiFile };
	}

	private static @Nullable String extractClassReferenceAtOffset( Document document, int offset ) {
		String fromExtendsOrImplements = extractExtendsOrImplementsReference( document, offset );
		if ( fromExtendsOrImplements != null ) {
			return fromExtendsOrImplements;
		}
		return extractTokenReference( document, offset );
	}

	private static @Nullable String extractExtendsOrImplementsReference( Document document, int offset ) {
		if ( document.getLineCount() <= 0 ) {
			return null;
		}
		int		clampedOffset	= Math.max( 0, Math.min( offset, Math.max( document.getTextLength() - 1, 0 ) ) );
		int		line			= document.getLineNumber( clampedOffset );
		int		lineStart		= document.getLineStartOffset( line );
		int		lineEnd			= document.getLineEndOffset( line );
		String	lineText		= document.getText( new TextRange( lineStart, lineEnd ) );
		Matcher	matcher			= EXTENDS_IMPLEMENTS_PATTERN.matcher( lineText );
		while ( matcher.find() ) {
			int		keyStart	= lineStart + matcher.start( 1 );
			int		keyEnd		= lineStart + matcher.end( 1 );
			int		valueStart	= lineStart + matcher.start( 3 );
			int		valueEnd	= lineStart + matcher.end( 3 );
			boolean	onKey		= clampedOffset >= keyStart && clampedOffset <= keyEnd;
			boolean	onValue		= clampedOffset >= valueStart && clampedOffset <= valueEnd;
			if ( !onKey && !onValue ) {
				continue;
			}
			return chooseCommaSeparatedReference( matcher.group( 3 ), Math.max( 0, clampedOffset - valueStart ) );
		}
		return null;
	}

	private static @Nullable String chooseCommaSeparatedReference( @Nullable String rawReferences, int relativeOffset ) {
		if ( rawReferences == null || rawReferences.isBlank() ) {
			return null;
		}
		String value = rawReferences.trim();
		if ( !value.contains( "," ) ) {
			return value;
		}
		int	cursor			= Math.max( 0, Math.min( relativeOffset, Math.max( 0, rawReferences.length() - 1 ) ) );
		int	segmentStart	= 0;
		for ( int i = 0; i <= rawReferences.length(); i++ ) {
			boolean atDelimiter = i == rawReferences.length() || rawReferences.charAt( i ) == ',';
			if ( !atDelimiter ) {
				continue;
			}
			if ( cursor >= segmentStart && cursor <= i ) {
				String segment = rawReferences.substring( segmentStart, i ).trim();
				return segment.isBlank() ? null : segment;
			}
			segmentStart = i + 1;
		}
		return value;
	}

	private static @Nullable String extractTokenReference( Document document, int offset ) {
		if ( document.getTextLength() <= 0 ) {
			return null;
		}
		CharSequence	chars	= document.getCharsSequence();
		int				index	= Math.max( 0, Math.min( offset, chars.length() - 1 ) );
		if ( !isClassReferenceCharacter( chars.charAt( index ) ) ) {
			if ( index > 0 && isClassReferenceCharacter( chars.charAt( index - 1 ) ) ) {
				index = index - 1;
			} else if ( index < chars.length() - 1 && isClassReferenceCharacter( chars.charAt( index + 1 ) ) ) {
				index = index + 1;
			} else {
				return null;
			}
		}

		int start = index;
		while ( start > 0 && isClassReferenceCharacter( chars.charAt( start - 1 ) ) ) {
			start--;
		}
		int end = index + 1;
		while ( end < chars.length() && isClassReferenceCharacter( chars.charAt( end ) ) ) {
			end++;
		}
		String token = chars.subSequence( start, end ).toString().trim();
		if ( token.isEmpty() ) {
			return null;
		}
		token = token.replace( "\"", "" ).replace( "'", "" ).trim();
		if ( token.isEmpty() || !token.contains( "." ) ) {
			return null;
		}
		if ( token.toLowerCase( Locale.ROOT ).startsWith( "java." ) ) {
			return null;
		}
		return token;
	}

	private static @Nullable String extractClassReferenceFromUriPath( String uriText ) {
		if ( uriText == null || uriText.isBlank() ) {
			return null;
		}
		String rawPath = uriText;
		try {
			URI uri = URI.create( uriText );
			if ( uri.getPath() != null && !uri.getPath().isBlank() ) {
				rawPath = uri.getPath();
			}
		} catch ( Exception ignored ) {
			// Continue with raw text when URI parsing fails.
		}
		String normalized = rawPath.replace( '\\', '/' ).trim();
		if ( normalized.isBlank() ) {
			return null;
		}
		while ( normalized.startsWith( "/" ) ) {
			normalized = normalized.substring( 1 );
		}
		if ( normalized.isBlank() ) {
			return null;
		}
		for ( String extension : CLASS_EXTENSIONS ) {
			if ( normalized.toLowerCase( Locale.ROOT ).endsWith( extension ) ) {
				normalized = normalized.substring( 0, normalized.length() - extension.length() );
				break;
			}
		}
		if ( normalized.isBlank() || !normalized.contains( "/" ) ) {
			return null;
		}
		return normalized.replace( '/', '.' );
	}

	private static boolean isClassReferenceCharacter( char character ) {
		return Character.isLetterOrDigit( character ) || character == '_' || character == '$' || character == '.';
	}

	private static List<Integer> buildCandidateOffsets( Document document, int requestedOffset ) {
		int length = document.getTextLength();
		if ( length <= 0 ) {
			return List.of( 0 );
		}
		int						clamped		= Math.max( 0, Math.min( requestedOffset, length - 1 ) );
		LinkedHashSet<Integer>	candidates	= new LinkedHashSet<>();
		candidates.add( clamped );
		if ( clamped > 0 ) {
			candidates.add( clamped - 1 );
		}
		if ( clamped < length - 1 ) {
			candidates.add( clamped + 1 );
		}
		int nearbyReferenceOffset = findNearbyReferenceOffset( document, clamped );
		if ( nearbyReferenceOffset >= 0 ) {
			candidates.add( nearbyReferenceOffset );
		}
		return new ArrayList<>( candidates );
	}

	private static int findNearbyReferenceOffset( Document document, int offset ) {
		CharSequence text = document.getCharsSequence();
		if ( offset >= 0 && offset < text.length() && isReferenceCharacter( text.charAt( offset ) ) ) {
			return offset;
		}
		int	line	= document.getLineNumber( offset );
		int	start	= document.getLineStartOffset( line );
		int	end		= document.getLineEndOffset( line );
		for ( int i = offset + 1; i < end; i++ ) {
			if ( isReferenceCharacter( text.charAt( i ) ) ) {
				return i;
			}
		}
		for ( int i = offset - 1; i >= start; i-- ) {
			if ( isReferenceCharacter( text.charAt( i ) ) ) {
				return i;
			}
		}
		return -1;
	}

	private static boolean isReferenceCharacter( char character ) {
		return Character.isLetterOrDigit( character ) || character == '_' || character == '$' || character == '.';
	}

	private static @Nullable PsiElement findTargetElement( PsiFile targetPsiFile, VirtualFile targetFile, @Nullable Position position ) {
		Document targetDocument = FileDocumentManager.getInstance().getDocument( targetFile );
		if ( targetDocument == null ) {
			return targetPsiFile;
		}
		int			targetOffset	= BoxLangLspClientService.positionToOffset( targetDocument, position );
		PsiElement	element			= targetPsiFile.findElementAt( targetOffset );
		return element != null ? element : targetPsiFile;
	}

	private static @Nullable VirtualFile findVirtualFile( @Nullable String uriText ) {
		if ( uriText == null || uriText.isBlank() ) {
			return null;
		}
		VirtualFile fromUrl = VirtualFileManager.getInstance().findFileByUrl( uriText );
		if ( fromUrl != null ) {
			return fromUrl;
		}
		try {
			URI uri = URI.create( uriText );
			if ( uri.getPath() == null || uri.getPath().isBlank() ) {
				return null;
			}
			String		path	= Paths.get( uri ).toString();
			VirtualFile	local	= LocalFileSystem.getInstance().findFileByPath( path );
			if ( local != null ) {
				return local;
			}
			return LocalFileSystem.getInstance().refreshAndFindFileByPath( path );
		} catch ( Exception ignored ) {
			return null;
		}
	}

	@Override
	public @Nullable String getActionText( DataContext context ) {
		return null;
	}
}
