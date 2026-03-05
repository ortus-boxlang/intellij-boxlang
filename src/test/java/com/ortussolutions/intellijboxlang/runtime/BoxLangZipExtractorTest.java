package com.ortussolutions.intellijboxlang.runtime;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class BoxLangZipExtractorTest extends BasePlatformTestCase {

	public void testExtractRejectsParentTraversalEntry() throws Exception {
		Path tempDir = Files.createTempDirectory( "boxlang-zip-slip-parent" );
		try {
			Path archive = tempDir.resolve( "archive.zip" );
			writeZip( archive, Map.of( "../outside.txt", "hacked" ) );

			try {
				BoxLangZipExtractor.extract( archive, tempDir.resolve( "dest" ) );
				fail( "Expected IOException for traversal entry" );
			} catch ( IOException e ) {
				assertTrue( e.getMessage().contains( "escapes destination" ) );
			}

			assertFalse( Files.exists( tempDir.resolve( "outside.txt" ) ) );
		} finally {
			deleteRecursively( tempDir );
		}
	}

	public void testExtractRejectsAbsolutePathEntry() throws Exception {
		Path tempDir = Files.createTempDirectory( "boxlang-zip-slip-absolute" );
		try {
			Path archive = tempDir.resolve( "archive.zip" );
			writeZip( archive, Map.of( "/absolute.txt", "hacked" ) );

			try {
				BoxLangZipExtractor.extract( archive, tempDir.resolve( "dest" ) );
				fail( "Expected IOException for absolute entry" );
			} catch ( IOException e ) {
				assertTrue( e.getMessage().contains( "escapes destination" ) );
			}
		} finally {
			deleteRecursively( tempDir );
		}
	}

	public void testExtractWritesValidEntriesInsideDestination() throws Exception {
		Path tempDir = Files.createTempDirectory( "boxlang-zip-valid" );
		try {
			Path archive = tempDir.resolve( "archive.zip" );
			writeZip( archive, Map.of( "nested/file.txt", "hello" ) );

			Path destination = tempDir.resolve( "dest" );
			BoxLangZipExtractor.extract( archive, destination );

			Path extracted = destination.resolve( "nested/file.txt" );
			assertTrue( Files.exists( extracted ) );
			assertEquals( "hello", Files.readString( extracted ) );
		} finally {
			deleteRecursively( tempDir );
		}
	}

	private static void writeZip( Path archive, Map<String, String> entries ) throws IOException {
		try ( ZipOutputStream out = new ZipOutputStream( Files.newOutputStream( archive ) ) ) {
			for ( Map.Entry<String, String> entry : entries.entrySet() ) {
				out.putNextEntry( new ZipEntry( entry.getKey() ) );
				out.write( entry.getValue().getBytes( StandardCharsets.UTF_8 ) );
				out.closeEntry();
			}
		}
	}

	private static void deleteRecursively( Path root ) throws IOException {
		if ( !Files.exists( root ) ) {
			return;
		}
		try ( var walk = Files.walk( root ) ) {
			walk.sorted( Comparator.reverseOrder() )
			    .forEach( path -> {
				    try {
					    Files.deleteIfExists( path );
				    } catch ( IOException ignored ) {
				    }
			    } );
		}
	}
}
