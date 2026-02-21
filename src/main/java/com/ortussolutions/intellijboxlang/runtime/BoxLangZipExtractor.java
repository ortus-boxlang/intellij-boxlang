package com.ortussolutions.intellijboxlang.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class BoxLangZipExtractor {

	private BoxLangZipExtractor() {
	}

	public static void extract( Path zipFile, Path destination ) throws IOException {
		Path normalizedDestination = destination.toAbsolutePath().normalize();
		Files.createDirectories( normalizedDestination );
		try ( InputStream input = Files.newInputStream( zipFile );
		    ZipInputStream zip = new ZipInputStream( input ) ) {
			ZipEntry entry;
			while ( ( entry = zip.getNextEntry() ) != null ) {
				Path target = resolveEntryTarget( normalizedDestination, entry );
				if ( entry.isDirectory() ) {
					Files.createDirectories( target );
				} else {
					Path parent = target.getParent();
					if ( parent != null ) {
						Files.createDirectories( parent );
					}
					Files.copy( zip, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING );
				}
				zip.closeEntry();
			}
		}
	}

	private static Path resolveEntryTarget( Path normalizedDestination, ZipEntry entry ) throws IOException {
		Path target = normalizedDestination.resolve( entry.getName() ).normalize();
		if ( !target.startsWith( normalizedDestination ) ) {
			throw new IOException( "Zip entry escapes destination: " + entry.getName() );
		}
		return target;
	}
}
