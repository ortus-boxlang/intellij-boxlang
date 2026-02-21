package com.ortussolutions.intellijboxlang.highlighting;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import com.ortussolutions.intellijboxlang.lexer.BoxLangLexer;
import com.ortussolutions.intellijboxlang.lexer.BoxLangTokenTypes;
import org.jetbrains.annotations.NotNull;

public final class BoxLangSyntaxHighlighter extends SyntaxHighlighterBase {

	@Override
	@NotNull
	public Lexer getHighlightingLexer() {
		return new BoxLangLexer();
	}

	@Override
	@NotNull
	public TextAttributesKey[] getTokenHighlights( IElementType tokenType ) {
		TextAttributesKey key;
		if ( tokenType == BoxLangTokenTypes.KEYWORD ) {
			key = BoxLangTextAttributes.KEYWORD;
		} else if ( tokenType == BoxLangTokenTypes.IDENTIFIER ) {
			key = BoxLangTextAttributes.IDENTIFIER;
		} else if ( tokenType == BoxLangTokenTypes.NUMBER ) {
			key = BoxLangTextAttributes.NUMBER;
		} else if ( tokenType == BoxLangTokenTypes.STRING ) {
			key = BoxLangTextAttributes.STRING;
		} else if ( tokenType == BoxLangTokenTypes.LINE_COMMENT ) {
			key = BoxLangTextAttributes.LINE_COMMENT;
		} else if ( tokenType == BoxLangTokenTypes.BLOCK_COMMENT ) {
			key = BoxLangTextAttributes.BLOCK_COMMENT;
		} else if ( tokenType == BoxLangTokenTypes.OPERATOR ) {
			key = BoxLangTextAttributes.OPERATOR;
		} else if ( tokenType == BoxLangTokenTypes.TAG ) {
			key = BoxLangTextAttributes.TAG;
		} else if ( tokenType == BoxLangTokenTypes.BRACE ) {
			key = BoxLangTextAttributes.BRACE;
		} else if ( tokenType == BoxLangTokenTypes.PAREN ) {
			key = BoxLangTextAttributes.PAREN;
		} else if ( tokenType == BoxLangTokenTypes.BRACKET ) {
			key = BoxLangTextAttributes.BRACKET;
		} else if ( tokenType == BoxLangTokenTypes.COMMA ) {
			key = BoxLangTextAttributes.COMMA;
		} else if ( tokenType == BoxLangTokenTypes.DOT ) {
			key = BoxLangTextAttributes.DOT;
		} else if ( tokenType == BoxLangTokenTypes.SEMICOLON ) {
			key = BoxLangTextAttributes.SEMICOLON;
		} else if ( tokenType == BoxLangTokenTypes.BAD_CHARACTER ) {
			key = BoxLangTextAttributes.BAD_CHARACTER;
		} else {
			key = null;
		}

		return key == null ? TextAttributesKey.EMPTY_ARRAY : new TextAttributesKey[] { key };
	}
}
