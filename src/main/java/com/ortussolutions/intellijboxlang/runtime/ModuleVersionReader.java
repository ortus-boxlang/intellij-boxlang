package com.ortussolutions.intellijboxlang.runtime;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jetbrains.annotations.Nullable;

/**
 * Utility to read module version from box.json files.
 */
public final class ModuleVersionReader {

	private ModuleVersionReader() {
	}

	/**
	 * Reads the version from a box.json file.
	 * 
	 * @param boxJsonPath path to the box.json file
	 * 
	 * @return the version string, or null if not found
	 */
	@Nullable
	public static String readVersion( @Nullable Path boxJsonPath ) {
		if ( boxJsonPath == null || !Files.exists( boxJsonPath ) ) {
			return null;
		}
		try {
			String		content	= Files.readString( boxJsonPath, StandardCharsets.UTF_8 );
			JsonObject	root	= JsonParser.parseString( content ).getAsJsonObject();
			return getString( root, "version" );
		} catch ( IOException ignored ) {
			return null;
		}
	}

	private static String getString( JsonObject parent, String key ) {
		if ( parent == null || !parent.has( key ) ) {
			return null;
		}
		JsonElement value = parent.get( key );
		return value.isJsonPrimitive() ? value.getAsString() : null;
	}
}
