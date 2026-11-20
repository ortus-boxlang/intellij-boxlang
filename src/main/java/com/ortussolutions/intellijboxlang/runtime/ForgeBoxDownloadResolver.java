package com.ortussolutions.intellijboxlang.runtime;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

final class ForgeBoxDownloadResolver {

	private static final String	FORGEBOX_STORAGE	= "forgeboxstorage";
	private static final String	STORAGE_ENDPOINT	= "https://forgebox.io/api/v1/storage/";

	private ForgeBoxDownloadResolver() {
	}

	static String resolve( String downloadLocation, String slug, String version ) throws IOException {
		if ( downloadLocation == null || downloadLocation.isBlank() ) {
			throw new IOException( "ForgeBox did not provide a download location for " + slug + " " + version + "." );
		}
		if ( !FORGEBOX_STORAGE.equalsIgnoreCase( downloadLocation.trim() ) ) {
			return downloadLocation;
		}

		String endpoint = storageEndpoint( slug, version );
		try ( InputStream input = URI.create( endpoint ).toURL().openStream() ) {
			return parseStorageLocation( new String( input.readAllBytes(), StandardCharsets.UTF_8 ) );
		}
	}

	static String storageEndpoint( String slug, String version ) {
		return STORAGE_ENDPOINT + encodePathSegment( slug ) + "/" + encodePathSegment( version );
	}

	static String parseStorageLocation( String payload ) throws IOException {
		try {
			JsonObject root = JsonParser.parseString( payload ).getAsJsonObject();
			if ( root.has( "error" ) && root.get( "error" ).getAsBoolean() ) {
				throw new IOException( "ForgeBox could not resolve the package storage location." );
			}
			JsonElement data = root.get( "data" );
			if ( data == null || !data.isJsonPrimitive() || data.getAsString().isBlank() ) {
				throw new IOException( "ForgeBox storage response did not contain a download URL." );
			}
			return data.getAsString();
		} catch ( IOException e ) {
			throw e;
		} catch ( Exception e ) {
			throw new IOException( "Unable to parse ForgeBox storage response.", e );
		}
	}

	private static String encodePathSegment( String value ) {
		return URLEncoder.encode( value, StandardCharsets.UTF_8 ).replace( "+", "%20" );
	}
}
