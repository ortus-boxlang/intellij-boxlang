package com.ortussolutions.intellijboxlang.highlighting;

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;

public final class BoxLangTextAttributes {

	public static final TextAttributesKey	KEYWORD			= TextAttributesKey.createTextAttributesKey(
	    "BOXLANG_KEYWORD",
	    DefaultLanguageHighlighterColors.KEYWORD
	);
	public static final TextAttributesKey	IDENTIFIER		= TextAttributesKey.createTextAttributesKey(
	    "BOXLANG_IDENTIFIER",
	    DefaultLanguageHighlighterColors.IDENTIFIER
	);
	public static final TextAttributesKey	NUMBER			= TextAttributesKey.createTextAttributesKey(
	    "BOXLANG_NUMBER",
	    DefaultLanguageHighlighterColors.NUMBER
	);
	public static final TextAttributesKey	STRING			= TextAttributesKey.createTextAttributesKey(
	    "BOXLANG_STRING",
	    DefaultLanguageHighlighterColors.STRING
	);
	public static final TextAttributesKey	HASH_SIGN		= TextAttributesKey.createTextAttributesKey(
	    "BOXLANG_HASH_SIGN",
	    DefaultLanguageHighlighterColors.KEYWORD
	);
	public static final TextAttributesKey	LINE_COMMENT	= TextAttributesKey.createTextAttributesKey(
	    "BOXLANG_LINE_COMMENT",
	    DefaultLanguageHighlighterColors.LINE_COMMENT
	);
	public static final TextAttributesKey	BLOCK_COMMENT	= TextAttributesKey.createTextAttributesKey(
	    "BOXLANG_BLOCK_COMMENT",
	    DefaultLanguageHighlighterColors.BLOCK_COMMENT
	);
	public static final TextAttributesKey	OPERATOR		= TextAttributesKey.createTextAttributesKey(
	    "BOXLANG_OPERATOR",
	    DefaultLanguageHighlighterColors.OPERATION_SIGN
	);
	public static final TextAttributesKey	TAG				= TextAttributesKey.createTextAttributesKey(
	    "BOXLANG_TAG",
	    resolveHtmlTagKey()
	);
	public static final TextAttributesKey	BRACE			= TextAttributesKey.createTextAttributesKey(
	    "BOXLANG_BRACE",
	    DefaultLanguageHighlighterColors.BRACES
	);
	public static final TextAttributesKey	PAREN			= TextAttributesKey.createTextAttributesKey(
	    "BOXLANG_PAREN",
	    DefaultLanguageHighlighterColors.PARENTHESES
	);
	public static final TextAttributesKey	BRACKET			= TextAttributesKey.createTextAttributesKey(
	    "BOXLANG_BRACKET",
	    DefaultLanguageHighlighterColors.BRACKETS
	);
	public static final TextAttributesKey	COMMA			= TextAttributesKey.createTextAttributesKey(
	    "BOXLANG_COMMA",
	    DefaultLanguageHighlighterColors.COMMA
	);
	public static final TextAttributesKey	DOT				= TextAttributesKey.createTextAttributesKey(
	    "BOXLANG_DOT",
	    DefaultLanguageHighlighterColors.DOT
	);
	public static final TextAttributesKey	SEMICOLON		= TextAttributesKey.createTextAttributesKey(
	    "BOXLANG_SEMICOLON",
	    DefaultLanguageHighlighterColors.SEMICOLON
	);
	public static final TextAttributesKey	BAD_CHARACTER	= TextAttributesKey.createTextAttributesKey(
	    "BOXLANG_BAD_CHARACTER",
	    HighlighterColors.BAD_CHARACTER
	);

	private static TextAttributesKey resolveHtmlTagKey() {
		TextAttributesKey key = TextAttributesKey.find( "HTML_TAG_NAME" );
		if ( key == null ) {
			key = TextAttributesKey.find( "HTML_TAG" );
		}
		if ( key == null ) {
			key = TextAttributesKey.find( "XML_TAG_NAME" );
		}
		return key != null ? key : DefaultLanguageHighlighterColors.MARKUP_TAG;
	}

	private BoxLangTextAttributes() {
	}
}
