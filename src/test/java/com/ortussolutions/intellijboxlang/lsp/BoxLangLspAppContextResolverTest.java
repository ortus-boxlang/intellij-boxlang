package com.ortussolutions.intellijboxlang.lsp;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import junit.framework.TestCase;

public class BoxLangLspAppContextResolverTest extends TestCase {

	public void testFindNearestApplicationRoot_prefersNearestAncestor() throws IOException {
		Path projectRoot = Files.createTempDirectory( "boxlang-lsp-root" );
		try {
			Path	appRoot		= projectRoot.resolve( "app" );
			Path	nestedApp	= appRoot.resolve( "subapp" );
			Files.createDirectories( nestedApp.resolve( "handlers" ) );
			Files.writeString( appRoot.resolve( "Application.bx" ), "component {}", StandardCharsets.UTF_8 );
			Files.writeString( nestedApp.resolve( "Application.bx" ), "component {}", StandardCharsets.UTF_8 );
			Path file = nestedApp.resolve( "handlers/ExampleHandler.cfc" );
			Files.writeString( file, "component {}", StandardCharsets.UTF_8 );

			Path resolved = BoxLangLspAppContextResolver.findNearestApplicationRoot( file, projectRoot );
			assertEquals( nestedApp.toAbsolutePath().normalize(), resolved );
		} finally {
			deleteRecursively( projectRoot );
		}
	}

	public void testFindNearestApplicationRoot_detectsApplicationCfc() throws IOException {
		Path projectRoot = Files.createTempDirectory( "boxlang-lsp-root-cfc" );
		try {
			Path	appRoot		= projectRoot.resolve( "app" );
			Path	nestedApp	= appRoot.resolve( "tests" );
			Files.createDirectories( nestedApp.resolve( "specs" ) );
			Files.writeString( nestedApp.resolve( "Application.cfc" ), "component {}", StandardCharsets.UTF_8 );
			Path file = nestedApp.resolve( "specs/ExampleSpec.cfc" );
			Files.writeString( file, "component {}", StandardCharsets.UTF_8 );

			Path resolved = BoxLangLspAppContextResolver.findNearestApplicationRoot( file, projectRoot );
			assertEquals( nestedApp.toAbsolutePath().normalize(), resolved );
		} finally {
			deleteRecursively( projectRoot );
		}
	}

	public void testResolve_extractsLiteralMappingsAndResolvesMappedClass() throws IOException {
		Path projectRoot = Files.createTempDirectory( "boxlang-lsp-mappings" );
		try {
			Path appRoot = projectRoot.resolve( "app" );
			Files.createDirectories( appRoot.resolve( "modules_app/api-trip/handlers" ) );
			Files.createDirectories( appRoot.resolve( "shared" ) );
			Files.createDirectories( appRoot.resolve( "repositories" ) );
			Files.createDirectories( projectRoot.resolve( "extra" ) );
			Files.writeString(
			    appRoot.resolve( "Application.bx" ),
			    """
			    component {
			        this.mappings = {
			            "/api": "./modules_app/api-trip",
			            "shared": expandPath("/shared")
			        };
			        this.mappings["extra"] = "../extra";
			        registerMapping("repo", expandPath("/repositories"));
			    }
			    """,
			    StandardCharsets.UTF_8
			);
			Files.writeString( appRoot.resolve( "modules_app/api-trip/handlers/BaseHandler.cfc" ), "component {}", StandardCharsets.UTF_8 );
			Path sourceFile = appRoot.resolve( "handlers/ChildHandler.cfc" );
			Files.createDirectories( sourceFile.getParent() );
			Files.writeString( sourceFile, "component extends=\"api.handlers.BaseHandler\" {}", StandardCharsets.UTF_8 );

			BoxLangLspAppContextResolver	resolver	= new BoxLangLspAppContextResolver();
			BoxLangLspAppContext			context		= resolver.resolve( projectRoot, sourceFile, null );

			assertEquals(
			    appRoot.resolve( "modules_app/api-trip" ).toAbsolutePath().normalize().toString(),
			    context.mappings().get( "api" )
			);
			assertEquals( appRoot.resolve( "shared" ).toAbsolutePath().normalize().toString(), context.mappings().get( "shared" ) );
			assertEquals( projectRoot.resolve( "extra" ).toAbsolutePath().normalize().toString(), context.mappings().get( "extra" ) );
			assertEquals( appRoot.resolve( "repositories" ).toAbsolutePath().normalize().toString(), context.mappings().get( "repo" ) );
			assertTrue( resolver.canResolveMappedClassReference( context, "api.handlers.BaseHandler" ) );
			assertFalse( resolver.canResolveMappedClassReference( context, "api.handlers.DoesNotExist" ) );
		} finally {
			deleteRecursively( projectRoot );
		}
	}

	public void testResolve_mergesAndDedupesModuleDirectories() throws IOException {
		Path projectRoot = Files.createTempDirectory( "boxlang-lsp-modules" );
		try {
			Path appRoot = projectRoot.resolve( "app" );
			Files.createDirectories( appRoot.resolve( "modules_app" ) );
			Files.createDirectories( appRoot.resolve( "modules" ) );
			Files.createDirectories( appRoot.resolve( "modules_custom" ) );
			Files.createDirectories( appRoot.resolve( "modules_more" ) );
			Files.createDirectories( appRoot.resolve( "modules_manual" ) );
			Files.writeString(
			    appRoot.resolve( "Application.bx" ),
			    """
			    component {
			        this.modulesDirectory = [ "modules_custom", "modules_custom" ];
			        registerModuleDirectory( "/modules_more" );
			    }
			    """,
			    StandardCharsets.UTF_8
			);
			Path sourceFile = appRoot.resolve( "handlers/Sample.cfc" );
			Files.createDirectories( sourceFile.getParent() );
			Files.writeString( sourceFile, "component {}", StandardCharsets.UTF_8 );

			BoxLangLspAppContextResolver	resolver	= new BoxLangLspAppContextResolver();
			BoxLangLspAppContext			context		= resolver.resolve(
			    projectRoot,
			    sourceFile,
			    "modules_custom, modules_manual, modules_manual"
			);
			List<Path>						dirs		= context.moduleDirectories();

			assertTrue( dirs.contains( appRoot.resolve( "modules_app" ).toAbsolutePath().normalize() ) );
			assertTrue( dirs.contains( appRoot.resolve( "modules" ).toAbsolutePath().normalize() ) );
			assertTrue( dirs.contains( appRoot.resolve( "modules_custom" ).toAbsolutePath().normalize() ) );
			assertTrue( dirs.contains( appRoot.resolve( "modules_more" ).toAbsolutePath().normalize() ) );
			assertTrue( dirs.contains( appRoot.resolve( "modules_manual" ).toAbsolutePath().normalize() ) );
			assertEquals( dirs.size(), dirs.stream().distinct().count() );
		} finally {
			deleteRecursively( projectRoot );
		}
	}

	public void testResolve_includesProjectDotBoxlangModulesMappings() throws IOException {
		Path projectRoot = Files.createTempDirectory( "boxlang-lsp-dot-boxlang" );
		try {
			Path appRoot = projectRoot.resolve( "app" );
			Files.createDirectories( appRoot.resolve( "handlers" ) );
			Files.writeString( appRoot.resolve( "Application.bx" ), "component {}", StandardCharsets.UTF_8 );

			Path testboxRoot = projectRoot.resolve( ".boxlang/modules/testbox" );
			Files.createDirectories( testboxRoot.resolve( "system" ) );
			Files.writeString(
			    testboxRoot.resolve( "ModuleConfig.bx" ),
			    """
			    component {
			        this.mapping = "testbox";
			    }
			    """,
			    StandardCharsets.UTF_8
			);
			Files.writeString( testboxRoot.resolve( "system/BaseSpec.cfc" ), "component {}", StandardCharsets.UTF_8 );

			Path sourceFile = appRoot.resolve( "handlers/Spec.cfc" );
			Files.writeString( sourceFile, "component extends=\"testbox.system.BaseSpec\" {}", StandardCharsets.UTF_8 );

			BoxLangLspAppContextResolver	resolver	= new BoxLangLspAppContextResolver();
			BoxLangLspAppContext			context		= resolver.resolve( projectRoot, sourceFile, null );

			assertEquals( testboxRoot.toAbsolutePath().normalize().toString(), context.mappings().get( "testbox" ) );
			assertTrue( context.moduleDirectories().contains( projectRoot.resolve( ".boxlang/modules" ).toAbsolutePath().normalize() ) );
			assertTrue( resolver.canResolveMappedClassReference( context, "testbox.system.BaseSpec" ) );
		} finally {
			deleteRecursively( projectRoot );
		}
	}

	public void testResolve_supportsApplicationCfcMappingReferenceConcatenation() throws IOException {
		Path projectRoot = Files.createTempDirectory( "boxlang-lsp-app-cfc-mappings" );
		try {
			Path testsRoot = projectRoot.resolve( "tests" );
			Files.createDirectories( testsRoot.resolve( "specs/query" ) );
			Files.createDirectories( testsRoot.resolve( "resources" ) );
			Files.createDirectories( projectRoot.resolve( "testbox/system" ) );
			Files.writeString(
			    testsRoot.resolve( "Application.cfc" ),
			    """
			    component {
			        this.mappings[ "/tests" ] = getDirectoryFromPath( getCurrentTemplatePath() );
			        this.mappings[ "/qb" ] = expandPath( "/" );
			        this.mappings[ "/testbox" ] = this.mappings[ "/qb" ] & "/testbox";
			    }
			    """,
			    StandardCharsets.UTF_8
			);
			Files.writeString( testsRoot.resolve( "resources/AbstractSchemaBuilderSpec.cfc" ), "component {}", StandardCharsets.UTF_8 );
			Files.writeString( projectRoot.resolve( "testbox/system/BaseSpec.cfc" ), "component {}", StandardCharsets.UTF_8 );
			Path sourceFile = testsRoot.resolve( "specs/query/QueryUtilsSpec.cfc" );
			Files.writeString( sourceFile, "component extends=\"testbox.system.BaseSpec\" {}", StandardCharsets.UTF_8 );

			BoxLangLspAppContextResolver	resolver	= new BoxLangLspAppContextResolver();
			BoxLangLspAppContext			context		= resolver.resolve( projectRoot, sourceFile, null );

			assertEquals( testsRoot.toAbsolutePath().normalize().toString(), context.mappings().get( "tests" ) );
			assertEquals( projectRoot.resolve( "testbox" ).toAbsolutePath().normalize().toString(), context.mappings().get( "testbox" ) );
			assertTrue( resolver.canResolveMappedClassReference( context, "testbox.system.BaseSpec" ) );
			assertTrue( resolver.canResolveMappedClassReference( context, "tests.resources.AbstractSchemaBuilderSpec" ) );
		} finally {
			deleteRecursively( projectRoot );
		}
	}

	public void testResolve_supportsModuleConfigCfmappingForModuleSourceFiles() throws IOException {
		Path projectRoot = Files.createTempDirectory( "boxlang-lsp-module-cfmapping" );
		try {
			Files.createDirectories( projectRoot.resolve( "models/Query" ) );
			Files.writeString(
			    projectRoot.resolve( "ModuleConfig.cfc" ),
			    """
			    component {
			        this.cfmapping = "qb";
			    }
			    """,
			    StandardCharsets.UTF_8
			);
			Files.writeString(
			    projectRoot.resolve( "models/Query/QueryBuilder.cfc" ),
			    "component {}",
			    StandardCharsets.UTF_8
			);
			Path sourceFile = projectRoot.resolve( "models/Query/JoinClause.cfc" );
			Files.writeString(
			    sourceFile,
			    "component extends=\"qb.models.Query.QueryBuilder\" {}",
			    StandardCharsets.UTF_8
			);

			BoxLangLspAppContextResolver	resolver	= new BoxLangLspAppContextResolver();
			BoxLangLspAppContext			context		= resolver.resolve( projectRoot, sourceFile, null );

			assertEquals( projectRoot.toAbsolutePath().normalize().toString(), context.mappings().get( "qb" ) );
			assertTrue( resolver.canResolveMappedClassReference( context, "qb.models.Query.QueryBuilder" ) );
		} finally {
			deleteRecursively( projectRoot );
		}
	}

	public void testResolve_supportsVariableAndRelativeMappingExpressions() throws IOException {
		Path projectRoot = Files.createTempDirectory( "boxlang-lsp-var-mappings" );
		try {
			Path appRoot = projectRoot.resolve( "tests" );
			Files.createDirectories( appRoot.resolve( "resources" ) );
			Files.createDirectories( appRoot.resolve( "specs/schema" ) );
			Files.writeString(
			    appRoot.resolve( "Application.cfc" ),
			    """
			    component {
			        var rootPath = getDirectoryFromPath( getCurrentTemplatePath() );
			        var resourcesDir = rootPath & "/resources";
			        this.mappings[ "/tests" ] = rootPath;
			        this.mappings[ "/fixtures" ] = resourcesDir;
			    }
			    """,
			    StandardCharsets.UTF_8
			);
			Files.writeString( appRoot.resolve( "resources/AbstractSchemaBuilderSpec.cfc" ), "component {}", StandardCharsets.UTF_8 );
			Path sourceFile = appRoot.resolve( "specs/schema/MySQLSchemaBuilderSpec.cfc" );
			Files.writeString( sourceFile, "component extends=\"tests.resources.AbstractSchemaBuilderSpec\" {}", StandardCharsets.UTF_8 );

			BoxLangLspAppContextResolver	resolver	= new BoxLangLspAppContextResolver();
			BoxLangLspAppContext			context		= resolver.resolve( projectRoot, sourceFile, null );

			assertEquals( appRoot.toAbsolutePath().normalize().toString(), context.mappings().get( "tests" ) );
			assertEquals( appRoot.resolve( "resources" ).toAbsolutePath().normalize().toString(), context.mappings().get( "fixtures" ) );
			assertTrue( resolver.canResolveMappedClassReference( context, "tests.resources.AbstractSchemaBuilderSpec" ) );
		} finally {
			deleteRecursively( projectRoot );
		}
	}

	public void testResolve_supportsInterpolationInMappingPaths() throws IOException {
		Path projectRoot = Files.createTempDirectory( "boxlang-lsp-interpolation" );
		try {
			Path appRoot = projectRoot.resolve( "app" );
			Files.createDirectories( appRoot.resolve( "modules/api/handlers" ) );
			Files.writeString(
			    appRoot.resolve( "Application.bx" ),
			    """
			    component {
			        var moduleFolder = "api";
			        this.mappings[ "/api" ] = expandPath( "/modules/#moduleFolder#" );
			    }
			    """,
			    StandardCharsets.UTF_8
			);
			Files.writeString( appRoot.resolve( "modules/api/handlers/BaseHandler.cfc" ), "component {}", StandardCharsets.UTF_8 );
			Path sourceFile = appRoot.resolve( "handlers/ChildHandler.cfc" );
			Files.createDirectories( sourceFile.getParent() );
			Files.writeString( sourceFile, "component extends=\"api.handlers.BaseHandler\" {}", StandardCharsets.UTF_8 );

			BoxLangLspAppContextResolver	resolver	= new BoxLangLspAppContextResolver();
			BoxLangLspAppContext			context		= resolver.resolve( projectRoot, sourceFile, null );

			assertEquals(
			    appRoot.resolve( "modules/api" ).toAbsolutePath().normalize().toString(),
			    context.mappings().get( "api" )
			);
			assertTrue( resolver.canResolveMappedClassReference( context, "api.handlers.BaseHandler" ) );
		} finally {
			deleteRecursively( projectRoot );
		}
	}

	private static void deleteRecursively( Path root ) throws IOException {
		if ( root == null || !Files.exists( root ) ) {
			return;
		}
		try ( var walk = Files.walk( root ) ) {
			walk.sorted( java.util.Comparator.reverseOrder() ).forEach( path -> {
				try {
					Files.deleteIfExists( path );
				} catch ( IOException ignored ) {
				}
			} );
		}
	}
}
