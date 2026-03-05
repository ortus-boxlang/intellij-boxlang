package com.ortussolutions.intellijboxlang.testbox;

import com.intellij.execution.lineMarker.ExecutorAction;
import com.intellij.execution.lineMarker.RunLineMarkerContributor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Provides test run icons in the gutter for TestBox spec files.
 *
 * BDD style: Shows icons on describe(), it(), test() calls.
 * xUnit style: Shows icons on function testXxx() and @test annotated functions.
 * File level: Shows icon on the class/first line for running the entire spec.
 */
public class TestBoxRunLineMarkerProvider extends RunLineMarkerContributor {

	// Pattern for describe("name", ...) or describe( "name", ... )
	private static final Pattern			DESCRIBE_CALL_PATTERN	= Pattern.compile(
	    "\\bdescribe\\s*\\(",
	    Pattern.CASE_INSENSITIVE
	);

	// Pattern for it("name", ...) or it( "name", ... )
	private static final Pattern			IT_CALL_PATTERN			= Pattern.compile(
	    "\\bit\\s*\\(",
	    Pattern.CASE_INSENSITIVE
	);

	// Pattern for test("name", ...) or test( "name", ... )
	private static final Pattern			TEST_CALL_PATTERN		= Pattern.compile(
	    "\\btest\\s*\\(",
	    Pattern.CASE_INSENSITIVE
	);

	// Pattern for xUnit-style: function testXxx(
	private static final Pattern			XUNIT_FUNC_PATTERN		= Pattern.compile(
	    "(?:^|\\s)(?:public\\s+|private\\s+|remote\\s+|static\\s+)*(?:\\w+\\s+)?function\\s+(test\\w+)\\s*\\(",
	    Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
	);

	// Pattern for @test annotation followed by function declaration
	private static final Pattern			ANNOTATION_FUNC_PATTERN	= Pattern.compile(
	    "@test\\b",
	    Pattern.CASE_INSENSITIVE
	);

	private static final Key<TestLineCache>	TEST_LINES_CACHE_KEY	= Key.create( "testbox.test.lines.cache" );

	@Override
	public @Nullable Info getInfo( @NotNull PsiElement element ) {
		PsiFile file = element.getContainingFile();
		if ( file == null ) {
			return null;
		}

		VirtualFile vFile = file.getVirtualFile();
		if ( vFile == null ) {
			return null;
		}

		// Only show for TestBox spec files
		if ( !TestBoxUtil.isTestBoxSpecFile( vFile ) ) {
			return null;
		}

		// Check that TestBox is installed in the project
		Project project = file.getProject();
		if ( !TestBoxUtil.isTestBoxInstalled( project ) ) {
			return null;
		}

		Document document = PsiDocumentManager.getInstance( project ).getDocument( file );
		if ( document == null ) {
			return null;
		}

		int elementOffset = element.getTextOffset();
		if ( elementOffset < 0 || elementOffset > document.getTextLength() ) {
			return null;
		}

		int elementLine = document.getLineNumber( Math.min( elementOffset, document.getTextLength() - 1 ) );

		// Check if this is the first element on its line (avoid duplicates)
		if ( !isFirstElementOnLine( element, document ) ) {
			return null;
		}

		// Get cached test line info
		TestLineCache cache = getCachedTestLines( file );
		if ( cache == null ) {
			return null;
		}

		// Check if this line is a test marker line
		TestLineInfo lineInfo = cache.getInfoForLine( elementLine );
		if ( lineInfo == null ) {
			return null;
		}

		AnAction[]	actions	= ExecutorAction.getActions( 0 );
		String		tooltip	= lineInfo.tooltip;

		return new Info(
		    lineInfo.type == TestLineType.SUITE
		        ? AllIcons.RunConfigurations.TestState.Run_run
		        : AllIcons.RunConfigurations.TestState.Run,
		    actions,
		    psiElement -> tooltip
		);
	}

	private @Nullable TestLineCache getCachedTestLines( @NotNull PsiFile file ) {
		long			modStamp	= file.getModificationStamp();
		TestLineCache	cached		= file.getUserData( TEST_LINES_CACHE_KEY );
		if ( cached != null && cached.modificationStamp == modStamp ) {
			return cached;
		}

		String text = file.getText();
		if ( text == null || text.isEmpty() ) {
			return null;
		}

		TestLineCache newCache = computeTestLines( text, modStamp );
		file.putUserData( TEST_LINES_CACHE_KEY, newCache );
		return newCache;
	}

	private TestLineCache computeTestLines( @NotNull String text, long modStamp ) {
		List<TestLineInfo> lines = new ArrayList<>();

		// Find describe() calls
		findPatternLines( text, DESCRIBE_CALL_PATTERN, lines, TestLineType.SUITE, "Run suite" );

		// Find it() calls
		findPatternLines( text, IT_CALL_PATTERN, lines, TestLineType.SPEC, "Run test" );

		// Find test() calls (BDD alias) — but exclude "testXxx(" which is xUnit
		findTestCallLines( text, lines );

		// Find xUnit function testXxx() declarations
		findXunitTestLines( text, lines );

		// Find @test annotation lines
		findAnnotationTestLines( text, lines );

		// If there are any test lines, add a class-level marker on line 0
		if ( !lines.isEmpty() ) {
			boolean hasLine0 = lines.stream().anyMatch( l -> l.line == 0 );
			if ( !hasLine0 ) {
				lines.add( new TestLineInfo( 0, TestLineType.SUITE, "Run all tests" ) );
			}
		}

		return new TestLineCache( modStamp, lines );
	}

	private void findPatternLines( @NotNull String text, @NotNull Pattern pattern,
	    @NotNull List<TestLineInfo> lines, @NotNull TestLineType type, @NotNull String tooltipPrefix ) {
		Matcher matcher = pattern.matcher( text );
		while ( matcher.find() ) {
			int offset = matcher.start();
			// For it() and test(), verify the char before is not alphanumeric
			// (to avoid matching "exit(", "wait(" etc.)
			if ( offset > 0 && Character.isLetterOrDigit( text.charAt( offset - 1 ) ) ) {
				continue;
			}

			int		line	= getLineNumber( text, offset );
			String	name	= extractNameArgument( text, matcher.end() );
			String	tooltip	= name != null ? tooltipPrefix + " '" + name + "'" : tooltipPrefix;
			lines.add( new TestLineInfo( line, type, tooltip ) );
		}
	}

	private void findTestCallLines( @NotNull String text, @NotNull List<TestLineInfo> lines ) {
		Matcher matcher = TEST_CALL_PATTERN.matcher( text );
		while ( matcher.find() ) {
			int offset = matcher.start();
			// Verify the char before "test" is not alphanumeric
			// (to ensure it's a standalone call, not part of "function testXxx")
			if ( offset > 0 && Character.isLetterOrDigit( text.charAt( offset - 1 ) ) ) {
				continue;
			}
			// Check if this looks like "function test" (xUnit) — skip those, handled separately
			String before = text.substring( Math.max( 0, offset - 20 ), offset ).trim().toLowerCase( Locale.ROOT );
			if ( before.endsWith( "function" ) ) {
				continue;
			}

			int		line	= getLineNumber( text, offset );
			String	name	= extractNameArgument( text, matcher.end() );
			String	tooltip	= name != null ? "Run test '" + name + "'" : "Run test";
			lines.add( new TestLineInfo( line, TestLineType.SPEC, tooltip ) );
		}
	}

	private void findXunitTestLines( @NotNull String text, @NotNull List<TestLineInfo> lines ) {
		Matcher matcher = XUNIT_FUNC_PATTERN.matcher( text );
		while ( matcher.find() ) {
			int		offset		= matcher.start();
			int		line		= getLineNumber( text, offset );
			String	funcName	= matcher.group( 1 );
			String	tooltip		= "Run test '" + funcName + "'";
			lines.add( new TestLineInfo( line, TestLineType.SPEC, tooltip ) );
		}
	}

	private void findAnnotationTestLines( @NotNull String text, @NotNull List<TestLineInfo> lines ) {
		Matcher matcher = ANNOTATION_FUNC_PATTERN.matcher( text );
		while ( matcher.find() ) {
			int		offset		= matcher.start();
			int		line		= getLineNumber( text, offset );
			// Find the function name on the next line(s)
			String	funcName	= findNextFunctionName( text, matcher.end() );
			String	tooltip		= funcName != null ? "Run test '" + funcName + "'" : "Run test";
			lines.add( new TestLineInfo( line, TestLineType.SPEC, tooltip ) );
		}
	}

	/**
	 * Extracts the string argument from a function call, e.g., the "name" from it("name", ...).
	 */
	private @Nullable String extractNameArgument( @NotNull String text, int afterParenPos ) {
		return TestBoxRunConfigurationProducer.extractStringArgument( text, afterParenPos );
	}

	/**
	 * Finds the next function name after an @test annotation.
	 */
	private @Nullable String findNextFunctionName( @NotNull String text, int afterAnnotation ) {
		Pattern	funcPattern	= Pattern.compile(
		    "\\bfunction\\s+(\\w+)\\s*\\(",
		    Pattern.CASE_INSENSITIVE
		);
		Matcher	matcher		= funcPattern.matcher( text );
		if ( matcher.find( afterAnnotation ) ) {
			// Only match if it's within a reasonable distance (e.g., 200 chars)
			if ( matcher.start() - afterAnnotation < 200 ) {
				return matcher.group( 1 );
			}
		}
		return null;
	}

	private int getLineNumber( @NotNull String text, int offset ) {
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

	private boolean isFirstElementOnLine( @NotNull PsiElement element, @NotNull Document document ) {
		int elementOffset = element.getTextOffset();
		if ( elementOffset < 0 || elementOffset > document.getTextLength() ) {
			return false;
		}

		int				lineNumber	= document.getLineNumber( Math.min( elementOffset, document.getTextLength() - 1 ) );
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

	// --- Inner types ---

	private enum TestLineType {
		SUITE, SPEC
	}

	private static final class TestLineInfo {

		final int			line;
		final TestLineType	type;
		final String		tooltip;

		TestLineInfo( int line, TestLineType type, String tooltip ) {
			this.line		= line;
			this.type		= type;
			this.tooltip	= tooltip;
		}
	}

	private static final class TestLineCache {

		final long					modificationStamp;
		final List<TestLineInfo>	lines;

		TestLineCache( long modificationStamp, List<TestLineInfo> lines ) {
			this.modificationStamp	= modificationStamp;
			this.lines				= lines;
		}

		@Nullable
		TestLineInfo getInfoForLine( int line ) {
			for ( TestLineInfo info : lines ) {
				if ( info.line == line ) {
					return info;
				}
			}
			return null;
		}
	}
}
