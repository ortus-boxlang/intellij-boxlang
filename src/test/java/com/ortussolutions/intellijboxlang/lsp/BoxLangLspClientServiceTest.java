package com.ortussolutions.intellijboxlang.lsp;

import com.intellij.testFramework.LightVirtualFile;
import java.nio.file.Path;
import java.util.List;
import junit.framework.TestCase;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

public class BoxLangLspClientServiceTest extends TestCase {

	public void testSafeToUri_returnsNullForLightVirtualFile() {
		LightVirtualFile file = new LightVirtualFile( "BoxLang Debug Console", "println('hi')" );
		assertNull( BoxLangLspClientService.safeToUri( file ) );
	}

	public void testSafeToUri_handlesNullInput() {
		assertNull( BoxLangLspClientService.safeToUri( null ) );
	}

	public void testToModulesDirectoryEnv_formatsCommaSeparatedPaths() {
		Path	first	= Path.of( "/tmp/modules-a" );
		Path	second	= Path.of( "/tmp/modules-b" );
		assertEquals(
		    "/tmp/modules-a,/tmp/modules-b",
		    BoxLangLspClientService.toModulesDirectoryEnv( List.of( first, second ) )
		);
	}

	public void testHasContextChanged_onlyWhenHashChanges() {
		BoxLangLspAppContext	first	= new BoxLangLspAppContext(
		    Path.of( "/tmp/app" ),
		    java.util.Map.of( "api", "/tmp/app/api" ),
		    List.of( Path.of( "/tmp/app/modules_app" ) ),
		    "same-hash"
		);
		BoxLangLspAppContext	second	= new BoxLangLspAppContext(
		    Path.of( "/tmp/app" ),
		    java.util.Map.of( "api", "/tmp/app/api" ),
		    List.of( Path.of( "/tmp/app/modules_app" ) ),
		    "same-hash"
		);
		BoxLangLspAppContext	changed	= new BoxLangLspAppContext(
		    Path.of( "/tmp/app" ),
		    java.util.Map.of( "api", "/tmp/app/api2" ),
		    List.of( Path.of( "/tmp/app/modules_app" ) ),
		    "different-hash"
		);

		assertFalse( BoxLangLspClientService.hasContextChanged( first, second ) );
		assertTrue( BoxLangLspClientService.hasContextChanged( first, changed ) );
		assertTrue( BoxLangLspClientService.hasContextChanged( null, changed ) );
	}

	public void testFlattenDefinitionResult_mapsLocationLinksToLocations() {
		LocationLink link = new LocationLink();
		link.setTargetUri( "file:///tmp/Example.cfc" );
		link.setTargetRange( new Range( new Position( 4, 2 ), new Position( 4, 15 ) ) );

		List<Location> flattened = BoxLangLspClientService.flattenDefinitionResult( Either.forRight( List.of( link ) ) );

		assertEquals( 1, flattened.size() );
		assertEquals( "file:///tmp/Example.cfc", flattened.getFirst().getUri() );
		assertEquals( 4, flattened.getFirst().getRange().getStart().getLine() );
		assertEquals( 2, flattened.getFirst().getRange().getStart().getCharacter() );
	}

	public void testFlattenDefinitionResult_preservesDirectLocations() {
		Location location = new Location();
		location.setUri( "file:///tmp/Direct.cfc" );
		location.setRange( new Range( new Position( 1, 1 ), new Position( 1, 5 ) ) );

		List<Location> flattened = BoxLangLspClientService.flattenDefinitionResult( Either.forLeft( List.of( location ) ) );

		assertEquals( 1, flattened.size() );
		assertEquals( "file:///tmp/Direct.cfc", flattened.getFirst().getUri() );
	}
}
