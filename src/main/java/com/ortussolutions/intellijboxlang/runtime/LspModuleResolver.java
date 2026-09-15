package com.ortussolutions.intellijboxlang.runtime;

import com.ortussolutions.intellijboxlang.settings.BoxLangResolvedSettings;
import com.ortussolutions.intellijboxlang.settings.BoxLangStoragePaths;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;
import org.jetbrains.annotations.Nullable;

public final class LspModuleResolver {

	private LspModuleResolver() {
	}

	public static LspModuleInfo resolve( BoxLangResolvedSettings settings ) {
		return resolveForVersion( settings.lspVersion );
	}

	public static LspModuleInfo resolveForVersion( String lspVersion ) {
		LspModuleInfo info = new LspModuleInfo();
		info.requestedVersion = lspVersion;

		if ( info.requestedVersion == null || info.requestedVersion.isBlank() ) {
			info.needsDownload = true;
			return info;
		}

		// 1. Check the flat global BoxLang home install (new location)
		Path globalModulePath = BoxLangStoragePaths.getUserBoxLangHome().resolve( "modules" ).resolve( "bx-lsp" );
		info.modulePath		= globalModulePath;
		info.boxJsonPath	= findBoxJson( globalModulePath );
		if ( info.boxJsonPath != null ) {
			info.needsDownload = false;
			return info;
		}

		// 2. Legacy: check the old version-nested cache path
		Path legacyRoot = BoxLangStoragePaths.getLspCacheRoot().resolve( info.requestedVersion );
		info.modulePath		= legacyRoot.resolve( "bx-lsp" );
		info.boxJsonPath	= findBoxJson( info.modulePath );
		info.needsDownload	= info.boxJsonPath == null;
		return info;
	}

	/**
	 * Finds any installed LSP version.
	 * Checks the flat global BoxLang home first, then falls back to the legacy version-nested cache.
	 * Returns null if none found.
	 */
	@Nullable
	public static LspModuleInfo findAnyInstalledVersion() {
		// 1. Check flat global BoxLang home install (new location)
		Path	globalModulePath	= BoxLangStoragePaths.getUserBoxLangHome().resolve( "modules" ).resolve( "bx-lsp" );
		Path	boxJson				= findBoxJson( globalModulePath );
		if ( boxJson != null ) {
			LspModuleInfo info = new LspModuleInfo();
			info.modulePath		= globalModulePath;
			info.boxJsonPath	= boxJson;
			info.needsDownload	= false;
			return info;
		}

		// 2. Legacy: scan the old version-nested cache directory
		Path cacheRoot = BoxLangStoragePaths.getLspCacheRoot();
		if ( !Files.exists( cacheRoot ) ) {
			return null;
		}

		try ( Stream<Path> versionDirs = Files.list( cacheRoot ) ) {
			Optional<Path> latestVersion = versionDirs
			    .filter( Files::isDirectory )
			    .filter( dir -> {
				    Path modulePath = dir.resolve( "bx-lsp" );
				    return findBoxJson( modulePath ) != null;
			    } )
			    .max( Comparator.comparingLong( dir -> {
				    try {
					    return Files.getLastModifiedTime( dir ).toMillis();
				    } catch ( IOException e ) {
					    return 0L;
				    }
			    } ) );

			if ( latestVersion.isPresent() ) {
				String version = latestVersion.get().getFileName().toString();
				return resolveForVersion( version );
			}
		} catch ( IOException ignored ) {
			// Fall through to return null
		}

		return null;
	}

	@Nullable
	public static Path findBoxJson( Path moduleRoot ) {
		if ( moduleRoot == null || !Files.exists( moduleRoot ) ) {
			return null;
		}
		try ( Stream<Path> stream = Files.walk( moduleRoot ) ) {
			Optional<Path> match = stream
			    .filter( path -> path.getFileName().toString().equals( "box.json" ) )
			    .findFirst();
			return match.orElse( null );
		} catch ( IOException ignored ) {
			return null;
		}
	}

	@Nullable
	public static Path resolveOverrideModulePath( @Nullable Path overridePath ) {
		if ( overridePath == null || !Files.exists( overridePath ) ) {
			return null;
		}
		if ( Files.isRegularFile( overridePath ) ) {
			String fileName = overridePath.getFileName() != null ? overridePath.getFileName().toString() : "";
			if ( "ModuleConfig.bx".equalsIgnoreCase( fileName ) || "box.json".equalsIgnoreCase( fileName ) ) {
				Path parent = overridePath.getParent();
				return isModuleRoot( parent ) ? parent : null;
			}
			return null;
		}
		if ( isModuleRoot( overridePath ) ) {
			return overridePath;
		}
		Path nested = overridePath.resolve( "bx-lsp" );
		return isModuleRoot( nested ) ? nested : null;
	}

	private static boolean isModuleRoot( @Nullable Path path ) {
		return path != null
		    && Files.isDirectory( path )
		    && Files.exists( path.resolve( "box.json" ) )
		    && Files.exists( path.resolve( "ModuleConfig.bx" ) );
	}
}
