package com.ortussolutions.intellijboxlang.testbox;

import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.LazyRunConfigurationProducer;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Produces TestBox run configurations from context (e.g., right-click on a test file).
 * This enables "Run TestBox tests" in the context menu for TestBox spec files.
 */
public class TestBoxRunConfigurationProducer extends LazyRunConfigurationProducer<TestBoxRunConfiguration> {

	@Override
	public @NotNull ConfigurationFactory getConfigurationFactory() {
		TestBoxConfigurationType type = ConfigurationTypeUtil.findConfigurationType( TestBoxConfigurationType.class );
		return type.getConfigurationFactories()[ 0 ];
	}

	@Override
	protected boolean setupConfigurationFromContext( @NotNull TestBoxRunConfiguration configuration,
	    @NotNull ConfigurationContext context,
	    @NotNull Ref<PsiElement> sourceElement ) {
		PsiFile psiFile = getPsiFile( context );
		if ( psiFile == null ) {
			return false;
		}

		VirtualFile file = psiFile.getVirtualFile();
		if ( file == null ) {
			return false;
		}

		Project project = context.getProject();

		// Check that TestBox is installed
		if ( !TestBoxUtil.isTestBoxInstalled( project ) ) {
			return false;
		}

		// Check that this is a test spec file
		if ( !TestBoxUtil.isTestBoxSpecFile( file ) ) {
			return false;
		}

		// Compute the dot-notation bundle path
		String bundlePath = TestBoxUtil.computeBundlePath( project, file );
		if ( bundlePath == null ) {
			return false;
		}

		// Set up configuration for this specific bundle
		configuration.setTestScope( "BUNDLE" );
		configuration.setBundlePath( bundlePath );
		configuration.setName( file.getNameWithoutExtension() );

		// Check if the context is inside a specific describe/it/test call
		// and set filter-specs or filter-suites accordingly
		PsiElement element = context.getPsiLocation();
		if ( element != null ) {
			SpecInfo specInfo = findEnclosingSpecName( element );
			if ( specInfo != null && !specInfo.filterValue.isEmpty() ) {
				if ( specInfo.isSuite ) {
					// Suite-level run: use --filter-suites (matches by name, no hashing needed)
					configuration.setFilterSuites( specInfo.filterValue );
					configuration.setFilterSpecs( "" );
				} else {
					// Spec-level run: use --filter-specs (matches by MD5 hash of spec name)
					configuration.setFilterSpecs( specInfo.filterValue );
					configuration.setFilterSuites( "" );
				}
				configuration.setName( file.getNameWithoutExtension() + "." + specInfo.displayName );
			}
		}

		sourceElement.set( context.getPsiLocation() );
		return true;
	}

	@Override
	public boolean isConfigurationFromContext( @NotNull TestBoxRunConfiguration configuration,
	    @NotNull ConfigurationContext context ) {
		PsiFile psiFile = getPsiFile( context );
		if ( psiFile == null ) {
			return false;
		}

		VirtualFile file = psiFile.getVirtualFile();
		if ( file == null ) {
			return false;
		}

		Project	project		= context.getProject();
		String	bundlePath	= TestBoxUtil.computeBundlePath( project, file );
		if ( bundlePath == null ) {
			return false;
		}

		// Check if bundle paths match
		if ( !"BUNDLE".equals( configuration.getTestScope() ) ) {
			return false;
		}

		String configBundlePath = configuration.getBundlePath();
		if ( !bundlePath.equals( configBundlePath ) ) {
			return false;
		}

		// Check spec/suite filter matches
		PsiElement	element		= context.getPsiLocation();
		SpecInfo	specInfo	= element != null ? findEnclosingSpecName( element ) : null;

		if ( specInfo != null && specInfo.isSuite ) {
			// Suite-level run: compare filter-suites
			String	suiteFilter			= specInfo.filterValue;
			String	configSuiteFilter	= configuration.getFilterSuites();
			String	configSpecFilter	= configuration.getFilterSpecs();
			// Must have matching filter-suites and empty filter-specs
			return suiteFilter.equals( configSuiteFilter )
			    && ( configSpecFilter == null || configSpecFilter.isEmpty() );
		}

		String	specFilter			= specInfo != null ? specInfo.filterValue : null;
		String	configFilter		= configuration.getFilterSpecs();
		String	configSuiteFilter	= configuration.getFilterSuites();

		if ( specFilter == null || specFilter.isEmpty() ) {
			return ( configFilter == null || configFilter.isEmpty() )
			    && ( configSuiteFilter == null || configSuiteFilter.isEmpty() );
		}
		return specFilter.equals( configFilter )
		    && ( configSuiteFilter == null || configSuiteFilter.isEmpty() );
	}

	/**
	 * Attempts to find the name of the enclosing test spec for the given PSI element.
	 * For BDD style: looks for it("name") or test("name") calls.
	 * For xUnit style: looks for function testXxx() declarations.
	 * Returns a SpecInfo with both the display name and the hashable filter value,
	 * or null if not inside a spec.
	 */
	private SpecInfo findEnclosingSpecName( @NotNull PsiElement element ) {
		PsiFile file = element.getContainingFile();
		if ( file == null ) {
			return null;
		}

		String	text			= file.getText();
		int		elementOffset	= element.getTextOffset();

		// Search backwards from the element offset for the nearest spec
		return findNearestSpec( text, elementOffset );
	}

	/**
	 * Holds information about a test target (spec or suite) found at a cursor position.
	 * For specs: displayName is the spec title, filterValue is the hashable value
	 * (suiteContext + specTitle for BDD, or funcName for xUnit).
	 * For suites: displayName is the suite title, filterValue is the suite name,
	 * and isSuite is true (so the producer uses --filter-suites instead of --filter-specs).
	 */
	static class SpecInfo {

		final String	displayName;
		final String	filterValue;
		final boolean	isSuite;

		SpecInfo( String displayName, String filterValue ) {
			this( displayName, filterValue, false );
		}

		SpecInfo( String displayName, String filterValue, boolean isSuite ) {
			this.displayName	= displayName;
			this.filterValue	= filterValue;
			this.isSuite		= isSuite;
		}
	}

	static SpecInfo findNearestSpec( @NotNull String text, int offset ) {
		String	lowerText	= text.toLowerCase();
		int		searchFrom	= Math.min( offset, text.length() - 1 );

		// Look backwards for it(, test(, function testXxx(, or describe()/suite-creating calls
		for ( int i = searchFrom; i >= 0; i-- ) {
			// Check for it("name"
			if ( i + 3 <= text.length() && lowerText.startsWith( "it(", i )
			    && ( i == 0 || !Character.isLetterOrDigit( text.charAt( i - 1 ) ) ) ) {
				String name = extractStringArgument( text, i + 3 );
				if ( name != null ) {
					// BDD spec: TestBox computes spec.id = hash(suiteContext + specTitle)
					// where suiteContext is the immediate parent describe() name.
					String	suiteContext	= findEnclosingDescribeName( text, i );
					String	filterValue		= suiteContext != null ? suiteContext + name : name;
					return new SpecInfo( name, filterValue );
				}
			}
			// Check for test("name" — but not "function test"
			if ( i + 5 <= text.length() && lowerText.startsWith( "test(", i )
			    && ( i == 0 || !Character.isLetterOrDigit( text.charAt( i - 1 ) ) ) ) {
				// Make sure it's not "function test(" (xUnit) — that's handled below
				String before = text.substring( Math.max( 0, i - 20 ), i ).trim().toLowerCase();
				if ( !before.endsWith( "function" ) ) {
					String name = extractStringArgument( text, i + 5 );
					if ( name != null ) {
						// BDD spec (test() is an alias for it()): same logic
						String	suiteContext	= findEnclosingDescribeName( text, i );
						String	filterValue		= suiteContext != null ? suiteContext + name : name;
						return new SpecInfo( name, filterValue );
					}
				}
			}
			// Check for xUnit-style: function testXxx(
			if ( i + 9 <= text.length() && lowerText.startsWith( "function ", i ) ) {
				// Verify "function" is preceded by whitespace or is at start
				if ( i == 0 || !Character.isLetterOrDigit( text.charAt( i - 1 ) ) ) {
					Matcher m = XUNIT_FUNC_AT_POS.matcher( text.substring( i ) );
					if ( m.find() && m.start() == 0 ) {
						String funcName = m.group( 1 );
						if ( funcName.toLowerCase().startsWith( "test" ) ) {
							// xUnit spec: TestBox computes spec.id = hash(funcName)
							// No suite context needed.
							return new SpecInfo( funcName, funcName );
						}
					}
				}
			}
			// Check for suite-creating calls: describe(, story(, feature(, given(, when(, scenario(
			int afterParen = matchSuiteCall( lowerText, text, i );
			if ( afterParen >= 0 ) {
				String name = extractStringArgument( text, afterParen );
				if ( name != null ) {
					// Verify the cursor is on or near this describe line, not just inside it.
					// We need the opening brace of the body to check if offset is INSIDE the body
					// (meaning a child spec/suite should be picked up instead) or ON the describe line
					// itself (meaning the user wants to run this suite).
					int bracePos = findBodyOpenBrace( text, afterParen );
					if ( bracePos < 0 || offset <= bracePos ) {
						// Cursor is before or at the opening brace — user clicked on the describe line
						return new SpecInfo( name, name, true );
					}
					// Cursor is inside the body — continue searching backwards for a spec
				}
			}
		}

		return null;
	}

	/**
	 * Finds the name of the immediately enclosing describe() block for a position in the text.
	 * Uses brace-depth tracking to find which describe() block the position is inside of.
	 *
	 * TestBox sets $suiteContext to the current describe()'s title, so for nested describes:
	 * describe("A", () => { describe("B", () => { it("x") }) })
	 * The suiteContext for it("x") is "B" (immediate parent), not "A".
	 *
	 * @return the describe title, or null if not inside a describe block
	 */
	static String findEnclosingDescribeName( @NotNull String text, int specOffset ) {
		String				lowerText	= text.toLowerCase();

		// Collect all describe/story/feature/given/when/scenario positions with their names
		// and the position of their opening brace (the body closure's '{')
		List<DescribeBlock>	describes	= new ArrayList<>();

		for ( int i = 0; i < specOffset; i++ ) {
			// Match describe(, story(, feature(, given(, when(, scenario( — all suite-creating aliases in TestBox
			int afterParen = matchSuiteCall( lowerText, text, i );
			if ( afterParen >= 0 ) {
				String name = extractStringArgument( text, afterParen );
				if ( name != null ) {
					// Find the opening brace of the body closure after the string arg
					int bracePos = findBodyOpenBrace( text, afterParen );
					if ( bracePos >= 0 && bracePos < specOffset ) {
						describes.add( new DescribeBlock( name, bracePos ) );
					}
				}
			}
		}

		if ( describes.isEmpty() ) {
			return null;
		}

		// For each describe, determine if specOffset is inside its brace block
		// by tracking brace depth from the describe's opening brace.
		// The innermost (last) describe whose block contains specOffset wins.
		String result = null;
		for ( DescribeBlock db : describes ) {
			if ( isInsideBraceBlock( text, db.openBracePos, specOffset ) ) {
				result = db.name;
			}
		}
		return result;
	}

	/**
	 * Checks if the position at index i in lowerText is the start of a suite-creating
	 * function call (describe, story, feature, given, when, scenario).
	 * Returns the position after the opening paren, or -1 if not a match.
	 */
	private static int matchSuiteCall( @NotNull String lowerText, @NotNull String text, int i ) {
		String[] keywords = { "describe(", "story(", "feature(", "given(", "when(", "scenario(" };
		for ( String kw : keywords ) {
			if ( i + kw.length() <= lowerText.length() && lowerText.startsWith( kw, i ) ) {
				// Ensure it's not part of a longer identifier
				if ( i == 0 || !Character.isLetterOrDigit( text.charAt( i - 1 ) ) ) {
					return i + kw.length();
				}
			}
		}
		return -1;
	}

	/**
	 * Starting from the position right after the opening parenthesis of a suite-creating call,
	 * find the opening brace of the body closure/lambda.
	 *
	 * Handles both positional and named argument forms:
	 * - Positional: describe( "name", function(){...} )
	 * - Named: describe( title = "name", body = function(){...} )
	 *
	 * Scans forward, skipping string literals to avoid matching braces inside strings,
	 * and returns the position of the first '{' that starts the body.
	 */
	private static int findBodyOpenBrace( @NotNull String text, int afterParenPos ) {
		int pos = afterParenPos;
		while ( pos < text.length() ) {
			char c = text.charAt( pos );

			// Skip string literals to avoid matching braces or other chars inside strings
			if ( c == '"' || c == '\'' ) {
				char quote = c;
				pos++;
				while ( pos < text.length() ) {
					char sc = text.charAt( pos );
					if ( sc == '\\' && pos + 1 < text.length() ) {
						pos += 2;
						continue;
					}
					if ( sc == quote ) {
						break;
					}
					pos++;
				}
				pos++;
				continue;
			}

			if ( c == '{' ) {
				return pos;
			}
			// Stop at unexpected tokens to avoid runaway scanning
			if ( c == ';' ) {
				return -1;
			}
			pos++;
		}
		return -1;
	}

	/**
	 * Checks if targetOffset falls inside the brace block that opens at openBracePos.
	 * Uses brace-depth counting from the opening brace, skipping string literals
	 * to avoid counting braces inside strings.
	 */
	private static boolean isInsideBraceBlock( @NotNull String text, int openBracePos, int targetOffset ) {
		int depth = 0;
		for ( int i = openBracePos; i < text.length(); i++ ) {
			char c = text.charAt( i );

			// Skip string literals to avoid counting braces inside strings
			if ( c == '"' || c == '\'' ) {
				char quote = c;
				i++;
				while ( i < text.length() ) {
					char sc = text.charAt( i );
					if ( sc == '\\' && i + 1 < text.length() ) {
						i += 2;
						continue;
					}
					if ( sc == quote ) {
						break;
					}
					i++;
				}
				continue;
			}

			if ( c == '{' ) {
				depth++;
			} else if ( c == '}' ) {
				depth--;
				if ( depth == 0 ) {
					// Block closed: target is inside if closing brace is at or after target
					return i >= targetOffset;
				}
			}
			if ( i >= targetOffset && depth > 0 ) {
				return true;
			}
		}
		// Reached end of file without closing — target is inside if depth > 0
		return depth > 0;
	}

	private static class DescribeBlock {

		final String	name;
		final int		openBracePos;

		DescribeBlock( String name, int openBracePos ) {
			this.name			= name;
			this.openBracePos	= openBracePos;
		}
	}

	/**
	 * Pattern to match "function testXxx(" at the start of a substring.
	 * Captures the function name.
	 */
	private static final Pattern XUNIT_FUNC_AT_POS = Pattern.compile(
	    "function\\s+(test\\w+)\\s*\\(",
	    Pattern.CASE_INSENSITIVE
	);

	/**
	 * Extracts a string literal argument starting at the given position.
	 * Expects the position to be right after the opening parenthesis.
	 * Handles both positional and named argument forms:
	 * - Positional: it( "spec name", ... )
	 * - Named: describe( title = "A spec", ... )
	 * Handles both single and double quoted strings.
	 */
	static String extractStringArgument( @NotNull String text, int afterParenPos ) {
		int pos = afterParenPos;
		// Skip whitespace
		while ( pos < text.length() && Character.isWhitespace( text.charAt( pos ) ) ) {
			pos++;
		}
		if ( pos >= text.length() ) {
			return null;
		}

		char ch = text.charAt( pos );

		// If the first non-whitespace char is a quote, it's positional — parse it directly
		if ( ch == '"' || ch == '\'' ) {
			return extractQuotedString( text, pos );
		}

		// Otherwise, it might be a named argument: `title = "value"` or `title="value"`
		// Look for an identifier followed by optional whitespace, '=', optional whitespace, then a quote
		if ( Character.isLetter( ch ) || ch == '_' ) {
			int identStart = pos;
			while ( pos < text.length() && ( Character.isLetterOrDigit( text.charAt( pos ) ) || text.charAt( pos ) == '_' ) ) {
				pos++;
			}
			// Skip whitespace after identifier
			while ( pos < text.length() && Character.isWhitespace( text.charAt( pos ) ) ) {
				pos++;
			}
			// Expect '='
			if ( pos < text.length() && text.charAt( pos ) == '=' ) {
				pos++;
				// Skip whitespace after '='
				while ( pos < text.length() && Character.isWhitespace( text.charAt( pos ) ) ) {
					pos++;
				}
				if ( pos < text.length() ) {
					char q = text.charAt( pos );
					if ( q == '"' || q == '\'' ) {
						return extractQuotedString( text, pos );
					}
				}
			}
		}

		return null;
	}

	/**
	 * Extracts a quoted string starting at the given position (which must be a quote character).
	 * Handles escape sequences with backslash.
	 */
	private static String extractQuotedString( @NotNull String text, int quotePos ) {
		char			quote	= text.charAt( quotePos );
		int				pos		= quotePos + 1;

		StringBuilder	sb		= new StringBuilder();
		while ( pos < text.length() ) {
			char c = text.charAt( pos );
			if ( c == '\\' && pos + 1 < text.length() ) {
				sb.append( text.charAt( pos + 1 ) );
				pos += 2;
				continue;
			}
			if ( c == quote ) {
				return sb.toString();
			}
			sb.append( c );
			pos++;
		}
		return null;
	}

	private PsiFile getPsiFile( @NotNull ConfigurationContext context ) {
		PsiElement element = context.getPsiLocation();
		if ( element == null ) {
			return null;
		}
		return element.getContainingFile();
	}
}
