package com.ortussolutions.intellijboxlang.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

public final class DebuggerModuleResolver {

	private DebuggerModuleResolver() {
	}

	/**
	 * Resolves the debugger module location within the given BoxLang home directory.
	 * The debugger module is expected to be at {boxLangHome}/modules/bx-debugger/
	 */
	public static DebuggerModuleInfo resolve( Path boxLangHome, String requestedVersion ) {
		DebuggerModuleInfo info = new DebuggerModuleInfo();
		info.requestedVersion = requestedVersion;

		if ( boxLangHome == null ) {
			info.needsDownload = true;
			return info;
		}

		Path modulesDir = boxLangHome.resolve( "modules" );
		info.modulePath		= modulesDir.resolve( "bx-debugger" );
		info.boxJsonPath	= findBoxJson( info.modulePath );
		info.needsDownload	= info.boxJsonPath == null;
		return info;
	}

	/**
	 * Finds the debugger JAR file within the module's libs directory.
	 * Returns null if not found.
	 */
	public static Path findDebuggerJar( Path modulePath ) {
		if ( modulePath == null || !Files.exists( modulePath ) ) {
			return null;
		}

		Path libsDir = modulePath.resolve( "libs" );
		if ( !Files.exists( libsDir ) ) {
			return null;
		}

		try ( Stream<Path> stream = Files.list( libsDir ) ) {
			Optional<Path> jarPath = stream
			    .filter( path -> {
				    String name = path.getFileName().toString();
				    return name.startsWith( "bx-debugger" ) && name.endsWith( ".jar" );
			    } )
			    .findFirst();
			return jarPath.orElse( null );
		} catch ( IOException ignored ) {
			return null;
		}
	}

	private static Path findBoxJson( Path moduleRoot ) {
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
}
