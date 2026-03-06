package com.ortussolutions.intellijboxlang.runtime;

import junit.framework.TestCase;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class BoxLangSystemRuntimeLocatorTest extends TestCase {

	public void testFindBest_usesUserBoxLangHomeInstall() throws Exception {
		String	originalUserHome	= System.getProperty( "user.home" );
		Path	tempHome			= Files.createTempDirectory( "boxlang-user-home-" );
		try {
			System.setProperty( "user.home", tempHome.toString() );

			Path jarPath = tempHome.resolve( ".boxlang" ).resolve( "lib" ).resolve( "boxlang-1.2.3.jar" );
			Files.createDirectories( jarPath.getParent() );
			Files.writeString( jarPath, "test" );

			BoxLangSystemRuntimeLocator.SystemRuntimeInfo info = BoxLangSystemRuntimeLocator.findBest();
			assertNotNull( info );
			assertEquals( jarPath.toAbsolutePath().normalize(), info.jarPath.toAbsolutePath().normalize() );
			assertEquals( "1.2.3", info.version );
		} finally {
			if ( originalUserHome != null ) {
				System.setProperty( "user.home", originalUserHome );
			}
		}
	}

	public void testFindAll_includesUserBoxLangHomeInstall() throws Exception {
		String	originalUserHome	= System.getProperty( "user.home" );
		Path	tempHome			= Files.createTempDirectory( "boxlang-user-home-all-" );
		try {
			System.setProperty( "user.home", tempHome.toString() );

			Path jarPath = tempHome.resolve( ".boxlang" ).resolve( "lib" ).resolve( "boxlang-9.9.9.jar" );
			Files.createDirectories( jarPath.getParent() );
			Files.writeString( jarPath, "test" );

			List<BoxLangSystemRuntimeLocator.SystemRuntimeInfo> all = BoxLangSystemRuntimeLocator.findAll();
			assertTrue( all.stream().anyMatch( info -> info.jarPath.toAbsolutePath().normalize().equals( jarPath.toAbsolutePath().normalize() )
			    && "9.9.9".equals( info.version ) ) );
		} finally {
			if ( originalUserHome != null ) {
				System.setProperty( "user.home", originalUserHome );
			}
		}
	}
}
