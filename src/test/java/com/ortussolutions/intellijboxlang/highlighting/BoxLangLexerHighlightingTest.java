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
        List<IElementType> tokens = lexTokens("component demo { function main() { return 1; } }");
        assertTokenSequence(tokens,
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
        List<IElementType> tokens = lexTokens("<bx:output>Hi</bx:output>");
        assertTokenSequence(tokens,
            BoxLangTokenTypes.TAG,
            BoxLangTokenTypes.IDENTIFIER,
            BoxLangTokenTypes.TAG
        );
    }

    private List<IElementType> lexTokens(String text) {
        Lexer lexer = new BoxLangLexer();
        lexer.start(text);
        List<IElementType> tokens = new ArrayList<>();
        while (lexer.getTokenType() != null) {
            IElementType tokenType = lexer.getTokenType();
            if (tokenType != TokenType.WHITE_SPACE) {
                tokens.add(tokenType);
            }
            lexer.advance();
        }
        return tokens;
    }

    private void assertTokenSequence(List<IElementType> tokens, IElementType... expected) {
        assertEquals("Token count mismatch", expected.length, tokens.size());
        for (int i = 0; i < expected.length; i++) {
            assertEquals("Token mismatch at index " + i, expected[i], tokens.get(i));
        }
    }
}
