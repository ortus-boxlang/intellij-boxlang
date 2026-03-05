package com.ortussolutions.intellijboxlang.runtime;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Fetches available versions for BoxLang modules from ForgeBox.
 */
public final class ForgeBoxVersionFetcher {

	private ForgeBoxVersionFetcher() {
	}

	/**
	 * Fetches available versions for bx-lsp from ForgeBox.
	 * Returns versions sorted with latest first.
	 */
	@NotNull
	public static List<String> fetchLspVersions() throws IOException {
		return fetchVersions( "https://forgebox.io/api/v1/entry/bx-lsp" );
	}

	/**
	 * Fetches available versions for bx-debugger from ForgeBox.
	 * Returns versions sorted with latest first.
	 */
	@NotNull
	public static List<String> fetchDebuggerVersions() throws IOException {
		return fetchVersions( "https://forgebox.io/api/v1/entry/bx-debugger" );
	}

	@NotNull
	private static List<String> fetchVersions( String endpoint ) throws IOException {
		String			payload			= fetchPayload( endpoint );
		JsonObject		data			= parseData( payload );
		List<String>	versions		= new ArrayList<>();

		// Add latest version first
		JsonObject		latestVersion	= getObject( data, "latestVersion" );
		if ( latestVersion != null ) {
			String latest = getString( latestVersion, "version" );
			if ( latest != null && !latest.isBlank() ) {
				versions.add( latest );
			}
		}

		// Add all other versions
		if ( data.has( "versions" ) && data.get( "versions" ).isJsonArray() ) {
			JsonArray versionsArray = data.getAsJsonArray( "versions" );
			for ( JsonElement element : versionsArray ) {
				if ( !element.isJsonObject() ) {
					continue;
				}
				JsonObject	entry	= element.getAsJsonObject();
				String		version	= getString( entry, "version" );
				if ( version != null && !version.isBlank() && !versions.contains( version ) ) {
					versions.add( version );
				}
			}
		}

		return versions;
	}

	private static String fetchPayload( String endpoint ) throws IOException {
		try ( InputStream input = URI.create( endpoint ).toURL().openStream() ) {
			return new String( input.readAllBytes(), StandardCharsets.UTF_8 );
		}
	}

	private static JsonObject parseData( String payload ) throws IOException {
		try {
			JsonObject	root	= JsonParser.parseString( payload ).getAsJsonObject();
			JsonObject	data	= getObject( root, "data" );
			if ( data == null ) {
				throw new IOException( "ForgeBox response missing data payload." );
			}
			return data;
		} catch ( Exception e ) {
			throw new IOException( "Unable to parse ForgeBox response.", e );
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
