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

	private CharSequence	buffer		= "";
	private int				bufferEnd	= 0;
	private int				tokenStart	= 0;
	private int				tokenEnd	= 0;
	private IElementType	tokenType;
	private int				position	= 0;

	@Override
	public void start( @NotNull CharSequence buffer, int startOffset, int endOffset, int initialState ) {
		this.buffer	= buffer;
		bufferEnd	= endOffset;
		position	= startOffset;
		tokenStart	= startOffset;
		tokenEnd	= startOffset;
		tokenType	= null;
		advance();
	}

	@Override
	public int getState() {
		return 0;
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
		char current = buffer.charAt( position );

		if ( Character.isWhitespace( current ) ) {
			position	= consumeWhile( position, Character::isWhitespace );
			tokenType	= TokenType.WHITE_SPACE;
			tokenEnd	= position;
			return;
		}

		if ( match( position, "//" ) ) {
			position	= consumeUntil( position + 2, value -> value == '\n' || value == '\r' );
			tokenType	= BoxLangTokenTypes.LINE_COMMENT;
			tokenEnd	= position;
			return;
		}

		if ( match( position, "/*" ) ) {
			position	= consumeBlock( position + 2, "*/" );
			tokenType	= BoxLangTokenTypes.BLOCK_COMMENT;
			tokenEnd	= position;
			return;
		}

		if ( match( position, "<!---" ) ) {
			position	= consumeBlock( position + 5, "--->" );
			tokenType	= BoxLangTokenTypes.BLOCK_COMMENT;
			tokenEnd	= position;
			return;
		}

		if ( matchTagStart( position ) ) {
			position	= consumeTag( position + 1 );
			tokenType	= BoxLangTokenTypes.TAG;
			tokenEnd	= position;
			return;
		}

		if ( current == '\'' || current == '"' ) {
			position	= consumeString( position, current );
			tokenType	= BoxLangTokenTypes.STRING;
			tokenEnd	= position;
			return;
		}

		if ( Character.isDigit( current ) ) {
			position	= consumeNumber( position );
			tokenType	= BoxLangTokenTypes.NUMBER;
			tokenEnd	= position;
			return;
		}

		if ( isIdentifierStart( current ) ) {
			position = consumeWhile( position + 1, this::isIdentifierPart );
			String text = buffer.subSequence( tokenStart, position ).toString();
			tokenType	= KEYWORDS.contains( text.toLowerCase( Locale.ROOT ) )
			    ? BoxLangTokenTypes.KEYWORD
			    : BoxLangTokenTypes.IDENTIFIER;
			tokenEnd	= position;
			return;
		}

		if ( current == '{' || current == '}' ) {
			position++;
			tokenType = BoxLangTokenTypes.BRACE;
		} else if ( current == '(' || current == ')' ) {
			position++;
			tokenType = BoxLangTokenTypes.PAREN;
		} else if ( current == '[' || current == ']' ) {
			position++;
			tokenType = BoxLangTokenTypes.BRACKET;
		} else if ( current == ',' ) {
			position++;
			tokenType = BoxLangTokenTypes.COMMA;
		} else if ( current == '.' ) {
			position++;
			tokenType = BoxLangTokenTypes.DOT;
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

	private int consumeString( int start, char quote ) {
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
				seenDot = true;
				index++;
				continue;
			}
			if ( !Character.isDigit( current ) ) {
				break;
			}
			index++;
		}
		return index;
	}

	private int consumeOperator( int start ) {
		int index = start + 1;
		if ( index < bufferEnd ) {
			String candidate = "" + buffer.charAt( start ) + buffer.charAt( index );
			if ( OPERATORS.contains( candidate ) ) {
				return index + 1;
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
		return match( index, "bx:" );
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

	private static final Set<String>	KEYWORDS		= new HashSet<>( Arrays.asList(
	    "abstract",
	    "and",
	    "break",
	    "case",
	    "catch",
	    "class",
	    "component",
	    "continue",
	    "default",
	    "do",
	    "else",
	    "extends",
	    "false",
	    "finally",
	    "for",
	    "function",
	    "if",
	    "implements",
	    "import",
	    "interface",
	    "let",
	    "new",
	    "null",
	    "or",
	    "property",
	    "private",
	    "protected",
	    "public",
	    "return",
	    "static",
	    "switch",
	    "this",
	    "throw",
	    "true",
	    "try",
	    "var",
	    "while"
	) );

	private static final Set<String>	OPERATORS		= new HashSet<>( Arrays.asList(
	    "==",
	    "!=",
	    ">=",
	    "<=",
	    "&&",
	    "||",
	    "++",
	    "--",
	    "=>",
	    "->",
	    "::"
	) );

	private static final Set<Character>	OPERATOR_STARTS	= new HashSet<>( Arrays.asList(
	    '+',
	    '-',
	    '*',
	    '/',
	    '%',
	    '=',
	    '<',
	    '>',
	    '!',
	    '&',
	    '|',
	    '^',
	    ':',
	    '?'
	) );
}
