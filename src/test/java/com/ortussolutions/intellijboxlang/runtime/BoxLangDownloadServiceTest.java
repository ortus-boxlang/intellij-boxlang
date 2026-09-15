package com.ortussolutions.intellijboxlang.runtime;

import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

public class BoxLangDownloadServiceTest extends BasePlatformTestCase {

	private Path directory;

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		directory = Files.createTempDirectory( "boxlang-download-test" );
	}

	@Override
	protected void tearDown() throws Exception {
		try {
			com.intellij.openapi.util.io.FileUtil.delete( directory.toFile() );
		} finally {
			super.tearDown();
		}
	}

	public void testSuccessfulDownloadReplacesOldFileAndReportsProgress() throws Exception {
		Path					target		= existing();
		EmptyProgressIndicator	indicator	= new EmptyProgressIndicator();
		serve( 200, "new runtime".getBytes( StandardCharsets.UTF_8 ), 11, url -> BoxLangDownloadService.downloadTo( url, target, indicator ) );
		assertEquals( "new runtime", Files.readString( target ) );
		assertEquals( 1.0, indicator.getFraction() );
		assertOnlyTarget();
	}

	public void testHttpFailurePreservesOldFile() throws Exception {
		Path target = existing();
		serve( 503, new byte[ 0 ], 0, url -> {
			try {
				BoxLangDownloadService.downloadTo( url, target, null );
				fail( "Expected HTTP error" );
			} catch ( IOException e ) {
				assertTrue( e.getMessage().contains( "503" ) );
			}
		} );
		assertEquals( "old", Files.readString( target ) );
		assertOnlyTarget();
	}

	public void testTruncatedDownloadPreservesOldFile() throws Exception {
		Path target = existing();
		serve( 200, new byte[] { 1, 2 }, 100, url -> {
			try {
				BoxLangDownloadService.downloadTo( url, target, null );
				fail( "Expected incomplete download" );
			} catch ( IOException expected ) {
			}
		} );
		assertEquals( "old", Files.readString( target ) );
		assertOnlyTarget();
	}

	public void testCancellationPreservesOldFile() throws Exception {
		Path					target		= existing();
		EmptyProgressIndicator	indicator	= new EmptyProgressIndicator();
		indicator.cancel();
		serve( 200, new byte[ 100 ], 100, url -> {
			try {
				BoxLangDownloadService.downloadTo( url, target, indicator );
				fail( "Expected cancellation" );
			} catch ( ProcessCanceledException expected ) {
			}
		} );
		assertEquals( "old", Files.readString( target ) );
		assertOnlyTarget();
	}

	public void testRuntimeRejectsHtmlResponseAndPreservesPreviousJar() throws Exception {
		Path	target	= directory.resolve( "runtime.jar" );
		byte[]	valid	= runtimeJar();
		Files.write( target, valid );
		byte[] html = "<html>Server error</html>".getBytes( StandardCharsets.UTF_8 );
		serve( 200, html, html.length, url -> {
			try {
				BoxLangRuntimeInstaller.installJar( url, target, null );
				fail( "Expected invalid JAR" );
			} catch ( IOException e ) {
				assertTrue( e.getMessage().contains( "valid BoxLang runtime" ) );
			}
		} );
		assertTrue( java.util.Arrays.equals( valid, Files.readAllBytes( target ) ) );
		assertTrue( BoxLangRuntimeInstaller.isValidRuntimeJar( target ) );
		assertOnlyTarget();
	}

	public void testValidRuntimeInstalls() throws Exception {
		Path	target	= existing();
		byte[]	valid	= runtimeJar();
		serve( 200, valid, valid.length, url -> BoxLangRuntimeInstaller.installJar( url, target, null ) );
		assertTrue( BoxLangRuntimeInstaller.isValidRuntimeJar( target ) );
		assertOnlyTarget();
	}

	public void testCompatibleCacheIsUsableWithoutCatalogLookup() throws Exception {
		Path valid = directory.resolve( "boxlang-1.11.0" );
		Files.createDirectories( valid );
		Files.write( valid.resolve( "boxlang-1.11.0.jar" ), runtimeJar() );
		Path broken = directory.resolve( "boxlang-1.12.0" );
		Files.createDirectories( broken );
		Files.writeString( broken.resolve( "boxlang-1.12.0.jar" ), "partial download" );
		var cached = BoxLangRuntimeResolver.findCachedRuntime( directory, "^1.6.0", true );
		assertNotNull( cached );
		assertEquals( "boxlang-1.11.0", cached.resolvedVersion );
		assertNull( BoxLangRuntimeResolver.findCachedRuntime( directory, "1.6.0", false ) );
		assertNotNull( BoxLangRuntimeResolver.findCachedRuntime( directory, "1.11.0", false ) );
	}

	public void testRuntimeCacheRejectsUntrustedVersionPaths() throws Exception {
		for ( String version : new String[] { "../outside", "boxlang-..", "boxlang-1.2.3/../../outside", "boxlang-1.2.3\\..\\outside", "boxlang-1.2.3\"}" } ) {
			try {
				BoxLangRuntimeInstaller.resolveCachedJar( version );
				fail( "Unsafe version accepted: " + version );
			} catch ( IOException expected ) {
				assertTrue( expected.getMessage().contains( "Invalid BoxLang runtime version" ) );
			}
			try {
				BoxLangRuntimeInstaller.installRuntime( version, "https://example.invalid/runtime.jar", null );
				fail( "Unsafe download version accepted: " + version );
			} catch ( IOException expected ) {
				assertTrue( expected.getMessage().contains( "Invalid BoxLang runtime version" ) );
			}
		}
		assertNull( BoxLangRuntimeInstaller.resolveCachedJar( "boxlang-1.2.3" ) );
	}

	private Path existing() throws IOException {
		return Files.writeString( directory.resolve( "runtime.jar" ), "old" );
	}

	private void assertOnlyTarget() throws IOException {
		try ( var paths = Files.list( directory ) ) {
			assertEquals( 1L, paths.count() );
		}
	}

	private byte[] runtimeJar() throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try ( JarOutputStream jar = new JarOutputStream( bytes ) ) {
			jar.putNextEntry( new JarEntry( "ortus/boxlang/runtime/BoxRunner.class" ) );
			jar.write( new byte[] { 1, 2, 3 } );
			jar.closeEntry();
		}
		return bytes.toByteArray();
	}

	@FunctionalInterface
	private interface Request {

		void run( URL url ) throws Exception;
	}

	private void serve( int status, byte[] body, int length, Request request ) throws Exception {
		try ( ServerSocket listener = new ServerSocket( 0, 1, java.net.InetAddress.getLoopbackAddress() ) ) {
			CompletableFuture<Void> response = CompletableFuture.runAsync( () -> {
				try ( var socket = listener.accept() ) {
					var		reader	= new java.io.BufferedReader( new java.io.InputStreamReader( socket.getInputStream(), StandardCharsets.US_ASCII ) );
					String	line;
					while ( ( line = reader.readLine() ) != null && !line.isEmpty() ) {
					}
					socket.getOutputStream().write( ( "HTTP/1.1 " + status + " Result\r\nContent-Length: " + length + "\r\nConnection: close\r\n\r\n" )
					    .getBytes( StandardCharsets.US_ASCII ) );
					socket.getOutputStream().write( body );
				} catch ( IOException e ) {
					throw new java.io.UncheckedIOException( e );
				}
			} );
			request.run( new URL( "http://localhost:" + listener.getLocalPort() + "/runtime.jar" ) );
			response.get( 5, java.util.concurrent.TimeUnit.SECONDS );
		}
	}
}
