package com.ortussolutions.intellijboxlang.runtime;

import com.intellij.openapi.progress.ProgressIndicator;
import com.ortussolutions.intellijboxlang.settings.BoxLangStoragePaths;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

public final class BoxLangRuntimeInstaller {

	private static final Pattern CACHE_VERSION = Pattern.compile( "^boxlang-[0-9A-Za-z.+-]+$" );

	private BoxLangRuntimeInstaller() {
	}

	public static BoxLangRuntimeSelection installRuntime( String resolvedVersion, String downloadUrl, ProgressIndicator indicator ) throws IOException {
		if ( downloadUrl == null || downloadUrl.isBlank() ) {
			throw new IOException( "Missing BoxLang download URL for " + resolvedVersion );
		}
		Path	versionDir	= cacheVersionDirectory( resolvedVersion );
		String	filename	= resolvedVersion + ".jar";
		URL		url			= URI.create( downloadUrl ).toURL();
		Files.createDirectories( versionDir );

		Path jarPath = versionDir.resolve( filename );
		installJar( url, jarPath, indicator );
		writeMetadata( versionDir, resolvedVersion );
		BoxLangRuntimeSelection selection = new BoxLangRuntimeSelection();
		selection.jarPath			= jarPath;
		selection.resolvedVersion	= resolvedVersion;
		return selection;
	}

	public static BoxLangRuntimeSelection resolveCachedJar( String resolvedVersion ) throws IOException {
		Path	versionDir	= cacheVersionDirectory( resolvedVersion );
		Path	jarPath		= versionDir.resolve( resolvedVersion + ".jar" );
		if ( !isValidRuntimeJar( jarPath ) ) {
			return null;
		}
		BoxLangRuntimeSelection selection = new BoxLangRuntimeSelection();
		selection.jarPath			= jarPath;
		selection.resolvedVersion	= resolvedVersion;
		return selection;
	}

	private static Path cacheVersionDirectory( String resolvedVersion ) throws IOException {
		if ( resolvedVersion == null || resolvedVersion.contains( ".." ) || resolvedVersion.contains( "/" )
		    || resolvedVersion.contains( "\\" ) || !CACHE_VERSION.matcher( resolvedVersion ).matches() ) {
			throw new IOException( "Invalid BoxLang runtime version name: " + resolvedVersion );
		}
		Path	cacheRoot	= BoxLangStoragePaths.getRuntimeCacheRoot().toAbsolutePath().normalize();
		Path	directory	= cacheRoot.resolve( resolvedVersion ).normalize();
		if ( !directory.startsWith( cacheRoot ) || !cacheRoot.equals( directory.getParent() ) ) {
			throw new IOException( "BoxLang runtime version escapes the cache directory: " + resolvedVersion );
		}
		return directory;
	}

	static void installJar( URL url, Path jarPath, ProgressIndicator indicator ) throws IOException {
		Files.createDirectories( jarPath.getParent() );
		Path staging = Files.createTempFile( jarPath.getParent(), "boxlang-runtime-", ".download" );
		try {
			BoxLangDownloadService.downloadTo( url, staging, indicator );
			if ( !isValidRuntimeJar( staging ) )
				throw new IOException( "The download is not a valid BoxLang runtime JAR. The server may have returned an error page." );
			if ( indicator != null )
				indicator.checkCanceled();
			try {
				Files.move( staging, jarPath, java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING );
			} catch ( java.nio.file.AtomicMoveNotSupportedException e ) {
				Files.move( staging, jarPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING );
			}
		} finally {
			Files.deleteIfExists( staging );
		}
	}

	public static boolean isValidRuntimeJar( Path path ) {
		if ( !Files.isRegularFile( path ) )
			return false;
		try ( var jar = new java.util.jar.JarFile( path.toFile() ) ) {
			return jar.getEntry( "ortus/boxlang/runtime/BoxRunner.class" ) != null;
		} catch ( IOException e ) {
			return false;
		}
	}

	private static void writeMetadata( Path versionDir, String resolvedVersion ) throws IOException {
		Path	metadata	= versionDir.resolve( "version.json" );
		String	content		= "{\"name\":\"" + resolvedVersion + "\"}";
		Files.writeString( metadata, content, StandardCharsets.UTF_8 );
	}

	public static String normalizeVersionName( String version ) {
		if ( version == null ) {
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
