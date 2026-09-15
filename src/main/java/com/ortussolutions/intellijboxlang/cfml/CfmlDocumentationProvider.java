package com.ortussolutions.intellijboxlang.cfml;

import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.openapi.util.text.StringUtil;

public final class CfmlDocumentationProvider extends AbstractDocumentationProvider {

	@Override
	public PsiElement getDocumentationElementForLookupItem( PsiManager manager, Object object, PsiElement element ) {
		if ( object instanceof CfmlFunctionCatalog.Function function && element != null ) {
			return new CfmlFunctionElement( element.getContainingFile(), function, element.getTextOffset(), 0 );
		}
		return null;
	}

	@Override
	public PsiElement getCustomDocumentationElement( com.intellij.openapi.editor.Editor editor, PsiFile file, PsiElement context, int offset ) {
		if ( file == null || !CfmlEditorContext.isCfml( file ) )
			return null;
		var	source	= file.getViewProvider().getContents();
		int	start	= Math.min( offset, source.length() );
		int	end		= start;
		while ( start > 0 && Character.isJavaIdentifierPart( source.charAt( start - 1 ) ) )
			start--;
		while ( end < source.length() && Character.isJavaIdentifierPart( source.charAt( end ) ) )
			end++;
		if ( !CfmlEditorContext.isExpression( file, source, end, true ) )
			return null;
		int next = end;
		while ( next < source.length() && Character.isWhitespace( source.charAt( next ) ) )
			next++;
		if ( next == source.length() || source.charAt( next ) != '(' )
			return null;
		String	name		= source.subSequence( start, end ).toString();
		var		functions	= new java.util.ArrayList<>( CfmlProjectFunctions.at( file, end ) );
		if ( CfmlEditorContext.isExpression( file, source, end ) )
			functions.addAll( CfmlFunctionCatalog.functions() );
		for ( var function : functions ) {
			if ( function.name().equalsIgnoreCase( name ) )
				return new CfmlFunctionElement( file, function, start, 0 );
		}
		return null;
	}

	@Override
	public String generateDoc( PsiElement element, PsiElement originalElement ) {
		if ( ! ( element instanceof CfmlFunctionElement functionElement ) )
			return null;
		return documentation( functionElement.function );
	}

	static String documentation( CfmlFunctionCatalog.Function function ) {
		StringBuilder html = new StringBuilder( "<div class='definition'><pre>" )
		    .append( escape( function.syntax() ) ).append( " → " ).append( escape( function.returns() ) )
		    .append( "</pre></div><div class='content'>" ).append( escape( function.description() ) ).append( "</div><table class='sections'>" );
		for ( var parameter : function.params() ) {
			html.append( "<tr><td><b>" ).append( escape( parameter.name() ) ).append( "</b> " )
			    .append( escape( parameter.type() ) ).append( parameter.required() ? " (required)" : " (optional)" )
			    .append( "</td><td>" ).append( escape( parameter.description() ) ).append( "</td></tr>" );
		}
		if ( !"CFDocs".equals( function.origin() ) )
			return html.append( "</table><p>Project-defined function</p>" ).toString();
		return html.append( "</table><p>Source: <a href='https://cfdocs.org/" )
		    .append( escape( function.sourceName() ) ).append( "'>CFDocs</a></p>" ).toString();
	}

	private static String escape( String text ) {
		return StringUtil.escapeXmlEntities( text == null ? "" : text );
	}
}
