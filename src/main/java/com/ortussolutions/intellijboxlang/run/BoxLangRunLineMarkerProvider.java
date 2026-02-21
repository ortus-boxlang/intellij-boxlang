package com.ortussolutions.intellijboxlang.run;

import com.intellij.execution.lineMarker.ExecutorAction;
import com.intellij.execution.lineMarker.RunLineMarkerContributor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Provides run icons in the gutter for BoxLang files.
 * 
 * For .bx (class) files: Shows run icon on the main() method if present.
 * For .bxs (script) files: Shows run icon on the first line.
 * For .bxm (template) files: Shows run icon on the first line.
 */
public class BoxLangRunLineMarkerProvider extends RunLineMarkerContributor {

	// Pattern to match "function main(" with optional whitespace and modifiers
	// Matches: function main(), public function main(), static function main(), etc.
	private static final Pattern				MAIN_FUNCTION_PATTERN	= Pattern.compile(
	    "(?:^|\\s)(?:public\\s+|private\\s+|static\\s+)*function\\s+main\\s*\\(",
	    Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
	);
	private static final Key<MainFunctionCache>	MAIN_FUNCTION_CACHE_KEY	= Key.create( "boxlang.main.function.cache" );

	@Override
	public @Nullable Info getInfo( @NotNull PsiElement element ) {
		PsiFile file = element.getContainingFile();
		if ( file == null ) {
			return null;
		}

		String fileName = file.getName();
		if ( fileName == null ) {
			return null;
		}

		String lowerName = fileName.toLowerCase( Locale.ROOT );

		// For .bx (class) files, only show on the main method
		if ( lowerName.endsWith( ".bx" ) ) {
			return getInfoForClassFile( element, file );
		}

		// For .bxs and .bxm files, show on first line
		if ( lowerName.endsWith( ".bxs" ) || lowerName.endsWith( ".bxm" ) ) {
			return getInfoForScriptFile( element, file );
		}

		return null;
	}

	/**
	 * For .bx class files, show run icon on the main() method.
	 */
	private @Nullable Info getInfoForClassFile( @NotNull PsiElement element, @NotNull PsiFile file ) {
		int mainLine = getCachedMainFunctionLine( file );
		if ( mainLine < 0 ) {
			return null;
		}

		Document document = PsiDocumentManager.getInstance( file.getProject() ).getDocument( file );
		if ( document == null ) {
			return null;
		}

		// Get the line number of the current element
		int elementOffset = element.getTextOffset();
		if ( elementOffset < 0 || elementOffset > document.getTextLength() ) {
			return null;
		}
		int elementLine = getLineNumber( document, elementOffset );

		// Only show the marker if this element is on the same line as main()
		if ( elementLine != mainLine ) {
			return null;
		}

		// Check if this is the first element on this line to avoid duplicates
		if ( !isFirstElementOnLine( element, document ) ) {
			return null;
		}

		AnAction[] actions = ExecutorAction.getActions( 0 );
		return new Info(
		    AllIcons.RunConfigurations.TestState.Run,
		    actions,
		    psiElement -> "Run " + file.getName()
		);
	}

	private int getCachedMainFunctionLine( @NotNull PsiFile file ) {
		long				modificationStamp	= file.getModificationStamp();
		MainFunctionCache	cached				= file.getUserData( MAIN_FUNCTION_CACHE_KEY );
		if ( cached != null && cached.modificationStamp == modificationStamp ) {
			return cached.mainLine;
		}

		int mainLine = computeMainFunctionLine( file.getText() );
		file.putUserData( MAIN_FUNCTION_CACHE_KEY, new MainFunctionCache( modificationStamp, mainLine ) );
		return mainLine;
	}

	private int computeMainFunctionLine( @Nullable String text ) {
		if ( text == null || text.isEmpty() ) {
			return -1;
		}
		Matcher matcher = MAIN_FUNCTION_PATTERN.matcher( text );
		if ( !matcher.find() ) {
			return -1;
		}

		int		mainOffset	= matcher.start();
		// Skip any leading whitespace in the match to get to "function" or modifier.
		String	match		= matcher.group();
		if ( !StringUtil.isEmpty( match ) && Character.isWhitespace( match.charAt( 0 ) ) ) {
			mainOffset += 1;
		}
		return getLineNumber( text, mainOffset );
	}

	/**
	 * For .bxs and .bxm files, show run icon on the first line.
	 */
	private @Nullable Info getInfoForScriptFile( @NotNull PsiElement element, @NotNull PsiFile file ) {
		if ( !isFirstElementInFile( element ) ) {
			return null;
		}

		AnAction[] actions = ExecutorAction.getActions( 0 );
		return new Info(
		    AllIcons.RunConfigurations.TestState.Run,
		    actions,
		    psiElement -> "Run " + file.getName()
		);
	}

	/**
	 * Gets the line number (0-indexed) for a given offset in the text.
	 */
	private int getLineNumber( String text, int offset ) {
		int line = 0;
		for ( int i = 0; i < offset && i < text.length(); i++ ) {
			char c = text.charAt( i );
			if ( c == '\n' ) {
				line++;
			} else if ( c == '\r' ) {
				line++;
				if ( i + 1 < text.length() && text.charAt( i + 1 ) == '\n' ) {
					i++;
				}
			}
		}
		return line;
	}

	private int getLineNumber( @NotNull Document document, int offset ) {
		if ( document.getTextLength() == 0 ) {
			return 0;
		}
		int safeOffset = Math.max( 0, Math.min( offset, document.getTextLength() - 1 ) );
		return document.getLineNumber( safeOffset );
	}

	/**
	 * Checks if this element is the first element on its line.
	 */
	private boolean isFirstElementOnLine( @NotNull PsiElement element, @NotNull Document document ) {
		int elementOffset = element.getTextOffset();
		if ( elementOffset < 0 || elementOffset > document.getTextLength() ) {
			return false;
		}

		int				lineNumber	= getLineNumber( document, elementOffset );
		int				lineStart	= document.getLineStartOffset( lineNumber );
		CharSequence	chars		= document.getCharsSequence();

		for ( int i = lineStart; i < elementOffset && i < chars.length(); i++ ) {
			char c = chars.charAt( i );
			if ( c != ' ' && c != '\t' ) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Checks if the element is the first significant element in the file.
	 */
	private boolean isFirstElementInFile( PsiElement element ) {
		PsiFile file = element.getContainingFile();
		if ( file == null ) {
			return false;
		}

		PsiElement firstChild = file.getFirstChild();
		if ( firstChild == null ) {
			return false;
		}

		// Navigate to the first leaf element
		while ( firstChild.getFirstChild() != null ) {
			firstChild = firstChild.getFirstChild();
		}

		return element.equals( firstChild );
	}

	private static final class MainFunctionCache {

		private final long	modificationStamp;
		private final int	mainLine;

		private MainFunctionCache( long modificationStamp, int mainLine ) {
			this.modificationStamp	= modificationStamp;
			this.mainLine			= mainLine;
		}
	}
}
