package com.ortussolutions.intellijboxlang.runtime;

import com.intellij.openapi.project.Project;
import com.ortussolutions.intellijboxlang.settings.BoxLangResolvedSettings;
import java.io.IOException;
import java.nio.file.Path;

public final class BoxLangRuntimeResolver {

	private BoxLangRuntimeResolver() {
	}

	public static BoxLangRuntimeInfo resolve( Project project, BoxLangResolvedSettings settings ) {
		BoxLangRuntimeInfo	info				= new BoxLangRuntimeInfo();

		String				requestedVersion	= settings.boxLangVersion;
		if ( settings.useBvmrc ) {
			String bvmrcVersion = BoxLangBvmrcService.findVersion( project );
			if ( bvmrcVersion != null ) {
				requestedVersion = bvmrcVersion;
			}
		}

		return resolveRuntime( requestedVersion, settings.boxLangJarPath, false );
	}

	public static BoxLangRuntimeInfo resolveLspRuntime( String requestedVersion, boolean treatAsMinimum ) {
		return resolveRuntime( requestedVersion, null, treatAsMinimum );
	}

	private static BoxLangRuntimeInfo resolveRuntime( String requestedVersion, String jarPathOverride, boolean treatAsMinimum ) {
		BoxLangRuntimeInfo info = new BoxLangRuntimeInfo();
		info.requestedVersion	= requestedVersion;
		info.resolvedVersion	= requestedVersion;

		if ( jarPathOverride != null ) {
			info.jarPath		= jarPathOverride;
			info.needsDownload	= false;
			return info;
		}

		if ( requestedVersion == null || requestedVersion.isBlank() ) {
			// No version requested — check system install before requiring a download
			BoxLangSystemRuntimeLocator.SystemRuntimeInfo systemRuntime = BoxLangSystemRuntimeLocator.findBest();
			if ( systemRuntime != null ) {
				info.jarPath			= systemRuntime.jarPath.toString();
				info.resolvedVersion	= systemRuntime.version;
				info.needsDownload		= false;
				return info;
			}
			info.needsDownload = true;
			return info;
		}

		// A compatible installed runtime must remain usable when the catalog is offline.
		BoxLangRuntimeSelection cached = findCachedRuntime( com.ortussolutions.intellijboxlang.settings.BoxLangStoragePaths.getRuntimeCacheRoot(),
		    requestedVersion, treatAsMinimum );
		if ( cached != null ) {
			info.jarPath			= cached.jarPath.toString();
			info.resolvedVersion	= cached.resolvedVersion;
			info.needsDownload		= false;
			return info;
		}
		for ( var installed : BoxLangSystemRuntimeLocator.findAll() ) {
			if ( versionSatisfies( installed.version, requestedVersion, treatAsMinimum ) && BoxLangRuntimeInstaller.isValidRuntimeJar( installed.jarPath ) ) {
				info.jarPath			= installed.jarPath.toString();
				info.resolvedVersion	= installed.version;
				info.needsDownload		= false;
				return info;
			}
		}

		try {
			BoxLangVersionInfo versionInfo = treatAsMinimum
			    ? BoxLangVersionCatalog.resolveLatestAtLeastInfo( requestedVersion )
			    : BoxLangVersionCatalog.resolveVersionInfo( requestedVersion );
			if ( versionInfo == null ) {
				info.needsDownload = true;
				return info;
			}
			info.resolvedVersion	= versionInfo.name();
			info.downloadUrl		= versionInfo.downloadUrl();
			BoxLangRuntimeSelection selection = BoxLangRuntimeInstaller.resolveCachedJar( info.resolvedVersion );
			if ( selection != null ) {
				info.jarPath			= selection.jarPath.toString();
				info.resolvedVersion	= selection.resolvedVersion;
				info.needsDownload		= false;
				return info;
			}

		} catch ( IOException ignored ) {
			info.needsDownload = true;
			return info;
		}

		info.needsDownload = true;
		return info;
	}

	static BoxLangRuntimeSelection findCachedRuntime( Path cacheRoot, String requestedVersion, boolean treatAsMinimum ) {
		if ( !java.nio.file.Files.isDirectory( cacheRoot ) )
			return null;
		try ( var directories = java.nio.file.Files.list( cacheRoot ) ) {
			var candidate = directories.filter( java.nio.file.Files::isDirectory )
			    .filter( dir -> versionSatisfies( dir.getFileName().toString(), requestedVersion, treatAsMinimum ) )
			    .filter( dir -> BoxLangRuntimeInstaller.isValidRuntimeJar( dir.resolve( dir.getFileName() + ".jar" ) ) )
			    .max( ( a, b ) -> compareVersions( a.getFileName().toString().replaceFirst( "^boxlang-", "" ),
			        b.getFileName().toString().replaceFirst( "^boxlang-", "" ) ) );
			if ( candidate.isEmpty() )
				return null;
			var result = new BoxLangRuntimeSelection();
			result.resolvedVersion	= candidate.get().getFileName().toString();
			result.jarPath			= candidate.get().resolve( result.resolvedVersion + ".jar" );
			return result;
		} catch ( IOException ignored ) {
			return null;
		}
	}

	/**
	 * Returns true if the installed version satisfies the requested version constraint.
	 * When {@code treatAsMinimum} is true, the installed version must be &gt;= requested.
	 * When false, it must match exactly (ignoring the "boxlang-" prefix).
	 *
	 * <p>
	 * Version comparison is done numerically per segment (e.g. 1.11.0 &gt; 1.6.0) rather than
	 * lexicographically, which would incorrectly treat "1.11" as less than "1.6".
	 */
	static boolean versionSatisfies( String installedVersion, String requestedVersion, boolean treatAsMinimum ) {
		if ( installedVersion == null || requestedVersion == null ) {
			return false;
		}
		// Normalise: strip "boxlang-" prefix from both sides
		String	installed	= installedVersion.startsWith( "boxlang-" ) ? installedVersion.substring( 8 ) : installedVersion;
		String	requested	= requestedVersion.startsWith( "boxlang-" ) ? requestedVersion.substring( 8 ) : requestedVersion;

		try {
			var	actual		= new com.vdurmont.semver4j.Semver( installed, com.vdurmont.semver4j.Semver.SemverType.NPM );
			var	required	= BoxLangVersionCatalog.parseMinimumVersion( requested );
			return required != null && ( treatAsMinimum ? actual.isGreaterThanOrEqualTo( required ) : actual.isEquivalentTo( required ) );
		} catch ( RuntimeException invalidVersion ) {
			return false;
		}
	}

	/**
	 * Compares two dotted-numeric version strings numerically, segment by segment.
	 * Non-numeric segments fall back to string comparison for that segment.
	 * Returns negative if {@code a < b}, zero if equal, positive if {@code a > b}.
	 */
	static int compareVersions( String a, String b ) {
		// Strip any build-metadata / pre-release suffix for numeric comparison
		String[]	partsA	= a.split( "[.\\-]" );
		String[]	partsB	= b.split( "[.\\-]" );
		int			len		= Math.max( partsA.length, partsB.length );
		for ( int i = 0; i < len; i++ ) {
			String	segA	= i < partsA.length ? partsA[ i ] : "0";
			String	segB	= i < partsB.length ? partsB[ i ] : "0";
			int		cmp;
			try {
				cmp = Integer.compare( Integer.parseInt( segA ), Integer.parseInt( segB ) );
			} catch ( NumberFormatException e ) {
				cmp = segA.compareTo( segB );
			}
			if ( cmp != 0 ) {
				return cmp;
			}
		}
		return 0;
	}
}
