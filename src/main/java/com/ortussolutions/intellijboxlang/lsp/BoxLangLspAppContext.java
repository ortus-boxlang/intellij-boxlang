package com.ortussolutions.intellijboxlang.lsp;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable, app-scoped context used to bootstrap the LSP with mapping and module awareness.
 */
public record BoxLangLspAppContext(
    Path appRoot,
    Map<String, String> mappings,
    List<Path> moduleDirectories,
    String contextHash ) {

	public BoxLangLspAppContext {
		appRoot				= appRoot.toAbsolutePath().normalize();
		mappings			= Map.copyOf( mappings );
		moduleDirectories	= List.copyOf( moduleDirectories );
	}

	public Map<String, Object> toInitializeOptions() {
		Map<String, Object> appContext = new LinkedHashMap<>();
		appContext.put( "rootPath", appRoot.toString() );
		appContext.put( "mappings", mappings );
		appContext.put( "moduleDirs", moduleDirectories.stream().map( Path::toString ).toList() );
		appContext.put( "contextHash", contextHash );
		return Map.of(
		    "boxlang",
		    Map.of( "appContext", appContext )
		);
	}
}
