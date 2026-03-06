package com.ortussolutions.intellijboxlang.lexer;

import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;

public final class BoxLangTokenTypes {

	public static final IElementType	KEYWORD				= new BoxLangTokenType( "KEYWORD" );
	public static final IElementType	IDENTIFIER			= new BoxLangTokenType( "IDENTIFIER" );
	public static final IElementType	NUMBER				= new BoxLangTokenType( "NUMBER" );
	public static final IElementType	STRING				= new BoxLangTokenType( "STRING" );
	public static final IElementType	HASH_SIGN			= new BoxLangTokenType( "HASH_SIGN" );
	public static final IElementType	LINE_COMMENT		= new BoxLangTokenType( "LINE_COMMENT" );
	public static final IElementType	BLOCK_COMMENT		= new BoxLangTokenType( "BLOCK_COMMENT" );
	public static final IElementType	DOC_COMMENT			= new BoxLangTokenType( "DOC_COMMENT" );
	public static final IElementType	OPERATOR			= new BoxLangTokenType( "OPERATOR" );
	public static final IElementType	TAG					= new BoxLangTokenType( "TAG" );
	public static final IElementType	BRACE				= new BoxLangTokenType( "BRACE" );
	public static final IElementType	PAREN				= new BoxLangTokenType( "PAREN" );
	public static final IElementType	BRACKET				= new BoxLangTokenType( "BRACKET" );
	public static final IElementType	COMMA				= new BoxLangTokenType( "COMMA" );
	public static final IElementType	DOT					= new BoxLangTokenType( "DOT" );
	public static final IElementType	SEMICOLON			= new BoxLangTokenType( "SEMICOLON" );
	public static final IElementType	ANNOTATION			= new BoxLangTokenType( "ANNOTATION" );
	public static final IElementType	FUNCTION_NAME		= new BoxLangTokenType( "FUNCTION_NAME" );
	public static final IElementType	CONSTANT			= new BoxLangTokenType( "CONSTANT" );
	public static final IElementType	SCOPE_VARIABLE		= new BoxLangTokenType( "SCOPE_VARIABLE" );
	public static final IElementType	STORAGE_TYPE		= new BoxLangTokenType( "STORAGE_TYPE" );
	public static final IElementType	STORAGE_MODIFIER	= new BoxLangTokenType( "STORAGE_MODIFIER" );
	public static final IElementType	BUILTIN_FUNCTION	= new BoxLangTokenType( "BUILTIN_FUNCTION" );
	public static final IElementType	STRUCT_KEY			= new BoxLangTokenType( "STRUCT_KEY" );
	public static final IElementType	FUNCTION_CALL		= new BoxLangTokenType( "FUNCTION_CALL" );
	public static final IElementType	NAMED_ARGUMENT		= new BoxLangTokenType( "NAMED_ARGUMENT" );
	public static final IElementType	BAD_CHARACTER		= TokenType.BAD_CHARACTER;

	private BoxLangTokenTypes() {
	}
}
