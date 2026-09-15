package com.ortussolutions.intellijboxlang.cfml;

import com.intellij.psi.PsiFile;
import com.ortussolutions.intellijboxlang.file.BoxLangFileType;
import com.ortussolutions.intellijboxlang.lexer.BoxLangLexer;
import com.ortussolutions.intellijboxlang.lexer.BoxLangTokenTypes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

final class CfmlEditorContext {

	private static final Pattern		TAG			= Pattern.compile( "<(/?)(cf[a-z][a-z0-9_]*)(?=[\\s/>]|$)", Pattern.CASE_INSENSITIVE );
	private static final Set<String>	EXPRESSIONS	= Set.of( "cfset", "cfif", "cfelseif", "cfreturn" );

	record Tag( int start, int end, String name, boolean closing, boolean selfClosing, boolean complete ) {
	}

	private CfmlEditorContext() {
	}

	static boolean isCfml( PsiFile file ) {
		String name = file.getName().toLowerCase( Locale.ROOT );
		return file.getFileType() == BoxLangFileType.INSTANCE && ( name.endsWith( ".cfm" ) || name.endsWith( ".cfc" ) );
	}

	static boolean excluded( CharSequence source, int offset ) {
		var lexer = new BoxLangLexer();
		lexer.start( source );
		return advanceTo( lexer, source, offset );
	}

	private static boolean advanceTo( BoxLangLexer lexer, CharSequence source, int offset ) {
		while ( lexer.getTokenType() != null ) {
			int end = templateCommentEnd( source, lexer.getTokenStart() );
			if ( end > lexer.getTokenStart() && lexer.getTokenType() == BoxLangTokenTypes.BLOCK_COMMENT ) {
				if ( offset < end )
					return true;
				lexer.start( source, end, source.length(), 0 );
			} else if ( lexer.getTokenEnd() <= offset )
				lexer.advance();
			else
				return excludedType( lexer.getTokenType() );
		}
		return false;
	}

	static int templateCommentEnd( CharSequence source, int start ) {
		if ( start + 5 > source.length() || !source.subSequence( start, start + 5 ).toString().equals( "<!---" ) )
			return start;
		int depth = 1;
		for ( int i = start + 5; i < source.length(); i++ ) {
			if ( i + 5 <= source.length() && source.subSequence( i, i + 5 ).toString().equals( "<!---" ) ) {
				depth++;
				i += 4;
			} else if ( i + 4 <= source.length() && source.subSequence( i, i + 4 ).toString().equals( "--->" ) ) {
				if ( --depth == 0 )
					return i + 4;
				i += 3;
			}
		}
		return source.length();
	}

	private static boolean excludedType( com.intellij.psi.tree.IElementType type ) {
		return type == BoxLangTokenTypes.STRING || type == BoxLangTokenTypes.LINE_COMMENT
		    || type == BoxLangTokenTypes.BLOCK_COMMENT || type == BoxLangTokenTypes.DOC_COMMENT;
	}

	static List<Tag> tags( CharSequence source ) {
		List<Tag>	tags	= new ArrayList<>();
		var			matcher	= TAG.matcher( source );
		int			next	= 0;
		var			lexer	= new BoxLangLexer();
		lexer.start( source );
		while ( matcher.find( next ) ) {
			int start = matcher.start();
			if ( advanceTo( lexer, source, start ) ) {
				next = matcher.end();
				continue;
			}
			char	quote		= 0;
			int		end			= matcher.end();
			boolean	complete	= false;
			for ( ; end < source.length(); end++ ) {
				char c = source.charAt( end );
				if ( quote != 0 ) {
					if ( c == quote ) {
						if ( end + 1 < source.length() && source.charAt( end + 1 ) == quote )
							end++;
						else
							quote = 0;
					}
				} else if ( c == '\'' || c == '"' )
					quote = c;
				else if ( c == '>' ) {
					end++;
					complete = true;
					break;
				} else if ( c == '<' )
					break;
			}
			tags.add( new Tag( start, end, matcher.group( 2 ), !matcher.group( 1 ).isEmpty(),
			    complete && source.charAt( end - 2 ) == '/', complete ) );
			next = Math.max( matcher.end(), end );
		}
		return tags;
	}

	static boolean isExpression( PsiFile file, CharSequence source, int offset ) {
		return isExpression( file, source, offset, false );
	}

	static boolean isExpression( PsiFile file, CharSequence source, int offset, boolean allowMember ) {
		if ( !isCfml( file ) || offset <= 0 || excluded( source, offset - 1 ) )
			return false;
		int start = offset;
		while ( start > 0 && Character.isJavaIdentifierPart( source.charAt( start - 1 ) ) )
			start--;
		int before = start - 1;
		while ( before >= 0 && Character.isWhitespace( source.charAt( before ) ) )
			before--;
		if ( !allowMember && before >= 0 && source.charAt( before ) == '.' )
			return false;
		int wordEnd = before + 1;
		while ( before >= 0 && Character.isJavaIdentifierPart( source.charAt( before ) ) )
			before--;
		if ( source.subSequence( before + 1, wordEnd ).toString().equalsIgnoreCase( "function" ) )
			return false;
		boolean	interpolation	= inInterpolation( source, start );
		boolean	script			= false;
		for ( Tag tag : tags( source ) ) {
			if ( tag.start() >= offset )
				break;
			if ( tag.end() >= offset && ( !tag.complete() || tag.end() > offset ) ) {
				String name = tag.name().toLowerCase( Locale.ROOT );
				if ( offset <= tag.start() + 1 + name.length() )
					return false;
				return !tag.closing() && ( EXPRESSIONS.contains( name ) || interpolation );
			}
			if ( tag.name().equalsIgnoreCase( "cfscript" ) )
				script = !tag.closing();
		}
		if ( script )
			return true;
		if ( file.getName().toLowerCase( Locale.ROOT ).endsWith( ".cfc" ) && !source.toString().stripLeading().startsWith( "<" ) )
			return true;
		return interpolation;
	}

	private static boolean inInterpolation( CharSequence source, int start ) {
		// Interpolated expressions in template output use paired hash delimiters.
		var lexer = new BoxLangLexer();
		lexer.start( source );
		boolean interpolation = false;
		while ( lexer.getTokenType() != null && lexer.getTokenStart() < start ) {
			if ( lexer.getTokenType() == BoxLangTokenTypes.HASH_SIGN )
				interpolation = !interpolation;
			lexer.advance();
		}
		return interpolation;
	}
}
