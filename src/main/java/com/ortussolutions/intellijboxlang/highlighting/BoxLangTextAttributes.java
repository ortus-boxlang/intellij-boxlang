package com.ortussolutions.intellijboxlang.highlighting;

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;

public final class BoxLangTextAttributes {

	public static final TextAttributesKey	KEYWORD				= TextAttributesKey.createTextAttributesKey(
	    "BOXLANG_KEYWORD",
	    DefaultLanguageHighlighterColors.KEYWORD
	);
	public static final TextAttributesKey	IDENTIFIER			= TextAttributesKey.createTextAttributesKey(
	    "BOXLANG_IDENTIFIER",
	    DefaultLanguageHighlighterColors.IDENTIFIER
	);
	public static final TextAttributesKey	NUMBER				= TextAttributesKey.createTextAttributesKey(
	    "BOXLANG_NUMBER",
	    DefaultLanguageHighlighterColors.NUMBER
	);
	public static final TextAttributesKey	STRING				= TextAttributesKey.createTextAttributesKey(
	    "BOXLANG_STRING",
	    DefaultLanguageHighlighterColors.STRING
	);
	public static final TextAttributesKey	HASH_SIGN			= TextAttributesKey.createTextAttributesKey(
	    "BOXLANG_HASH_SIGN",
	    DefaultLanguageHighlighterColors.KEYWORD
	);
	public static final TextAttributesKey	LINE_COMMENT		= TextAttributesKey.createTextAttributesKey(
	    "BOXLANG_LINE_COMMENT",
	    DefaultLanguageHighlighterColors.LINE_COMMENT
	);
	public static final TextAttributesKey	BLOCK_COMMENT		= TextAttributesKey.createTextAttributesKey(
	    "BOXLANG_BLOCK_COMMENT",
	    DefaultLanguageHighlighterColors.BLOCK_COMMENT
	);
	public static final TextAttributesKey	DOC_COMMENT			= TextAttributesKey.createTextAttributesKey(
	    "BOXLANG_DOC_COMMENT",
	    DefaultLanguageHighlighterColors.DOC_COMMENT
	);
	public static final TextAttributesKey	OPERATOR			= TextAttributesKey.createTextAttributesKey(
	    "BOXLANG_OPERATOR",
	    DefaultLanguageHighlighterColors.OPERATION_SIGN
	);
	public static final TextAttributesKey	TAG					= TextAttributesKey.createTextAttributesKey(
	    "BOXLANG_TAG",
	    resolveHtmlTagKey()
	);
	public static final TextAttributesKey	BRACE				= TextAttributesKey.createTextAttributesKey(
	    "BOXLANG_BRACE",
	    DefaultLanguageHighlighterColors.BRACES
	);
	public static final TextAttributesKey	PAREN				= TextAttributesKey.createTextAttributesKey(
	    "BOXLANG_PAREN",
	    DefaultLanguageHighlighterColors.PARENTHESES
	);
	public static final TextAttributesKey	BRACKET				= TextAttributesKey.createTextAttributesKey(
	    "BOXLANG_BRACKET",
	    DefaultLanguageHighlighterColors.BRACKETS
	);
	public static final TextAttributesKey	COMMA				= TextAttributesKey.createTextAttributesKey(
	    "BOXLANG_COMMA",
	    DefaultLanguageHighlighterColors.COMMA
	);
	public static final TextAttributesKey	DOT					= TextAttributesKey.createTextAttributesKey(
	    "BOXLANG_DOT",
	    DefaultLanguageHighlighterColors.DOT
	);
	public static final TextAttributesKey	SEMICOLON			= TextAttributesKey.createTextAttributesKey(
	    "BOXLANG_SEMICOLON",
	    DefaultLanguageHighlighterColors.SEMICOLON
	);
	// Falls back to LINE_COMMENT (reliable in every theme) — XML overrides give distinct color
	public static final TextAttributesKey	ANNOTATION			= TextAttributesKey.createTextAttributesKey(
	    "BOXLANG_ANNOTATION",
	    DefaultLanguageHighlighterColors.LINE_COMMENT
	);
	// Falls back to IDENTIFIER bold — XML overrides give distinct color
	public static final TextAttributesKey	FUNCTION_NAME		= TextAttributesKey.createTextAttributesKey(
	    "BOXLANG_FUNCTION_NAME",
	    DefaultLanguageHighlighterColors.IDENTIFIER
	);
	// Falls back to KEYWORD (reliable in every theme) — XML overrides give distinct color
	public static final TextAttributesKey	CONSTANT			= TextAttributesKey.createTextAttributesKey(
	    "BOXLANG_CONSTANT",
	    DefaultLanguageHighlighterColors.KEYWORD
	);
	// Falls back to KEYWORD italic — XML overrides give distinct color
	public static final TextAttributesKey	SCOPE_VARIABLE		= TextAttributesKey.createTextAttributesKey(
	    "BOXLANG_SCOPE_VARIABLE",
	    DefaultLanguageHighlighterColors.KEYWORD
	);
	public static final TextAttributesKey	STORAGE_TYPE		= TextAttributesKey.createTextAttributesKey(
	    "BOXLANG_STORAGE_TYPE",
	    DefaultLanguageHighlighterColors.KEYWORD
	);
	public static final TextAttributesKey	STORAGE_MODIFIER	= TextAttributesKey.createTextAttributesKey(
	    "BOXLANG_STORAGE_MODIFIER",
	    DefaultLanguageHighlighterColors.KEYWORD
	);
	// Falls back to IDENTIFIER — XML overrides give distinct color
	public static final TextAttributesKey	BUILTIN_FUNCTION	= TextAttributesKey.createTextAttributesKey(
	    "BOXLANG_BUILTIN_FUNCTION",
	    DefaultLanguageHighlighterColors.IDENTIFIER
	);
	// Falls back to IDENTIFIER — XML overrides give distinct color
	public static final TextAttributesKey	STRUCT_KEY			= TextAttributesKey.createTextAttributesKey(
	    "BOXLANG_STRUCT_KEY",
	    DefaultLanguageHighlighterColors.IDENTIFIER
	);
	// Falls back to IDENTIFIER — XML overrides give distinct color
	public static final TextAttributesKey	FUNCTION_CALL		= TextAttributesKey.createTextAttributesKey(
	    "BOXLANG_FUNCTION_CALL",
	    DefaultLanguageHighlighterColors.IDENTIFIER
	);
	// Falls back to IDENTIFIER — XML overrides give distinct color
	public static final TextAttributesKey	NAMED_ARGUMENT		= TextAttributesKey.createTextAttributesKey(
	    "BOXLANG_NAMED_ARGUMENT",
	    DefaultLanguageHighlighterColors.IDENTIFIER
	);
	public static final TextAttributesKey	BAD_CHARACTER		= TextAttributesKey.createTextAttributesKey(
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
