package com.ortussolutions.intellijboxlang.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class BoxLangZipExtractor {

	private static final int BUFFER_SIZE = 8192;

	private BoxLangZipExtractor() {
	}

	public static void extract( Path zipFile, Path destination ) throws IOException {
		Files.createDirectories( destination );
		try ( InputStream input = Files.newInputStream( zipFile );
		    ZipInputStream zip = new ZipInputStream( input ) ) {
			ZipEntry entry;
			while ( ( entry = zip.getNextEntry() ) != null ) {
				Path target = destination.resolve( entry.getName() );
				if ( entry.isDirectory() ) {
					Files.createDirectories( target );
				} else {
					Files.createDirectories( target.getParent() );
					Files.copy( zip, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING );
				}
				zip.closeEntry();
			}
		}
	}
}
