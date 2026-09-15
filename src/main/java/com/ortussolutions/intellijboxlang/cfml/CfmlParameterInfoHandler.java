package com.ortussolutions.intellijboxlang.cfml;

import com.intellij.lang.parameterInfo.*;
import com.intellij.psi.PsiFile;
import com.ortussolutions.intellijboxlang.lexer.BoxLangLexer;
import java.util.ArrayDeque;

public final class CfmlParameterInfoHandler
    implements ParameterInfoHandler<CfmlFunctionElement, CfmlFunctionCatalog.Function>, com.intellij.openapi.project.DumbAware {

	private static final com.intellij.psi.tree.TokenSet COMMENTS = new com.ortussolutions.intellijboxlang.parser.BoxLangParserDefinition().getCommentTokens();

	private static final class Frame {

		int							offset;
		int							argument;
		int							argumentStart;
		final java.util.Set<String>	supplied	= new java.util.HashSet<>();
		String						name;
		char						delimiter;

		Frame( int offset, String name, char delimiter ) {
			this.offset			= offset;
			this.argumentStart	= offset + 1;
			this.name			= name;
			this.delimiter		= delimiter;
		}
	}

	static CfmlFunctionElement findCall( PsiFile file, int offset ) {
		if ( !CfmlEditorContext.isCfml( file ) )
			return null;
		var	source	= file.getViewProvider().getContents();
		var	lexer	= new BoxLangLexer();
		lexer.start( source );
		var stack = new ArrayDeque<Frame>();
		while ( lexer.getTokenType() != null && lexer.getTokenStart() < offset ) {
			var type = lexer.getTokenType();
			if ( type == com.ortussolutions.intellijboxlang.lexer.BoxLangTokenTypes.STRING
			    || COMMENTS.contains( type ) ) {
				int commentEnd = CfmlEditorContext.templateCommentEnd( source, lexer.getTokenStart() );
				if ( commentEnd > lexer.getTokenStart() )
					lexer.start( source, commentEnd, source.length(), 0 );
				else
					lexer.advance();
				continue;
			}
			int		start	= lexer.getTokenStart();
			String	token	= lexer.getTokenText();
			if ( "(".equals( token ) || "[".equals( token ) || "{".equals( token ) ) {
				int end = start;
				while ( end > 0 && Character.isWhitespace( source.charAt( end - 1 ) ) )
					end--;
				int nameStart = end;
				while ( nameStart > 0 && Character.isJavaIdentifierPart( source.charAt( nameStart - 1 ) ) )
					nameStart--;
				String name = "(".equals( token ) && CfmlEditorContext.isExpression( file, source, end, true )
				    ? source.subSequence( nameStart, end ).toString()
				    : "";
				stack.push( new Frame( start, name, token.charAt( 0 ) ) );
			} else if ( ")".equals( token ) || "]".equals( token ) || "}".equals( token ) ) {
				if ( !stack.isEmpty() )
					stack.pop();
			} else if ( ",".equals( token ) && !stack.isEmpty() ) {
				var		frame		= stack.peek();
				String	supplied	= namedArgument( source, frame.argumentStart, start );
				if ( supplied != null )
					frame.supplied.add( supplied.toLowerCase( java.util.Locale.ROOT ) );
				frame.argument++;
				frame.argumentStart = lexer.getTokenEnd();
			}
			lexer.advance();
		}
		for ( Frame frame : stack ) {
			if ( frame.delimiter != '(' || frame.name.isEmpty() )
				continue;
			var functions = new java.util.ArrayList<>( CfmlProjectFunctions.at( file, frame.offset ) );
			if ( CfmlEditorContext.isExpression( file, source, frame.offset ) )
				functions.addAll( CfmlFunctionCatalog.functions() );
			for ( var function : functions ) {
				if ( function.name().equalsIgnoreCase( frame.name ) ) {
					int		active	= frame.argument;
					String	named	= namedArgument( source, frame.argumentStart, offset );
					if ( named != null ) {
						active = -1;
						for ( int i = 0; i < function.params().size(); i++ ) {
							if ( function.params().get( i ).name().equalsIgnoreCase( named ) ) {
								active = i;
								break;
							}
						}
					}
					return new CfmlFunctionElement( file, function, frame.offset, active, frame.argumentStart, frame.supplied );
				}
			}
		}
		return null;
	}

	private static String namedArgument( CharSequence source, int start, int end ) {
		var lexer = new BoxLangLexer();
		lexer.start( source.subSequence( start, end ) );
		String name = null;
		while ( lexer.getTokenType() != null ) {
			if ( COMMENTS.contains( lexer.getTokenType() ) || lexer.getTokenText().isBlank() ) {
				lexer.advance();
				continue;
			}
			if ( name == null ) {
				name = lexer.getTokenText();
				if ( !name.matches( "[A-Za-z_$][A-Za-z0-9_$]*" ) )
					return null;
			} else {
				return "=".equals( lexer.getTokenText() ) ? name : null;
			}
			lexer.advance();
		}
		return null;
	}

	@Override
	public CfmlFunctionElement findElementForParameterInfo( CreateParameterInfoContext context ) {
		var call = findCall( context.getFile(), context.getOffset() );
		if ( call != null )
			context.setItemsToShow( new Object[] { call.function } );
		return call;
	}

	@Override
	public void showParameterInfo( CfmlFunctionElement element, CreateParameterInfoContext context ) {
		context.showHint( element, element.offset, this );
	}

	@Override
	public CfmlFunctionElement findElementForUpdatingParameterInfo( UpdateParameterInfoContext context ) {
		var call = findCall( context.getFile(), context.getOffset() );
		if ( call == null || call.offset != context.getParameterListStart() ) {
			context.removeHint();
			return null;
		}
		return call;
	}

	@Override
	public void updateParameterInfo( CfmlFunctionElement element, UpdateParameterInfoContext context ) {
		context.setParameterOwner( element );
		context.setCurrentParameter( element.parameter );
	}

	@Override
	public void updateUI( CfmlFunctionCatalog.Function function, ParameterInfoUIContext context ) {
		StringBuilder	text	= new StringBuilder();
		int				start	= -1, end = -1;
		for ( int i = 0; i < function.params().size(); i++ ) {
			if ( i > 0 )
				text.append( ", " );
			var	parameter	= function.params().get( i );
			int	from		= text.length();
			text.append( parameter.type() ).append( " " ).append( parameter.name() );
			if ( !parameter.required() )
				text.append( " (optional)" );
			if ( i == context.getCurrentParameterIndex() ) {
				start	= from;
				end		= text.length();
			}
		}
		if ( text.isEmpty() )
			text.append( "No parameters" );
		context.setupUIComponentPresentation( text.toString(), start, end, false, false, false, context.getDefaultParameterColor() );
	}
}
