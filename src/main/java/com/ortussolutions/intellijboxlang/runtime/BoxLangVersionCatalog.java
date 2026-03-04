package com.ortussolutions.intellijboxlang.runtime;

import java.io.IOException;
import com.vdurmont.semver4j.Semver;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BoxLangVersionCatalog {

	private static final String		LIST_URL			= "https://s3.amazonaws.com/downloads.ortussolutions.com?prefix=ortussolutions/boxlang/&list-type=2";
	private static final String		DOWNLOAD_BASE		= "https://downloads.ortussolutions.com/";
	private static final Pattern	KEY_PATTERN			= Pattern.compile( "<Key>([^<]+)</Key>" );
	private static final Pattern	MODIFIED_PATTERN	= Pattern.compile( "<LastModified>([^<]+)</LastModified>" );
	private static final Pattern	CONTENTS_PATTERN	= Pattern.compile( "<Contents>(.*?)</Contents>", Pattern.DOTALL );
	private static final Pattern	JAR_NAME_PATTERN	= Pattern.compile( "^boxlang-[0-9A-Za-z.+-]+$", Pattern.CASE_INSENSITIVE );

	private BoxLangVersionCatalog() {
	}

	public static String resolveLatestVersion() throws IOException {
		return resolveLatestInfo().name();
	}

	public static BoxLangVersionInfo resolveLatestInfo() throws IOException {
		return loadEntries().stream()
		    .max( Comparator.comparing( BoxLangVersionInfo::modifiedAt ) )
		    .orElseThrow( () -> new IOException( "Unable to resolve latest BoxLang version." ) );
	}

	public static String resolveLatestAtLeast( String minimumVersion ) throws IOException {
		return resolveLatestAtLeastInfo( minimumVersion ).name();
	}

	public static BoxLangVersionInfo resolveLatestAtLeastInfo( String minimumVersion ) throws IOException {
		Semver minimum = parseSemver( minimumVersion );
		if ( minimum == null ) {
			throw new IOException( "Invalid minimum BoxLang version: " + minimumVersion );
		}
		return resolveLatestAtLeastInfo( minimum );
	}

	public static BoxLangVersionInfo resolveLatestAtLeastInfo( Semver minimum ) throws IOException {
		return loadEntries().stream()
		    .filter( entry -> entry.version() != null && entry.version().isGreaterThanOrEqualTo( minimum ) )
		    .max( ( left, right ) -> {
			    int versionCompare = left.version().compareTo( right.version() );
			    if ( versionCompare != 0 ) {
				    return versionCompare;
			    }
			    return left.modifiedAt().compareTo( right.modifiedAt() );
		    } )
		    .orElseThrow( () -> new IOException( "Unable to resolve BoxLang version >= " + minimum ) );
	}

	public static BoxLangVersionInfo resolveVersionInfo( String version ) throws IOException {
		String normalized = normalizeName( version );
		if ( normalized == null ) {
			return null;
		}
		return loadEntries().stream()
		    .filter( entry -> normalized.equals( entry.name() ) )
		    .findFirst()
		    .orElse( null );
	}

	private static List<BoxLangVersionInfo> loadEntries() throws IOException {
		try {
			String						payload	= new String( URI.create( LIST_URL ).toURL().openStream().readAllBytes(), StandardCharsets.UTF_8 );
			List<BoxLangVersionInfo>	entries	= new ArrayList<>();
			Matcher						matcher	= CONTENTS_PATTERN.matcher( payload );
			while ( matcher.find() ) {
				String	block			= matcher.group( 1 );
				String	key				= extractFirst( block, KEY_PATTERN );
				String	modifiedValue	= extractFirst( block, MODIFIED_PATTERN );
				if ( key == null || modifiedValue == null ) {
					continue;
				}
				String lowerKey = key.toLowerCase();
				if ( !lowerKey.endsWith( ".jar" )
				    || lowerKey.contains( "javadoc" )
				    || lowerKey.contains( "sources" )
				    || lowerKey.contains( "snapshot" ) ) {
					continue;
				}
				String fileName = key.substring( key.lastIndexOf( '/' ) + 1, key.length() - 4 );
				if ( !JAR_NAME_PATTERN.matcher( fileName ).matches() ) {
					continue;
				}
				Semver	version		= parseSemver( fileName );
				String	downloadUrl	= DOWNLOAD_BASE + key;
				entries.add( new BoxLangVersionInfo( fileName, downloadUrl, Instant.parse( modifiedValue ), version ) );
			}
			return entries;
		} catch ( Exception e ) {
			throw new IOException( "Unable to resolve BoxLang version list.", e );
		}
	}

	private static List<String> extractAll( String payload, Pattern pattern ) {
		List<String>	results	= new ArrayList<>();
		Matcher			matcher	= pattern.matcher( payload );
		while ( matcher.find() ) {
			results.add( matcher.group( 1 ) );
		}
		return results;
	}

	private static String extractFirst( String payload, Pattern pattern ) {
		Matcher matcher = pattern.matcher( payload );
		return matcher.find() ? matcher.group( 1 ) : null;
	}

	private static Semver parseSemver( String value ) {
		if ( value == null || value.isBlank() ) {
			return null;
		}
		String normalized = value.trim();
		if ( normalized.startsWith( "boxlang-" ) ) {
			normalized = normalized.substring( "boxlang-".length() );
		}
		int atIndex = normalized.indexOf( '@' );
		if ( atIndex >= 0 ) {
			normalized = normalized.substring( atIndex + 1 );
		}
		if ( normalized.isBlank() ) {
			return null;
		}
		try {
			return new Semver( normalized, Semver.SemverType.NPM );
		} catch ( Exception ignored ) {
			return null;
		}
	}

	private static String normalizeName( String version ) {
		if ( version == null || version.isBlank() ) {
			return null;
		}
		String normalized = version.trim();
		if ( normalized.endsWith( ".jar" ) ) {
			normalized = normalized.substring( 0, normalized.length() - 4 );
		}
		if ( !normalized.startsWith( "boxlang-" ) ) {
			normalized = "boxlang-" + normalized;
		}
		return normalized;
	}
}
