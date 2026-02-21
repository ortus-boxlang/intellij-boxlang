package com.ortussolutions.intellijboxlang.lexer;

import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;

public final class BoxLangTokenTypes {

	public static final IElementType	KEYWORD			= new BoxLangTokenType( "KEYWORD" );
	public static final IElementType	IDENTIFIER		= new BoxLangTokenType( "IDENTIFIER" );
	public static final IElementType	NUMBER			= new BoxLangTokenType( "NUMBER" );
	public static final IElementType	STRING			= new BoxLangTokenType( "STRING" );
	public static final IElementType	LINE_COMMENT	= new BoxLangTokenType( "LINE_COMMENT" );
	public static final IElementType	BLOCK_COMMENT	= new BoxLangTokenType( "BLOCK_COMMENT" );
	public static final IElementType	OPERATOR		= new BoxLangTokenType( "OPERATOR" );
	public static final IElementType	TAG				= new BoxLangTokenType( "TAG" );
	public static final IElementType	BRACE			= new BoxLangTokenType( "BRACE" );
	public static final IElementType	PAREN			= new BoxLangTokenType( "PAREN" );
	public static final IElementType	BRACKET			= new BoxLangTokenType( "BRACKET" );
	public static final IElementType	COMMA			= new BoxLangTokenType( "COMMA" );
	public static final IElementType	DOT				= new BoxLangTokenType( "DOT" );
	public static final IElementType	SEMICOLON		= new BoxLangTokenType( "SEMICOLON" );
	public static final IElementType	BAD_CHARACTER	= TokenType.BAD_CHARACTER;

	private BoxLangTokenTypes() {
	}
}
