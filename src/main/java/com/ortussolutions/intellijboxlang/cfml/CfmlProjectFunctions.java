package com.ortussolutions.intellijboxlang.cfml;

import com.google.gson.Gson;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiDocumentManager;
import com.ortussolutions.intellijboxlang.lsp.BoxLangLspClientService;
import java.util.ArrayList;
import java.util.List;

/** Project signatures supplied by the language server; built-ins keep the local CFDocs catalog. */
final class CfmlProjectFunctions {

	private static final Gson GSON = new Gson();

	private CfmlProjectFunctions() {
	}

	static List<CfmlFunctionCatalog.Function> at( PsiFile file, int offset ) {
		if ( !CfmlEditorContext.isCfml( file ) )
			return List.of();
		var document = PsiDocumentManager.getInstance( file.getProject() ).getDocument( file );
		if ( document == null || file.getVirtualFile() == null )
			return List.of();
		List<CfmlFunctionCatalog.Function> result = new ArrayList<>();
		for ( var item : BoxLangLspClientService.getInstance( file.getProject() ).requestCompletions( file.getVirtualFile(), document, offset ) ) {
			if ( item.getData() == null )
				continue;
			try {
				var data = GSON.toJsonTree( item.getData() );
				if ( !data.isJsonObject() )
					continue;
				if ( data.getAsJsonObject().has( "boxlangMember" ) ) {
					var		member		= data.getAsJsonObject().getAsJsonObject( "boxlangMember" );
					String	globalName	= member.get( "globalName" ).getAsString();
					String	name		= member.get( "name" ).getAsString();
					for ( var builtin : CfmlFunctionCatalog.functions() ) {
						if ( !builtin.name().equalsIgnoreCase( globalName ) || builtin.member() == null )
							continue;
						var signature = java.util.regex.Pattern.compile( "\\.([A-Za-z_$][A-Za-z0-9_$]*)\\s*\\((.*)\\)" ).matcher( builtin.member() );
						if ( !signature.find() || !signature.group( 1 ).equalsIgnoreCase( name ) )
							continue;
						String	arguments	= signature.group( 2 );
						var		parameters	= builtin.params().stream().filter( parameter -> java.util.regex.Pattern.compile(
						    "(?i)(?<![A-Za-z0-9_$])" + java.util.regex.Pattern.quote( parameter.name() ) + "(?![A-Za-z0-9_$])" ).matcher( arguments ).find() )
						    .toList();
						result.add( new CfmlFunctionCatalog.Function( name, name + "(" + arguments + ")", builtin.returns(), builtin.description(), parameters,
						    "CFDocs", builtin.name(), null ) );
					}
					continue;
				}
				if ( !data.getAsJsonObject().has( "boxlangCallable" ) )
					continue;
				var function = GSON.fromJson( data.getAsJsonObject().get( "boxlangCallable" ), CfmlFunctionCatalog.Function.class );
				if ( function != null && "project".equals( function.origin() ) && function.name() != null
				    && function.name().matches( "[A-Za-z_$][A-Za-z0-9_$]*" ) )
					result.add( function );
			} catch ( RuntimeException ignored ) {
				// An older or third-party server can omit this optional structured signature.
			}
		}
		return result;
	}
}
