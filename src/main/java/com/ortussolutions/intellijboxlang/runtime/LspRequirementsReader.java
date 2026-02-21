package com.ortussolutions.intellijboxlang.runtime;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class LspRequirementsReader {

	private LspRequirementsReader() {
	}

	public static LspRequirements read( Path boxJsonPath ) {
		if ( boxJsonPath == null || !Files.exists( boxJsonPath ) ) {
			return null;
		}
		try {
			String		content			= Files.readString( boxJsonPath, StandardCharsets.UTF_8 );
			JsonObject	root			= JsonParser.parseString( content ).getAsJsonObject();
			JsonObject	boxlang			= getObject( root, "boxlang" );
			String		minimumVersion	= getString( boxlang, "minimumVersion" );
			if ( minimumVersion == null || minimumVersion.isBlank() ) {
				return null;
			}
			LspRequirements requirements = new LspRequirements();
			requirements.minimumBoxLangVersion = minimumVersion;
			return requirements;
		} catch ( IOException ignored ) {
			return null;
		}
	}

	private static JsonObject getObject( JsonObject parent, String key ) {
		if ( parent == null || !parent.has( key ) || !parent.get( key ).isJsonObject() ) {
			return null;
		}
		return parent.getAsJsonObject( key );
	}

	private static String getString( JsonObject parent, String key ) {
		if ( parent == null || !parent.has( key ) ) {
			return null;
		}
		JsonElement value = parent.get( key );
		return value.isJsonPrimitive() ? value.getAsString() : null;
	}
}
