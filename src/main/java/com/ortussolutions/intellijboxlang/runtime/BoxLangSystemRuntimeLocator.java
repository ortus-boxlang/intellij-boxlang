package com.ortussolutions.intellijboxlang.runtime;

import com.intellij.openapi.diagnostic.Logger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.jetbrains.annotations.Nullable;

/**
 * Locates BoxLang runtime JARs installed outside the IDE plugin cache — for example
 * via BVM ({@code ~/.bvm}) or a manual installation ({@code ~/.local/boxlang}).
 *
 * <p>
 * Probe order:
 * <ol>
 * <li>{@code ~/.bvm/current/lib/boxlang-*.jar} — the currently active BVM version</li>
 * <li>{@code ~/.bvm/versions/<version>/lib/boxlang-*.jar} — each installed BVM version,
 * most-recently-modified first</li>
 * <li>{@code ~/.local/boxlang/lib/boxlang-*.jar} — common manual install location</li>
 * </ol>
 *
 * <p>
 * This class is read-only and never downloads or modifies anything.
 */
public final class BoxLangSystemRuntimeLocator {

	private static final Logger		LOG			= Logger.getInstance( BoxLangSystemRuntimeLocator.class );

	/** Matches {@code boxlang-<version>.jar} — captures the version string. */
	private static final Pattern	JAR_PATTERN	= Pattern.compile( "^boxlang-(.+)\\.jar$" );

	private BoxLangSystemRuntimeLocator() {
	}

	/**
	 * Holds a located system BoxLang JAR and its parsed version string.
	 */
	public static final class SystemRuntimeInfo {

		/** The path to the boxlang-&lt;version&gt;.jar file. */
		public final Path	jarPath;

		/**
		 * The version string extracted from the JAR filename, e.g. {@code "1.11.0"} or
		 * {@code "1.10.0-snapshot"}.
		 */
		public final String	version;

		SystemRuntimeInfo( Path jarPath, String version ) {
			this.jarPath	= jarPath;
			this.version	= version;
		}
	}

	/**
	 * Returns the most appropriate system-installed BoxLang runtime, or {@code null} if none is
	 * found.
	 */
	@Nullable
	public static SystemRuntimeInfo findBest() {
		// 1. BVM current
		SystemRuntimeInfo bvmCurrent = findInBvmCurrent();
		if ( bvmCurrent != null ) {
			return bvmCurrent;
		}

		// 2. BVM versions (most recently modified)
		SystemRuntimeInfo bvmVersion = findBestBvmVersion();
		if ( bvmVersion != null ) {
			return bvmVersion;
		}

		// 3. ~/.local/boxlang
		SystemRuntimeInfo localInstall = findInDirectory( Path.of( System.getProperty( "user.home" ), ".local", "boxlang", "lib" ) );
		if ( localInstall != null ) {
			return localInstall;
		}

		return null;
	}

	/**
	 * Returns all system-installed BoxLang runtimes found across all known locations,
	 * most-preferred first.
	 */
	public static List<SystemRuntimeInfo> findAll() {
		List<SystemRuntimeInfo>	results		= new ArrayList<>();

		SystemRuntimeInfo		bvmCurrent	= findInBvmCurrent();
		if ( bvmCurrent != null ) {
			results.add( bvmCurrent );
		}

		Path bvmVersionsDir = Path.of( System.getProperty( "user.home" ), ".bvm", "versions" );
		if ( Files.isDirectory( bvmVersionsDir ) ) {
			try ( Stream<Path> versionDirs = Files.list( bvmVersionsDir ) ) {
				versionDirs
				    .filter( Files::isDirectory )
				    .filter( d -> !"latest".equals( d.getFileName().toString() ) ) // skip symlink alias
				    .sorted( ( a, b ) -> {
					    try {
						    return Files.getLastModifiedTime( b ).compareTo( Files.getLastModifiedTime( a ) );
					    } catch ( IOException e ) {
						    return 0;
					    }
				    } )
				    .forEach( versionDir -> {
					    SystemRuntimeInfo info = findInDirectory( versionDir.resolve( "lib" ) );
					    if ( info != null && results.stream().noneMatch( r -> r.version.equals( info.version ) ) ) {
						    results.add( info );
					    }
				    } );
			} catch ( IOException e ) {
				LOG.warn( "Failed to list BVM versions directory", e );
			}
		}

		SystemRuntimeInfo localInstall = findInDirectory( Path.of( System.getProperty( "user.home" ), ".local", "boxlang", "lib" ) );
		if ( localInstall != null && results.stream().noneMatch( r -> r.jarPath.equals( localInstall.jarPath ) ) ) {
			results.add( localInstall );
		}

		return results;
	}

	// -------------------------------------------------------------------------
	// Private helpers
	// -------------------------------------------------------------------------

	@Nullable
	private static SystemRuntimeInfo findInBvmCurrent() {
		Path bvmCurrent = Path.of( System.getProperty( "user.home" ), ".bvm", "current", "lib" );
		return findInDirectory( bvmCurrent );
	}

	@Nullable
	private static SystemRuntimeInfo findBestBvmVersion() {
		Path bvmVersionsDir = Path.of( System.getProperty( "user.home" ), ".bvm", "versions" );
		if ( !Files.isDirectory( bvmVersionsDir ) ) {
			return null;
		}

		try ( Stream<Path> versionDirs = Files.list( bvmVersionsDir ) ) {
			return versionDirs
			    .filter( Files::isDirectory )
			    .filter( d -> !"latest".equals( d.getFileName().toString() ) ) // skip symlink alias
			    .sorted( ( a, b ) -> {
				    try {
					    return Files.getLastModifiedTime( b ).compareTo( Files.getLastModifiedTime( a ) );
				    } catch ( IOException e ) {
					    return 0;
				    }
			    } )
			    .map( versionDir -> findInDirectory( versionDir.resolve( "lib" ) ) )
			    .filter( info -> info != null )
			    .findFirst()
			    .orElse( null );
		} catch ( IOException e ) {
			LOG.warn( "Failed to list BVM versions", e );
			return null;
		}
	}

	/**
	 * Finds the first {@code boxlang-*.jar} in the given directory that is NOT a miniserver JAR.
	 */
	@Nullable
	static SystemRuntimeInfo findInDirectory( @Nullable Path libDir ) {
		if ( libDir == null || !Files.isDirectory( libDir ) ) {
			return null;
		}

		try ( Stream<Path> files = Files.list( libDir ) ) {
			return files
			    .filter( f -> {
				    String name = f.getFileName().toString();
				    // Exclude miniserver and other auxiliary JARs
				    return name.startsWith( "boxlang-" ) && name.endsWith( ".jar" )
				        && !name.contains( "miniserver" );
			    } )
			    .map( jar -> {
				    Matcher m = JAR_PATTERN.matcher( jar.getFileName().toString() );
				    if ( m.matches() ) {
					    return new SystemRuntimeInfo( jar, m.group( 1 ) );
				    }
				    return null;
			    } )
			    .filter( info -> info != null )
			    .findFirst()
			    .orElse( null );
		} catch ( IOException e ) {
			LOG.warn( "Failed to list lib directory: " + libDir, e );
			return null;
		}
	}
}
