package com.ortussolutions.intellijboxlang.lexer;

import com.intellij.lexer.LexerBase;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class BoxLangLexer extends LexerBase {

	// Lexer states
	private static final int	STATE_NORMAL			= 0;
	private static final int	STATE_IN_SINGLE_STRING	= 1;
	private static final int	STATE_IN_DOUBLE_STRING	= 2;
	private static final int	STATE_IN_INTERPOLATION	= 3;

	private CharSequence		buffer					= "";
	private int					bufferEnd				= 0;
	private int					tokenStart				= 0;
	private int					tokenEnd				= 0;
	private IElementType		tokenType;
	private int					position				= 0;
	private int					state					= STATE_NORMAL;

	// Stack for nested interpolations - tracks the quote char and depth
	private char				stringQuoteChar			= 0;
	private int					interpolationDepth		= 0;

	// Track whether the last non-whitespace token was "function" keyword for function-name detection
	private boolean				lastWasFunctionKeyword	= false;

	// Track the last non-whitespace token type for context-sensitive classification
	private IElementType		lastNonWsTokenType		= null;

	// Track parenthesis nesting depth for named-argument detection
	private int					parenDepth				= 0;

	@Override
	public void start( @NotNull CharSequence buffer, int startOffset, int endOffset, int initialState ) {
		this.buffer				= buffer;
		bufferEnd				= endOffset;
		position				= startOffset;
		tokenStart				= startOffset;
		tokenEnd				= startOffset;
		tokenType				= null;
		state					= decodeState( initialState );
		stringQuoteChar			= decodeQuoteChar( initialState );
		interpolationDepth		= decodeDepth( initialState );
		lastWasFunctionKeyword	= false;
		lastNonWsTokenType		= null;
		parenDepth				= 0;
		advance();
	}

	@Override
	public int getState() {
		return encodeState( state, stringQuoteChar, interpolationDepth );
	}

	private int encodeState( int state, char quoteChar, int depth ) {
		// Pack state info: bits 0-1 for state, bit 2 for quote type (0=single, 1=double), bits 3-7 for depth
		int quoteBit = quoteChar == '"' ? 1 : 0;
		return ( state & 0x3 ) | ( ( quoteBit & 0x1 ) << 2 ) | ( ( depth & 0x1F ) << 3 );
	}

	private int decodeState( int encoded ) {
		return encoded & 0x3;
	}

	private char decodeQuoteChar( int encoded ) {
		return ( ( encoded >> 2 ) & 0x1 ) == 1 ? '"' : '\'';
	}

	private int decodeDepth( int encoded ) {
		return ( encoded >> 3 ) & 0x1F;
	}

	@Override
	public IElementType getTokenType() {
		return tokenType;
	}

	@Override
	public int getTokenStart() {
		return tokenStart;
	}

	@Override
	public int getTokenEnd() {
		return tokenEnd;
	}

	@Override
	@NotNull
	public CharSequence getBufferSequence() {
		return buffer;
	}

	@Override
	public int getBufferEnd() {
		return bufferEnd;
	}

	@Override
	public void advance() {
		if ( position >= bufferEnd ) {
			tokenType	= null;
			tokenStart	= bufferEnd;
			tokenEnd	= bufferEnd;
			return;
		}

		tokenStart = position;

		// Handle different states
		switch ( state ) {
			case STATE_IN_SINGLE_STRING :
			case STATE_IN_DOUBLE_STRING :
				advanceInString();
				break;
			case STATE_IN_INTERPOLATION :
				advanceInInterpolation();
				break;
			default :
				advanceNormal();
		}

		// Track last non-whitespace token type for context-sensitive classification
		if ( tokenType != null && tokenType != TokenType.WHITE_SPACE ) {
			lastNonWsTokenType = tokenType;
		}
	}

	private void advanceNormal() {
		char current = buffer.charAt( position );

		if ( Character.isWhitespace( current ) ) {
			position	= consumeWhile( position, Character::isWhitespace );
			tokenType	= TokenType.WHITE_SPACE;
			tokenEnd	= position;
			return;
		}

		if ( match( position, "//" ) ) {
			position				= consumeUntil( position + 2, value -> value == '\n' || value == '\r' );
			tokenType				= BoxLangTokenTypes.LINE_COMMENT;
			tokenEnd				= position;
			lastWasFunctionKeyword	= false;
			return;
		}

		// Doc comment: /** ... */
		if ( match( position, "/**" ) && ( position + 3 >= bufferEnd || buffer.charAt( position + 3 ) != '/' ) ) {
			position				= consumeBlock( position + 3, "*/" );
			tokenType				= BoxLangTokenTypes.DOC_COMMENT;
			tokenEnd				= position;
			lastWasFunctionKeyword	= false;
			return;
		}

		if ( match( position, "/*" ) ) {
			position				= consumeBlock( position + 2, "*/" );
			tokenType				= BoxLangTokenTypes.BLOCK_COMMENT;
			tokenEnd				= position;
			lastWasFunctionKeyword	= false;
			return;
		}

		if ( match( position, "<!---" ) ) {
			position				= consumeBlock( position + 5, "--->" );
			tokenType				= BoxLangTokenTypes.BLOCK_COMMENT;
			tokenEnd				= position;
			lastWasFunctionKeyword	= false;
			return;
		}

		if ( matchTagStart( position ) ) {
			position				= consumeTag( position + 1 );
			tokenType				= BoxLangTokenTypes.TAG;
			tokenEnd				= position;
			lastWasFunctionKeyword	= false;
			return;
		}

		if ( current == '\'' || current == '"' ) {
			// Start of a string - emit the quote and switch state
			stringQuoteChar	= current;
			state			= current == '"' ? STATE_IN_DOUBLE_STRING : STATE_IN_SINGLE_STRING;
			position++;
			tokenType				= BoxLangTokenTypes.STRING;
			tokenEnd				= position;
			lastWasFunctionKeyword	= false;
			return;
		}

		// Hex number: 0x...
		if ( current == '0' && position + 1 < bufferEnd
		    && ( buffer.charAt( position + 1 ) == 'x' || buffer.charAt( position + 1 ) == 'X' ) ) {
			position				= consumeHexNumber( position );
			tokenType				= BoxLangTokenTypes.NUMBER;
			tokenEnd				= position;
			lastWasFunctionKeyword	= false;
			return;
		}

		if ( Character.isDigit( current ) ) {
			position				= consumeNumber( position );
			tokenType				= BoxLangTokenTypes.NUMBER;
			tokenEnd				= position;
			lastWasFunctionKeyword	= false;
			return;
		}

		// Annotation: @word
		if ( current == '@' && position + 1 < bufferEnd && isIdentifierStart( buffer.charAt( position + 1 ) ) ) {
			position = consumeWhile( position + 1, this::isIdentifierPart );
			// Also allow dotted annotations like @foo.bar
			while ( position < bufferEnd && buffer.charAt( position ) == '.'
			    && position + 1 < bufferEnd && isIdentifierPart( buffer.charAt( position + 1 ) ) ) {
				position = consumeWhile( position + 1, this::isIdentifierPart );
			}
			tokenType				= BoxLangTokenTypes.ANNOTATION;
			tokenEnd				= position;
			lastWasFunctionKeyword	= false;
			return;
		}

		if ( isIdentifierStart( current ) ) {
			position = consumeWhile( position + 1, this::isIdentifierPart );
			String	text				= buffer.subSequence( tokenStart, position ).toString();
			String	lowerText			= text.toLowerCase( Locale.ROOT );

			// Check if the next non-whitespace char is '(' for BIF/function-name detection
			boolean	followedByParen		= isFollowedByParen( position );
			boolean	followedByColon		= isFollowedByColon( position );
			boolean	afterDot			= lastNonWsTokenType == BoxLangTokenTypes.DOT;
			boolean	followedByEquals	= isFollowedByEquals( position );

			// Classify the identifier
			// First check classified sets (storage types, modifiers, keywords, etc.)
			// Then fall back to function-name detection for unclassified identifiers after "function" keyword
			if ( CONSTANTS.contains( lowerText ) ) {
				tokenType				= BoxLangTokenTypes.CONSTANT;
				lastWasFunctionKeyword	= false;
			} else if ( !afterDot && SCOPE_VARIABLES.contains( lowerText ) ) {
				// Scope variables only at start of identifier chain (not after a dot)
				tokenType				= BoxLangTokenTypes.SCOPE_VARIABLE;
				lastWasFunctionKeyword	= false;
			} else if ( STORAGE_TYPES.contains( lowerText ) ) {
				tokenType				= BoxLangTokenTypes.STORAGE_TYPE;
				// Track "function" keyword for function-name detection
				lastWasFunctionKeyword	= "function".equals( lowerText );
			} else if ( STORAGE_MODIFIERS.contains( lowerText ) ) {
				tokenType = BoxLangTokenTypes.STORAGE_MODIFIER;
				// Don't reset lastWasFunctionKeyword — modifiers can precede function names
				// but the flag is set by "function" keyword, not modifiers
			} else if ( lastWasFunctionKeyword ) {
				// This is a function name in a declaration: function <name>
				// (may or may not be followed by parens)
				tokenType				= BoxLangTokenTypes.FUNCTION_NAME;
				lastWasFunctionKeyword	= false;
			} else if ( followedByParen && BUILTIN_FUNCTIONS.contains( lowerText ) ) {
				tokenType				= BoxLangTokenTypes.BUILTIN_FUNCTION;
				lastWasFunctionKeyword	= false;
			} else if ( KEYWORDS.contains( lowerText ) ) {
				tokenType				= BoxLangTokenTypes.KEYWORD;
				lastWasFunctionKeyword	= false;
			} else if ( WORD_OPERATORS.contains( lowerText ) ) {
				tokenType				= BoxLangTokenTypes.OPERATOR;
				lastWasFunctionKeyword	= false;
			} else if ( followedByColon ) {
				// Struct/map key: identifier followed by : (but not :: or :=)
				tokenType				= BoxLangTokenTypes.STRUCT_KEY;
				lastWasFunctionKeyword	= false;
			} else if ( followedByParen ) {
				// Any identifier followed by ( that isn't a BIF or keyword — function call
				tokenType				= BoxLangTokenTypes.FUNCTION_CALL;
				lastWasFunctionKeyword	= false;
			} else if ( parenDepth > 0 && followedByEquals ) {
				// Inside parentheses, identifier followed by = is a named argument
				tokenType				= BoxLangTokenTypes.NAMED_ARGUMENT;
				lastWasFunctionKeyword	= false;
			} else {
				tokenType				= BoxLangTokenTypes.IDENTIFIER;
				lastWasFunctionKeyword	= false;
			}
			tokenEnd = position;
			return;
		}

		if ( current == '{' || current == '}' ) {
			position++;
			tokenType = BoxLangTokenTypes.BRACE;
		} else if ( current == '(' || current == ')' ) {
			if ( current == '(' ) {
				parenDepth++;
			} else if ( parenDepth > 0 ) {
				parenDepth--;
			}
			position++;
			tokenType = BoxLangTokenTypes.PAREN;
		} else if ( current == '[' || current == ']' ) {
			position++;
			tokenType = BoxLangTokenTypes.BRACKET;
		} else if ( current == ',' ) {
			position++;
			tokenType = BoxLangTokenTypes.COMMA;
		} else if ( current == '.' ) {
			// Check for range operator ..
			if ( position + 1 < bufferEnd && buffer.charAt( position + 1 ) == '.' ) {
				position	+= 2;
				tokenType	= BoxLangTokenTypes.OPERATOR;
			} else {
				position++;
				tokenType = BoxLangTokenTypes.DOT;
			}
		} else if ( current == ';' ) {
			position++;
			tokenType = BoxLangTokenTypes.SEMICOLON;
		} else if ( current == '#' ) {
			// Hash sign outside of string - just treat as operator or bad char
			position++;
			tokenType = BoxLangTokenTypes.HASH_SIGN;
		} else if ( isOperatorStart( current ) ) {
			position	= consumeOperator( position );
			tokenType	= BoxLangTokenTypes.OPERATOR;
		} else {
			position++;
			tokenType = BoxLangTokenTypes.BAD_CHARACTER;
		}

		lastWasFunctionKeyword	= false;
		tokenEnd				= position;
	}

	private void advanceInString() {
		char	quoteChar	= state == STATE_IN_DOUBLE_STRING ? '"' : '\'';
		char	current		= buffer.charAt( position );

		// Check for end of string
		if ( current == quoteChar ) {
			position++;
			tokenType	= BoxLangTokenTypes.STRING;
			tokenEnd	= position;
			state		= STATE_NORMAL;
			return;
		}

		// Check for escaped hash (##)
		if ( current == '#' && position + 1 < bufferEnd && buffer.charAt( position + 1 ) == '#' ) {
			// Escaped hash - emit as string content
			position	+= 2;
			tokenType	= BoxLangTokenTypes.STRING;
			tokenEnd	= position;
			return;
		}

		// Check for interpolation start
		if ( current == '#' ) {
			// Emit the hash sign and switch to interpolation mode
			position++;
			tokenType			= BoxLangTokenTypes.HASH_SIGN;
			tokenEnd			= position;
			state				= STATE_IN_INTERPOLATION;
			interpolationDepth	= 0;
			return;
		}

		// Consume string content until we hit quote, #, or newline
		int index = position;
		while ( index < bufferEnd ) {
			char ch = buffer.charAt( index );
			if ( ch == '\\' && index + 1 < bufferEnd ) {
				index += 2;
				continue;
			}
			if ( ch == quoteChar || ch == '#' || ch == '\n' || ch == '\r' ) {
				break;
			}
			index++;
		}

		if ( index == position ) {
			// Newline or something unexpected - consume one char
			position++;
		} else {
			position = index;
		}
		tokenType	= BoxLangTokenTypes.STRING;
		tokenEnd	= position;
	}

	private void advanceInInterpolation() {
		char current = buffer.charAt( position );

		if ( Character.isWhitespace( current ) ) {
			position	= consumeWhile( position, Character::isWhitespace );
			tokenType	= TokenType.WHITE_SPACE;
			tokenEnd	= position;
			return;
		}

		// Check for closing hash (end of interpolation)
		if ( current == '#' && interpolationDepth == 0 ) {
			position++;
			tokenType	= BoxLangTokenTypes.HASH_SIGN;
			tokenEnd	= position;
			state		= stringQuoteChar == '"' ? STATE_IN_DOUBLE_STRING : STATE_IN_SINGLE_STRING;
			return;
		}

		// Track nested braces/parens for complex expressions
		if ( current == '(' || current == '[' || current == '{' ) {
			interpolationDepth++;
			position++;
			if ( current == '(' ) {
				tokenType = BoxLangTokenTypes.PAREN;
			} else if ( current == '[' ) {
				tokenType = BoxLangTokenTypes.BRACKET;
			} else {
				tokenType = BoxLangTokenTypes.BRACE;
			}
			tokenEnd = position;
			return;
		}

		if ( current == ')' || current == ']' || current == '}' ) {
			if ( interpolationDepth > 0 ) {
				interpolationDepth--;
			}
			position++;
			if ( current == ')' ) {
				tokenType = BoxLangTokenTypes.PAREN;
			} else if ( current == ']' ) {
				tokenType = BoxLangTokenTypes.BRACKET;
			} else {
				tokenType = BoxLangTokenTypes.BRACE;
			}
			tokenEnd = position;
			return;
		}

		// Handle nested strings within interpolation
		if ( current == '\'' || current == '"' ) {
			position	= consumeNestedString( position, current );
			tokenType	= BoxLangTokenTypes.STRING;
			tokenEnd	= position;
			return;
		}

		// Handle numbers
		if ( Character.isDigit( current ) ) {
			position	= consumeNumber( position );
			tokenType	= BoxLangTokenTypes.NUMBER;
			tokenEnd	= position;
			return;
		}

		// Handle identifiers and keywords
		if ( isIdentifierStart( current ) ) {
			position = consumeWhile( position + 1, this::isIdentifierPart );
			String	text			= buffer.subSequence( tokenStart, position ).toString();
			String	lowerText		= text.toLowerCase( Locale.ROOT );

			boolean	followedByParen	= isFollowedByParen( position );

			if ( CONSTANTS.contains( lowerText ) ) {
				tokenType = BoxLangTokenTypes.CONSTANT;
			} else if ( SCOPE_VARIABLES.contains( lowerText ) ) {
				tokenType = BoxLangTokenTypes.SCOPE_VARIABLE;
			} else if ( followedByParen && BUILTIN_FUNCTIONS.contains( lowerText ) ) {
				tokenType = BoxLangTokenTypes.BUILTIN_FUNCTION;
			} else if ( KEYWORDS.contains( lowerText ) || STORAGE_TYPES.contains( lowerText )
			    || STORAGE_MODIFIERS.contains( lowerText ) ) {
				tokenType = BoxLangTokenTypes.KEYWORD;
			} else if ( WORD_OPERATORS.contains( lowerText ) ) {
				tokenType = BoxLangTokenTypes.OPERATOR;
			} else {
				tokenType = BoxLangTokenTypes.IDENTIFIER;
			}
			tokenEnd = position;
			return;
		}

		// Handle operators and other symbols
		if ( current == ',' ) {
			position++;
			tokenType = BoxLangTokenTypes.COMMA;
		} else if ( current == '.' ) {
			if ( position + 1 < bufferEnd && buffer.charAt( position + 1 ) == '.' ) {
				position	+= 2;
				tokenType	= BoxLangTokenTypes.OPERATOR;
			} else {
				position++;
				tokenType = BoxLangTokenTypes.DOT;
			}
		} else if ( current == ';' ) {
			position++;
			tokenType = BoxLangTokenTypes.SEMICOLON;
		} else if ( isOperatorStart( current ) ) {
			position	= consumeOperator( position );
			tokenType	= BoxLangTokenTypes.OPERATOR;
		} else {
			position++;
			tokenType = BoxLangTokenTypes.BAD_CHARACTER;
		}

		tokenEnd = position;
	}

	private int consumeNestedString( int start, char quote ) {
		int index = start + 1;
		while ( index < bufferEnd ) {
			char current = buffer.charAt( index );
			if ( current == '\\' && index + 1 < bufferEnd ) {
				index += 2;
				continue;
			}
			if ( current == quote ) {
				return index + 1;
			}
			if ( current == '\n' || current == '\r' ) {
				return index;
			}
			// For nested strings, we don't process interpolation - just consume the whole string
			index++;
		}
		return bufferEnd;
	}

	private int consumeWhile( int start, java.util.function.Predicate<Character> predicate ) {
		int index = start;
		while ( index < bufferEnd && predicate.test( buffer.charAt( index ) ) ) {
			index++;
		}
		return index;
	}

	private int consumeUntil( int start, java.util.function.Predicate<Character> predicate ) {
		int index = start;
		while ( index < bufferEnd && !predicate.test( buffer.charAt( index ) ) ) {
			index++;
		}
		return index;
	}

	private int consumeBlock( int start, String terminator ) {
		int index = start;
		while ( index < bufferEnd ) {
			if ( match( index, terminator ) ) {
				return Math.min( index + terminator.length(), bufferEnd );
			}
			index++;
		}
		return bufferEnd;
	}

	private int consumeNumber( int start ) {
		int		index	= start;
		boolean	seenDot	= false;
		while ( index < bufferEnd ) {
			char current = buffer.charAt( index );
			if ( current == '.' && !seenDot ) {
				// Check that this is a decimal point, not a range operator (..)
				if ( index + 1 < bufferEnd && buffer.charAt( index + 1 ) == '.' ) {
					break;
				}
				seenDot = true;
				index++;
				continue;
			}
			if ( current == '_' && index > start ) {
				// Allow underscores in numeric literals like 1_000_000
				index++;
				continue;
			}
			if ( ( current == 'e' || current == 'E' ) && index > start ) {
				// Scientific notation
				index++;
				if ( index < bufferEnd && ( buffer.charAt( index ) == '+' || buffer.charAt( index ) == '-' ) ) {
					index++;
				}
				continue;
			}
			if ( !Character.isDigit( current ) ) {
				break;
			}
			index++;
		}
		// Handle type suffixes: L, l, F, f, D, d, G, g
		if ( index < bufferEnd ) {
			char suffix = buffer.charAt( index );
			if ( suffix == 'L' || suffix == 'l' || suffix == 'F' || suffix == 'f'
			    || suffix == 'D' || suffix == 'd' || suffix == 'G' || suffix == 'g' ) {
				index++;
			}
		}
		return index;
	}

	private int consumeHexNumber( int start ) {
		int index = start + 2; // skip 0x
		while ( index < bufferEnd ) {
			char current = buffer.charAt( index );
			if ( !isHexDigit( current ) ) {
				break;
			}
			index++;
		}
		// Handle type suffixes
		if ( index < bufferEnd ) {
			char suffix = buffer.charAt( index );
			if ( suffix == 'L' || suffix == 'l' || suffix == 'F' || suffix == 'f'
			    || suffix == 'D' || suffix == 'd' ) {
				index++;
			}
		}
		return index;
	}

	private boolean isHexDigit( char ch ) {
		return ( ch >= '0' && ch <= '9' ) || ( ch >= 'a' && ch <= 'f' ) || ( ch >= 'A' && ch <= 'F' );
	}

	private int consumeOperator( int start ) {
		char first = buffer.charAt( start );

		// Check for three-character operators first
		if ( start + 2 < bufferEnd ) {
			String three = "" + first + buffer.charAt( start + 1 ) + buffer.charAt( start + 2 );
			if ( THREE_CHAR_OPERATORS.contains( three ) ) {
				return start + 3;
			}
		}

		// Check for two-character operators
		if ( start + 1 < bufferEnd ) {
			String two = "" + first + buffer.charAt( start + 1 );
			if ( TWO_CHAR_OPERATORS.contains( two ) ) {
				return start + 2;
			}
		}

		return start + 1;
	}

	private boolean isIdentifierStart( char ch ) {
		return ch == '_' || ch == '$' || Character.isLetter( ch );
	}

	private boolean isIdentifierPart( char ch ) {
		return isIdentifierStart( ch ) || Character.isDigit( ch );
	}

	private boolean isOperatorStart( char ch ) {
		return OPERATOR_STARTS.contains( ch );
	}

	private boolean isFollowedByParen( int pos ) {
		int index = pos;
		while ( index < bufferEnd && Character.isWhitespace( buffer.charAt( index ) ) ) {
			index++;
		}
		return index < bufferEnd && buffer.charAt( index ) == '(';
	}

	/**
	 * Check if the current position is followed by a single colon (not ::).
	 * Used to detect struct/map key patterns like { key: value }.
	 */
	private boolean isFollowedByColon( int pos ) {
		int index = pos;
		while ( index < bufferEnd && Character.isWhitespace( buffer.charAt( index ) ) ) {
			index++;
		}
		if ( index < bufferEnd && buffer.charAt( index ) == ':' ) {
			// Make sure it's not :: (scope resolution) or := (assignment)
			if ( index + 1 < bufferEnd ) {
				char next = buffer.charAt( index + 1 );
				return next != ':' && next != '=';
			}
			return true;
		}
		return false;
	}

	/**
	 * Check if the current position is followed by a single equals sign (not ==, =>, =~).
	 * Used to detect named argument patterns like foo(name = value).
	 */
	private boolean isFollowedByEquals( int pos ) {
		int index = pos;
		while ( index < bufferEnd && Character.isWhitespace( buffer.charAt( index ) ) ) {
			index++;
		}
		if ( index < bufferEnd && buffer.charAt( index ) == '=' ) {
			// Make sure it's not ==, =>, =~
			if ( index + 1 < bufferEnd ) {
				char next = buffer.charAt( index + 1 );
				return next != '=' && next != '>' && next != '~';
			}
			return true;
		}
		return false;
	}

	private boolean match( int offset, String text ) {
		if ( offset + text.length() > bufferEnd ) {
			return false;
		}
		for ( int i = 0; i < text.length(); i++ ) {
			if ( buffer.charAt( offset + i ) != text.charAt( i ) ) {
				return false;
			}
		}
		return true;
	}

	private boolean matchTagStart( int offset ) {
		if ( offset >= bufferEnd || buffer.charAt( offset ) != '<' ) {
			return false;
		}
		int index = offset + 1;
		if ( index < bufferEnd && buffer.charAt( index ) == '/' ) {
			index++;
		}
		return matchInsensitive( index, "bx:" );
	}

	private boolean matchInsensitive( int offset, String text ) {
		if ( offset + text.length() > bufferEnd ) {
			return false;
		}
		for ( int i = 0; i < text.length(); i++ ) {
			if ( Character.toLowerCase( buffer.charAt( offset + i ) ) != Character.toLowerCase( text.charAt( i ) ) ) {
				return false;
			}
		}
		return true;
	}

	private int consumeTag( int start ) {
		int index = start;
		while ( index < bufferEnd ) {
			char current = buffer.charAt( index );
			if ( current == '>' ) {
				return index + 1;
			}
			if ( current == '\n' || current == '\r' ) {
				return index;
			}
			index++;
		}
		return bufferEnd;
	}

	// ── Keyword / classification sets ──────────────────────────────────────────

	/**
	 * Control-flow and other keywords (highlighted as keyword.control / keyword.other in TMBundle).
	 * Storage types, storage modifiers, constants, scope variables, and word operators are broken out separately.
	 */
	private static final Set<String>	KEYWORDS				= new HashSet<>( Arrays.asList(
	    // Control flow — keyword.control.boxlang
	    "if", "else", "elseif", "switch", "case", "default", "for", "while", "do",
	    "break", "continue", "return", "try", "catch", "finally", "throw", "rethrow",
	    // keyword.other.boxlang
	    "import", "include", "as", "in", "instanceof", "castas",
	    "does", "contain", "contains", "than", "to", "when",
	    "assert", "param", "abort", "exit",
	    "lock", "thread", "transaction", "throws",
	    "new", "extends", "implements",
	    // Output helpers treated as keywords
	    "writedump", "echo", "println", "writeoutput"
	) );

	/**
	 * Storage type keywords — storage.type.boxlang in TMBundle
	 */
	private static final Set<String>	STORAGE_TYPES			= new HashSet<>( Arrays.asList(
	    "class", "component", "interface", "function", "property", "var",
	    // Primitive / hint types — storage.type.primitive.boxlang
	    "any", "array", "boolean", "numeric", "query", "string", "struct",
	    "date", "binary", "guid", "uuid", "closure", "lambda"
	) );

	/**
	 * Storage modifiers — storage.modifier.boxlang in TMBundle
	 */
	private static final Set<String>	STORAGE_MODIFIERS		= new HashSet<>( Arrays.asList(
	    "public", "private", "remote", "package", "static", "final", "abstract", "required", "protected"
	) );

	/**
	 * Language constants — constant.language.boxlang in TMBundle
	 */
	private static final Set<String>	CONSTANTS				= new HashSet<>( Arrays.asList(
	    "true", "false", "null", "yes", "no"
	) );

	/**
	 * Scoped variable prefixes — variable.language.scope.boxlang in TMBundle
	 */
	private static final Set<String>	SCOPE_VARIABLES			= new HashSet<>( Arrays.asList(
	    "variables", "request", "server", "application", "session",
	    "client", "form", "url", "cgi", "cookie", "this", "local", "let", "arguments"
	) );

	/**
	 * Word-based operators — keyword.operator.* in TMBundle. These are highlighted as OPERATOR rather than KEYWORD.
	 */
	private static final Set<String>	WORD_OPERATORS			= new HashSet<>( Arrays.asList(
	    // Logical
	    "and", "or", "not", "xor", "eqv", "imp",
	    // Comparison
	    "eq", "equal", "neq", "gt", "gte", "ge", "lt", "lte", "le", "is",
	    // Arithmetic
	    "mod"
	) );

	/**
	 * Three-character operators
	 */
	private static final Set<String>	THREE_CHAR_OPERATORS	= new HashSet<>( Arrays.asList(
	    "===", "<=>", "==~"
	) );

	/**
	 * Two-character operators
	 */
	private static final Set<String>	TWO_CHAR_OPERATORS		= new HashSet<>( Arrays.asList(
	    "==", "!=", ">=", "<=", "<>",
	    "&&", "||",
	    "++", "--",
	    "=>", "->", "::",
	    "+=", "-=", "*=", "/=", "%=", "&=",
	    "?:", "?.",
	    "=~", "*:"
	) );

	private static final Set<Character>	OPERATOR_STARTS			= new HashSet<>( Arrays.asList(
	    '+', '-', '*', '/', '%', '=', '<', '>', '!', '&', '|', '^', ':', '?', '~'
	) );

	/**
	 * Built-in functions (BIFs) — support.function.global.* in TMBundle.
	 * These are recognized only when followed by '(' to avoid false positives.
	 */
	private static final Set<String>	BUILTIN_FUNCTIONS		= new HashSet<>( Arrays.asList(
	    // String functions
	    "arrayappend", "arrayavg", "arrayclear", "arraycontains", "arraycontainsnocase",
	    "arraydelete", "arraydeleteat", "arraydeletenocase", "arrayeach", "arrayevery",
	    "arrayfilter", "arrayfind", "arrayfindall", "arrayfindallnocase", "arrayfindnocase",
	    "arrayfirst", "arrayinsertat", "arrayisdefined", "arrayisempty", "arraylast",
	    "arraylen", "arraymap", "arraymax", "arraymerge", "arraymin", "arraynew",
	    "arrayprepend", "arrayreduce", "arrayresize", "arrayreverse", "arrayset",
	    "arrayslice", "arraysort", "arraysum", "arrayswap", "arraytolist", "arrayunique",
	    "asc", "char", "compare", "comparenocase", "find", "findnocase", "findoneof",
	    "gettoken", "insert", "lcase", "left", "len", "listappend", "listchangedelims",
	    "listcontains", "listcontainsnocase", "listdeleteat", "listfind", "listfindnocase",
	    "listfirst", "listgetat", "listinsertat", "listlast", "listlen", "listprepend",
	    "listqualify", "listrest", "listsetat", "listsort", "listtoarray", "listvaluecount",
	    "listvaluecountnocase", "ljustify", "ltrim", "mid", "refind", "refindnocase",
	    "rematch", "rematchnocase", "rereplace", "rereplacenocase", "removechars",
	    "repeatstring", "replace", "replacelist", "replacenocase", "reverse", "right",
	    "rjustify", "rtrim", "spanexcluding", "spanincluding", "stripcr", "trim",
	    "ucase", "val", "wrap",
	    // Math functions
	    "abs", "acos", "asin", "atan", "atan2", "ceiling", "cos", "decrementvalue",
	    "exp", "fix", "formatbasen", "incrementvalue", "inputbasen", "int", "log",
	    "log10", "max", "min", "pi", "pow", "rand", "randomize", "randrange", "round",
	    "sgn", "sin", "sqr", "tan", "bitand", "bitmaskclear", "bitmaskread", "bitmaskset",
	    "bitnot", "bitor", "bitshln", "bitshrn", "bitxor", "decimalformat", "dollarformat",
	    "numberformat",
	    // Date/time functions
	    "createdate", "createdatetime", "createodbcdate", "createodbcdatetime",
	    "createodbctime", "createtime", "createtimespan", "dateadd", "datecompare",
	    "dateconvert", "datediff", "dateformat", "datepart", "day", "dayofweek",
	    "dayofyear", "daysinmonth", "daysinyear", "firstdayofmonth", "gethttptimestring",
	    "gettickcount", "gettimezoneinfo", "hour", "isdate", "isleapyear", "lsdateformat",
	    "lsparsedatetime", "lstimeformat", "minute", "month", "monthasstring", "now",
	    "parsedatetime", "quarter", "second", "timeformat", "week", "year",
	    // Decision functions
	    "iif", "isarray", "isbinary", "isboolean", "iscustomfunction", "isdefined",
	    "isempty", "isjson", "isnull", "isnumeric", "isobject", "isquery",
	    "issimplevalue", "isstruct", "isvalid", "isxml", "isxmlattribute", "isxmldoc",
	    "isxmlelem", "isxmlnode", "isxmlroot",
	    // Conversion functions
	    "binarydecode", "binaryencode", "charsetdecode", "charsetencode", "htmlcodeformat",
	    "htmleditformat", "jsstringformat", "paragraphformat", "urldecode",
	    "urlencodedformat", "xmlformat", "tobase64", "tostring", "tobinary",
	    // Struct functions
	    "duplicate", "structappend", "structclear", "structcopy", "structcount",
	    "structdelete", "structfind", "structfindkey", "structfindvalue", "structget",
	    "structinsert", "structisempty", "structkeyarray", "structkeyexists",
	    "structkeylist", "structnew", "structsort", "structupdate",
	    // Query functions
	    "queryaddcolumn", "queryaddrow", "querynew", "querysetcell", "valuelist",
	    // File functions
	    "directoryexists", "fileexists", "getdirectoryfrompath", "getfilefrompath",
	    "gettempdirectory", "gettempfile", "expandpath",
	    // System functions
	    "createobject", "getbasetemplatepath", "getcurrenttemplatepath", "getfunctionlist",
	    "getmetadata", "hash", "serialize", "setencoding", "setlocale", "systemexecute",
	    "writeoutput", "writedump", "evaluate", "invoke",
	    // Cache functions
	    "cacheclear", "cachedelete", "cacheget", "cachegetall", "cachegetmetadata",
	    "cacheidexists", "cacheput", "cacheremove", "cacheremoveall",
	    // Encryption functions
	    "decrypt", "encrypt", "generatesecretkey",
	    // XML/Zip functions
	    "xmlchildpos", "xmlelemnew", "xmlgetnodetype", "xmlnew", "xmlparse",
	    "xmlsearch", "xmltransform", "xmlvalidate", "zip", "unzip", "iszipfile"
	) );
}
