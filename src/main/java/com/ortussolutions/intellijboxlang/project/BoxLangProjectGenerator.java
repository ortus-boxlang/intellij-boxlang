package com.ortussolutions.intellijboxlang.project;

import com.intellij.facet.ui.ValidationResult;
import com.intellij.ide.util.projectWizard.AbstractNewProjectStep;
import com.intellij.ide.util.projectWizard.CustomStepProjectGenerator;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.impl.welcomeScreen.AbstractActionWithPanel;
import com.intellij.platform.DirectoryProjectGenerator;
import com.intellij.platform.ProjectGeneratorPeer;
import com.ortussolutions.intellijboxlang.BoxLangIcons;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Project generator for BoxLang projects.
 * This enables creating new BoxLang projects via File -&gt; New -&gt; Project...
 */
public final class BoxLangProjectGenerator implements DirectoryProjectGenerator<BoxLangProjectSettings>,
    CustomStepProjectGenerator<BoxLangProjectSettings> {

	private static final Logger LOG = Logger.getInstance( BoxLangProjectGenerator.class );

	@Override
	public @NotNull String getName() {
		return "BoxLang";
	}

	@Override
	public @Nullable String getDescription() {
		return "Create a new BoxLang project";
	}

	@Override
	public @Nullable Icon getLogo() {
		return BoxLangIcons.FILE;
	}

	@Override
	public @NotNull ProjectGeneratorPeer<BoxLangProjectSettings> createPeer() {
		return new BoxLangProjectGeneratorPeer();
	}

	@Override
	public @NotNull ValidationResult validate( @NotNull String baseDirPath ) {
		return ValidationResult.OK;
	}

	@Override
	public void generateProject(
	    @NotNull Project project,
	    @NotNull VirtualFile baseDir,
	    @NotNull BoxLangProjectSettings settings,
	    @NotNull Module module ) {
		ApplicationManager.getApplication().runWriteAction( () -> {
			try {
				String projectName = baseDir.getName();
				generateProjectStructure( baseDir, settings, projectName );
			} catch ( IOException e ) {
				throw new RuntimeException( "Failed to generate BoxLang project structure", e );
			}
		} );
	}

	private void generateProjectStructure( VirtualFile baseDir, BoxLangProjectSettings settings, String projectName ) throws IOException {
		GitHubTemplate template = settings.getSelectedTemplate();

		if ( template.isBuiltIn() ) {
			// Use built-in template generation
			BoxLangProjectTemplateType templateType = settings.getTemplateType();
			if ( templateType != null ) {
				switch ( templateType ) {
					case MINISERVER -> generateMiniServerApplication( baseDir, settings, projectName );
					case COMMANDBOX -> generateCommandBoxApplication( baseDir, settings, projectName );
					case COMMAND_LINE -> generateCommandLineApplication( baseDir, settings, projectName );
					case MODULE -> generateModule( baseDir, settings, projectName );
				}
			}
		} else {
			// Download and extract GitHub template
			downloadAndExtractTemplate( baseDir, template, projectName, settings.getBoxLangVersion() );
		}
	}

	/**
	 * Downloads and extracts a GitHub template to the project directory.
	 */
	private void downloadAndExtractTemplate( VirtualFile baseDir, GitHubTemplate template, String projectName, String boxLangVersion )
	    throws IOException {
		String zipUrl = template.getZipDownloadUrl();
		if ( zipUrl == null ) {
			throw new IOException( "Template does not have a valid download URL" );
		}

		LOG.info( "Downloading template from: " + zipUrl );

		// Create temp directory for extraction
		Path tempDir = Files.createTempDirectory( "boxlang-template-" );

		try {
			// Download and extract ZIP
			try ( InputStream in = new URL( zipUrl ).openStream();
			    ZipInputStream zis = new ZipInputStream( in ) ) {

				ZipEntry entry;
				while ( ( entry = zis.getNextEntry() ) != null ) {
					// ZIP contains a root folder like "repo-name-main/", we need to strip it
					String	entryName	= entry.getName();
					int		slashIndex	= entryName.indexOf( '/' );
					if ( slashIndex < 0 ) {
						continue; // Skip root directory entry
					}
					String relativePath = entryName.substring( slashIndex + 1 );
					if ( relativePath.isEmpty() ) {
						continue;
					}

					Path targetPath = tempDir.resolve( relativePath );
					if ( entry.isDirectory() ) {
						Files.createDirectories( targetPath );
					} else {
						Files.createDirectories( targetPath.getParent() );
						Files.copy( zis, targetPath, StandardCopyOption.REPLACE_EXISTING );
					}
					zis.closeEntry();
				}
			}

			// Copy extracted files to project directory using VFS
			copyDirectoryToVfs( tempDir, baseDir );

			// Replace placeholder project name in common files
			replaceProjectNameInFiles( baseDir, projectName );

		} finally {
			// Clean up temp directory
			deleteDirectory( tempDir );
		}

		LOG.info( "Template extracted successfully to: " + baseDir.getPath() );
	}

	/**
	 * Copies a directory from the filesystem to a VirtualFile directory.
	 */
	private void copyDirectoryToVfs( Path source, VirtualFile targetDir ) throws IOException {
		Files.walkFileTree( source, new SimpleFileVisitor<>() {

			@Override
			public FileVisitResult preVisitDirectory( Path dir, BasicFileAttributes attrs ) throws IOException {
				Path relativePath = source.relativize( dir );
				if ( !relativePath.toString().isEmpty() ) {
					VfsUtil.createDirectoryIfMissing( targetDir, relativePath.toString().replace( '\\', '/' ) );
				}
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile( Path file, BasicFileAttributes attrs ) throws IOException {
				Path		relativePath	= source.relativize( file );
				String		relPathStr		= relativePath.toString().replace( '\\', '/' );
				int			lastSlash		= relPathStr.lastIndexOf( '/' );
				VirtualFile	parentDir;
				String		fileName;

				if ( lastSlash >= 0 ) {
					parentDir	= VfsUtil.createDirectoryIfMissing( targetDir, relPathStr.substring( 0, lastSlash ) );
					fileName	= relPathStr.substring( lastSlash + 1 );
				} else {
					parentDir	= targetDir;
					fileName	= relPathStr;
				}

				if ( parentDir != null ) {
					byte[]		content	= Files.readAllBytes( file );
					VirtualFile	newFile	= parentDir.createChildData( this, fileName );
					newFile.setBinaryContent( content );
				}
				return FileVisitResult.CONTINUE;
			}
		} );
	}

	/**
	 * Recursively deletes a directory.
	 */
	private void deleteDirectory( Path directory ) {
		try {
			Files.walkFileTree( directory, new SimpleFileVisitor<>() {

				@Override
				public FileVisitResult visitFile( Path file, BasicFileAttributes attrs ) throws IOException {
					Files.delete( file );
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult postVisitDirectory( Path dir, IOException exc ) throws IOException {
					Files.delete( dir );
					return FileVisitResult.CONTINUE;
				}
			} );
		} catch ( IOException e ) {
			LOG.warn( "Failed to delete temp directory: " + directory, e );
		}
	}

	/**
	 * Replaces placeholder project names in common configuration files.
	 */
	private void replaceProjectNameInFiles( VirtualFile baseDir, String projectName ) throws IOException {
		// Files that commonly contain project name placeholders
		String[] configFiles = { "box.json", "server.json", "package.json" };

		for ( String fileName : configFiles ) {
			VirtualFile file = baseDir.findChild( fileName );
			if ( file != null && file.exists() ) {
				String	content	= new String( file.contentsToByteArray(), StandardCharsets.UTF_8 );
				// Replace common placeholders
				String	updated	= content
				    .replace( "{{projectName}}", projectName )
				    .replace( "{{PROJECT_NAME}}", projectName )
				    .replace( "${projectName}", projectName );
				if ( !updated.equals( content ) ) {
					file.setBinaryContent( updated.getBytes( StandardCharsets.UTF_8 ) );
				}
			}
		}
	}

	private void generateMiniServerApplication( VirtualFile baseDir, BoxLangProjectSettings settings, String projectName ) throws IOException {
		// Create directory structure
		VirtualFile	srcDir	= VfsUtil.createDirectoryIfMissing( baseDir, "src" );

		// Create box.json
		String		boxJson	= generateBoxJson( projectName, "web-app", settings.getBoxLangVersion() );
		createFile( baseDir, "box.json", boxJson );

		// Create Application.bx
		String applicationBx = """
		                       class {

		                           this.name = "%s";
		                           this.sessionManagement = true;
		                           this.sessionTimeout = createTimeSpan( 0, 0, 30, 0 );

		                           function onApplicationStart() {
		                               return true;
		                           }

		                           function onRequestStart( targetPage ) {
		                               return true;
		                           }

		                       }
		                       """.formatted( projectName );
		createFile( srcDir, "Application.bx", applicationBx );

		// Create index.bxm
		String indexBxm = """
		                  <bx:output>
		                  <!DOCTYPE html>
		                  <html>
		                  <head>
		                      <title>%s</title>
		                  </head>
		                  <body>
		                      <h1>Welcome to BoxLang!</h1>
		                      <p>Your MiniServer web application is ready.</p>
		                  </body>
		                  </html>
		                  </bx:output>
		                  """.formatted( projectName );
		createFile( srcDir, "index.bxm", indexBxm );

		// Create server.bxs for MiniServer
		String serverBxs = """
		                   // BoxLang MiniServer startup script
		                   // Run this file to start the development server

		                   println( "Starting BoxLang MiniServer..." );
		                   println( "Visit http://localhost:8080 in your browser" );
		                   """;
		createFile( baseDir, "server.bxs", serverBxs );
	}

	private void generateCommandBoxApplication( VirtualFile baseDir, BoxLangProjectSettings settings, String projectName ) throws IOException {
		// Create directory structure
		VirtualFile	srcDir	= VfsUtil.createDirectoryIfMissing( baseDir, "src" );

		// Create box.json
		String		boxJson	= generateBoxJson( projectName, "web-app", settings.getBoxLangVersion() );
		createFile( baseDir, "box.json", boxJson );

		// Create server.json for CommandBox
		String serverJson = """
		                    {
		                        "name": "%s",
		                        "app": {
		                            "cfengine": "boxlang"
		                        },
		                        "web": {
		                            "host": "127.0.0.1",
		                            "http": {
		                                "port": 8080,
		                                "enable": true
		                            },
		                            "webroot": "src"
		                        },
		                        "openBrowser": true
		                    }
		                    """.formatted( projectName );
		createFile( baseDir, "server.json", serverJson );

		// Create Application.bx
		String applicationBx = """
		                       class {

		                           this.name = "%s";
		                           this.sessionManagement = true;
		                           this.sessionTimeout = createTimeSpan( 0, 0, 30, 0 );

		                           function onApplicationStart() {
		                               return true;
		                           }

		                           function onRequestStart( targetPage ) {
		                               return true;
		                           }

		                       }
		                       """.formatted( projectName );
		createFile( srcDir, "Application.bx", applicationBx );

		// Create index.bxm
		String indexBxm = """
		                  <bx:output>
		                  <!DOCTYPE html>
		                  <html>
		                  <head>
		                      <title>%s</title>
		                  </head>
		                  <body>
		                      <h1>Welcome to BoxLang!</h1>
		                      <p>Your CommandBox web application is ready.</p>
		                      <p>Run <code>box server start</code> to start the server.</p>
		                  </body>
		                  </html>
		                  </bx:output>
		                  """.formatted( projectName );
		createFile( srcDir, "index.bxm", indexBxm );
	}

	private void generateCommandLineApplication( VirtualFile baseDir, BoxLangProjectSettings settings, String projectName ) throws IOException {
		// Create directory structure
		VirtualFile	srcDir	= VfsUtil.createDirectoryIfMissing( baseDir, "src" );

		// Create box.json
		String		boxJson	= generateBoxJson( projectName, "cli-app", settings.getBoxLangVersion() );
		createFile( baseDir, "box.json", boxJson );

		// Create main.bx
		String mainBx = """
		                class {

		                    function main( args = [] ) {
		                        println( "Hello from BoxLang!" );

		                        if ( args.len() > 0 ) {
		                            println( "Arguments: " & args.toList( ", " ) );
		                        }
		                    }

		                }
		                """;
		createFile( srcDir, "Main.bx", mainBx );
	}

	private void generateModule( VirtualFile baseDir, BoxLangProjectSettings settings, String projectName ) throws IOException {
		// Create directory structure
		VirtualFile	bxmDir		= VfsUtil.createDirectoryIfMissing( baseDir, "bx-modules" );
		VirtualFile	moduleDir	= VfsUtil.createDirectoryIfMissing( bxmDir, projectName );

		// Create box.json
		String		boxJson		= generateBoxJson( projectName, "module", settings.getBoxLangVersion() );
		createFile( baseDir, "box.json", boxJson );

		// Create ModuleConfig.bx
		String moduleConfigBx = """
		                        class {

		                            this.name = "%s";
		                            this.version = "1.0.0";
		                            this.author = "";
		                            this.description = "A BoxLang module";

		                            function configure() {
		                                // Module configuration
		                            }

		                            function onLoad() {
		                                // Called when module is loaded
		                            }

		                            function onUnload() {
		                                // Called when module is unloaded
		                            }

		                        }
		                        """.formatted( projectName );
		createFile( moduleDir, "ModuleConfig.bx", moduleConfigBx );

		// Create a sample component
		VirtualFile	modelsDir	= VfsUtil.createDirectoryIfMissing( moduleDir, "models" );
		String		sampleBx	= """
		                          class {

		                              function init() {
		                                  return this;
		                              }

		                              function greet( name = "World" ) {
		                                  return "Hello, " & name & "!";
		                              }

		                          }
		                          """;
		createFile( modelsDir, "Greeter.bx", sampleBx );
	}

	private String generateBoxJson( String projectName, String type, String boxLangVersion ) {
		String version = boxLangVersion != null && !boxLangVersion.isBlank() ? boxLangVersion : "1.0.0";
		return """
		       {
		           "name": "%s",
		           "version": "1.0.0",
		           "type": "%s",
		           "boxlang": {
		               "version": "%s"
		           }
		       }
		       """.formatted( projectName, type, version );
	}

	private void createFile( VirtualFile directory, String fileName, String content ) throws IOException {
		VirtualFile file = directory.createChildData( this, fileName );
		file.setBinaryContent( content.getBytes( StandardCharsets.UTF_8 ) );
	}

	@Override
	public AbstractActionWithPanel createStep(
	    DirectoryProjectGenerator<BoxLangProjectSettings> projectGenerator,
	    AbstractNewProjectStep.AbstractCallback<BoxLangProjectSettings> callback ) {
		return new BoxLangProjectSettingsStep( projectGenerator, callback );
	}
}
