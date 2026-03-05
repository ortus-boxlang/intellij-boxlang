package com.ortussolutions.intellijboxlang.highlighting;

import com.intellij.lexer.Lexer;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.ortussolutions.intellijboxlang.lexer.BoxLangLexer;
import com.ortussolutions.intellijboxlang.lexer.BoxLangTokenTypes;
import java.util.ArrayList;
import java.util.List;

public final class BoxLangLexerHighlightingTest extends BasePlatformTestCase {

	public void testScriptLexerTokens() {
		List<IElementType> tokens = lexTokens( "component demo { function main() { return 1; } }" );
		assertTokenSequence( tokens,
		    BoxLangTokenTypes.KEYWORD,
		    BoxLangTokenTypes.IDENTIFIER,
		    BoxLangTokenTypes.BRACE,
		    BoxLangTokenTypes.KEYWORD,
		    BoxLangTokenTypes.IDENTIFIER,
		    BoxLangTokenTypes.PAREN,
		    BoxLangTokenTypes.PAREN,
		    BoxLangTokenTypes.BRACE,
		    BoxLangTokenTypes.KEYWORD,
		    BoxLangTokenTypes.NUMBER,
		    BoxLangTokenTypes.SEMICOLON,
		    BoxLangTokenTypes.BRACE,
		    BoxLangTokenTypes.BRACE
		);
	}

	public void testTemplateLexerTokens() {
		List<IElementType> tokens = lexTokens( "<bx:output>Hi</bx:output>" );
		assertTokenSequence( tokens,
		    BoxLangTokenTypes.TAG,
		    BoxLangTokenTypes.IDENTIFIER,
		    BoxLangTokenTypes.TAG
		);
	}

	public void testStringInterpolationTokens() {
		// Test basic interpolation: "Hello, #name#!"
		List<IElementType> tokens = lexTokens( "\"Hello, #name#!\"" );
		assertTokenSequence( tokens,
		    BoxLangTokenTypes.STRING, // Opening quote "
		    BoxLangTokenTypes.STRING, // "Hello, "
		    BoxLangTokenTypes.HASH_SIGN, // #
		    BoxLangTokenTypes.IDENTIFIER, // name
		    BoxLangTokenTypes.HASH_SIGN, // #
		    BoxLangTokenTypes.STRING, // "!"
		    BoxLangTokenTypes.STRING // Closing quote "
		);
	}

	public void testStringInterpolationWithExpression() {
		// Test interpolation with expression: "Result: #foo + bar#"
		List<IElementType> tokens = lexTokens( "\"Result: #foo + bar#\"" );
		assertTokenSequence( tokens,
		    BoxLangTokenTypes.STRING, // Opening quote "
		    BoxLangTokenTypes.STRING, // "Result: "
		    BoxLangTokenTypes.HASH_SIGN, // #
		    BoxLangTokenTypes.IDENTIFIER, // foo
		    BoxLangTokenTypes.OPERATOR, // +
		    BoxLangTokenTypes.IDENTIFIER, // bar
		    BoxLangTokenTypes.HASH_SIGN, // #
		    BoxLangTokenTypes.STRING // Closing quote "
		);
	}

	public void testEscapedHashInString() {
		// Test escaped hash: "Price: ##50"
		List<IElementType> tokens = lexTokens( "\"Price: ##50\"" );
		assertTokenSequence( tokens,
		    BoxLangTokenTypes.STRING, // Opening quote "
		    BoxLangTokenTypes.STRING, // "Price: "
		    BoxLangTokenTypes.STRING, // ## (escaped hash)
		    BoxLangTokenTypes.STRING, // "50"
		    BoxLangTokenTypes.STRING // Closing quote "
		);
	}

	public void testSingleQuoteStringInterpolation() {
		// Test single quote string with interpolation: 'Hello, #name#!'
		List<IElementType> tokens = lexTokens( "'Hello, #name#!'" );
		assertTokenSequence( tokens,
		    BoxLangTokenTypes.STRING, // Opening quote '
		    BoxLangTokenTypes.STRING, // 'Hello, '
		    BoxLangTokenTypes.HASH_SIGN, // #
		    BoxLangTokenTypes.IDENTIFIER, // name
		    BoxLangTokenTypes.HASH_SIGN, // #
		    BoxLangTokenTypes.STRING, // '!'
		    BoxLangTokenTypes.STRING // Closing quote '
		);
	}

	public void testNestedFunctionCallInInterpolation() {
		// Test function call in interpolation: "Count: #len(items)#"
		List<IElementType> tokens = lexTokens( "\"Count: #len(items)#\"" );
		assertTokenSequence( tokens,
		    BoxLangTokenTypes.STRING, // Opening quote "
		    BoxLangTokenTypes.STRING, // "Count: "
		    BoxLangTokenTypes.HASH_SIGN, // #
		    BoxLangTokenTypes.IDENTIFIER, // len
		    BoxLangTokenTypes.PAREN, // (
		    BoxLangTokenTypes.IDENTIFIER, // items
		    BoxLangTokenTypes.PAREN, // )
		    BoxLangTokenTypes.HASH_SIGN, // #
		    BoxLangTokenTypes.STRING // Closing quote "
		);
	}

	private List<IElementType> lexTokens( String text ) {
		Lexer lexer = new BoxLangLexer();
		lexer.start( text );
		List<IElementType> tokens = new ArrayList<>();
		while ( lexer.getTokenType() != null ) {
			IElementType tokenType = lexer.getTokenType();
			if ( tokenType != TokenType.WHITE_SPACE ) {
				tokens.add( tokenType );
			}
			lexer.advance();
		}
		return tokens;
	}

	private void assertTokenSequence( List<IElementType> tokens, IElementType... expected ) {
		assertEquals( "Token count mismatch", expected.length, tokens.size() );
		for ( int i = 0; i < expected.length; i++ ) {
			assertEquals( "Token mismatch at index " + i, expected[ i ], tokens.get( i ) );
		}
	}
}
