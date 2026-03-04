package com.ortussolutions.intellijboxlang.project;

import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.ortussolutions.intellijboxlang.BoxLangIcons;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
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
 * Module builder for BoxLang projects.
 * Handles the creation of BoxLang project structure when using File -&gt; New -&gt; Project...
 */
public final class BoxLangModuleBuilder extends ModuleBuilder {

	private static final Logger	LOG				= Logger.getInstance( BoxLangModuleBuilder.class );

	private GitHubTemplate		selectedTemplate;
	private String				boxLangVersion	= "";

	public BoxLangModuleBuilder() {
		// Default to the first built-in template
		this.selectedTemplate = GitHubTemplate.fromBuiltIn( BoxLangProjectTemplateType.COMMAND_LINE );
	}

	@Override
	public ModuleType<?> getModuleType() {
		return BoxLangModuleType.getInstance();
	}

	@Override
	public String getName() {
		return "BoxLang";
	}

	@Override
	public String getPresentableName() {
		return "BoxLang";
	}

	@Override
	public String getDescription() {
		return "Create a new BoxLang project";
	}

	@Override
	public Icon getNodeIcon() {
		return BoxLangIcons.FILE;
	}

	@NotNull
	public GitHubTemplate getSelectedTemplate() {
		return selectedTemplate;
	}

	public void setSelectedTemplate( @Nullable GitHubTemplate selectedTemplate ) {
		if ( selectedTemplate != null ) {
			this.selectedTemplate = selectedTemplate;
		}
	}

	/**
	 * For backward compatibility.
	 */
	@Nullable
	public BoxLangProjectTemplateType getTemplateType() {
		if ( selectedTemplate == null || !selectedTemplate.isBuiltIn() ) {
			return null;
		}
		String name = selectedTemplate.getName().toUpperCase().replace( "-", "_" );
		try {
			return BoxLangProjectTemplateType.valueOf( name );
		} catch ( IllegalArgumentException e ) {
			return null;
		}
	}

	/**
	 * For backward compatibility.
	 */
	public void setTemplateType( @NotNull BoxLangProjectTemplateType templateType ) {
		this.selectedTemplate = GitHubTemplate.fromBuiltIn( templateType );
	}

	public String getBoxLangVersion() {
		return boxLangVersion;
	}

	public void setBoxLangVersion( String boxLangVersion ) {
		this.boxLangVersion = boxLangVersion;
	}

	@Override
	public void setupRootModel( @NotNull ModifiableRootModel modifiableRootModel ) throws ConfigurationException {
		String contentEntryPath = getContentEntryPath();
		if ( contentEntryPath == null ) {
			return;
		}

		File contentRoot = new File( contentEntryPath );
		FileUtil.createDirectory( contentRoot );

		VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile( contentRoot );
		if ( virtualFile == null ) {
			return;
		}

		ContentEntry	contentEntry	= modifiableRootModel.addContentEntry( virtualFile );
		String			projectName		= contentRoot.getName();

		try {
			generateProjectStructure( contentRoot.toPath(), projectName );

			// Mark src as source root if it exists
			Path srcPath = contentRoot.toPath().resolve( "src" );
			if ( Files.exists( srcPath ) ) {
				VirtualFile srcDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile( srcPath.toFile() );
				if ( srcDir != null ) {
					contentEntry.addSourceFolder( srcDir, false );
				}
			}
		} catch ( IOException e ) {
			throw new ConfigurationException( "Failed to create BoxLang project structure: " + e.getMessage() );
		}
	}

	@Override
	public @Nullable ModuleWizardStep getCustomOptionsStep( WizardContext context, Disposable parentDisposable ) {
		return new BoxLangModuleWizardStep( this );
	}

	private void generateProjectStructure( Path baseDir, String projectName ) throws IOException {
		if ( selectedTemplate.isBuiltIn() ) {
			// Use built-in template generation
			BoxLangProjectTemplateType templateType = getTemplateType();
			if ( templateType != null ) {
				switch ( templateType ) {
					case MINISERVER -> generateMiniServerApplication( baseDir, projectName );
					case COMMANDBOX -> generateCommandBoxApplication( baseDir, projectName );
					case COMMAND_LINE -> generateCommandLineApplication( baseDir, projectName );
					case MODULE -> generateModule( baseDir, projectName );
				}
			}
		} else {
			// Download and extract GitHub template
			downloadAndExtractTemplate( baseDir, projectName );
		}
	}

	/**
	 * Downloads and extracts a GitHub template to the project directory.
	 */
	private void downloadAndExtractTemplate( Path baseDir, String projectName ) throws IOException {
		String zipUrl = selectedTemplate.getZipDownloadUrl();
		if ( zipUrl == null ) {
			throw new IOException( "Template does not have a valid download URL" );
		}

		LOG.info( "Downloading template from: " + zipUrl );

		// Create temp directory for extraction
		Path tempDir = Files.createTempDirectory( "boxlang-template-" );

		try {
			// Download and extract ZIP
			try ( InputStream in = URI.create( zipUrl ).toURL().openStream();
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

			// Copy extracted files to project directory
			copyDirectory( tempDir, baseDir );

			// Replace placeholder project name in common files
			replaceProjectNameInFiles( baseDir, projectName );

		} finally {
			// Clean up temp directory
			deleteDirectory( tempDir );
		}

		LOG.info( "Template extracted successfully to: " + baseDir );
	}

	/**
	 * Recursively copies a directory.
	 */
	private void copyDirectory( Path source, Path target ) throws IOException {
		Files.walkFileTree( source, new SimpleFileVisitor<>() {

			@Override
			public FileVisitResult preVisitDirectory( Path dir, BasicFileAttributes attrs ) throws IOException {
				Path targetDir = target.resolve( source.relativize( dir ) );
				Files.createDirectories( targetDir );
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile( Path file, BasicFileAttributes attrs ) throws IOException {
				Files.copy( file, target.resolve( source.relativize( file ) ), StandardCopyOption.REPLACE_EXISTING );
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
	private void replaceProjectNameInFiles( Path baseDir, String projectName ) throws IOException {
		// Files that commonly contain project name placeholders
		String[] configFiles = { "box.json", "server.json", "package.json" };

		for ( String fileName : configFiles ) {
			Path filePath = baseDir.resolve( fileName );
			if ( Files.exists( filePath ) ) {
				String	content	= Files.readString( filePath, StandardCharsets.UTF_8 );
				// Replace common placeholders
				String	updated	= content
				    .replace( "{{projectName}}", projectName )
				    .replace( "{{PROJECT_NAME}}", projectName )
				    .replace( "${projectName}", projectName );
				if ( !updated.equals( content ) ) {
					Files.writeString( filePath, updated, StandardCharsets.UTF_8 );
				}
			}
		}
	}

	private void generateMiniServerApplication( Path baseDir, String projectName ) throws IOException {
		// Create directory structure
		Path	srcDir	= Files.createDirectories( baseDir.resolve( "src" ) );

		// Create box.json
		String	boxJson	= generateBoxJson( projectName, "web-app" );
		writeFile( baseDir.resolve( "box.json" ), boxJson );

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
		writeFile( srcDir.resolve( "Application.bx" ), applicationBx );

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
		writeFile( srcDir.resolve( "index.bxm" ), indexBxm );

		// Create server.bxs for MiniServer
		String serverBxs = """
		                   // BoxLang MiniServer startup script
		                   // Run this file to start the development server

		                   println( "Starting BoxLang MiniServer..." );
		                   println( "Visit http://localhost:8080 in your browser" );
		                   """;
		writeFile( baseDir.resolve( "server.bxs" ), serverBxs );
	}

	private void generateCommandBoxApplication( Path baseDir, String projectName ) throws IOException {
		// Create directory structure
		Path	srcDir	= Files.createDirectories( baseDir.resolve( "src" ) );

		// Create box.json
		String	boxJson	= generateBoxJson( projectName, "web-app" );
		writeFile( baseDir.resolve( "box.json" ), boxJson );

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
		writeFile( baseDir.resolve( "server.json" ), serverJson );

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
		writeFile( srcDir.resolve( "Application.bx" ), applicationBx );

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
		writeFile( srcDir.resolve( "index.bxm" ), indexBxm );
	}

	private void generateCommandLineApplication( Path baseDir, String projectName ) throws IOException {
		// Create directory structure
		Path	srcDir	= Files.createDirectories( baseDir.resolve( "src" ) );

		// Create box.json
		String	boxJson	= generateBoxJson( projectName, "cli-app" );
		writeFile( baseDir.resolve( "box.json" ), boxJson );

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
		writeFile( srcDir.resolve( "Main.bx" ), mainBx );
	}

	private void generateModule( Path baseDir, String projectName ) throws IOException {
		// Create directory structure
		Path	moduleDir	= Files.createDirectories( baseDir.resolve( "bx-modules" ).resolve( projectName ) );

		// Create box.json
		String	boxJson		= generateBoxJson( projectName, "module" );
		writeFile( baseDir.resolve( "box.json" ), boxJson );

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
		writeFile( moduleDir.resolve( "ModuleConfig.bx" ), moduleConfigBx );

		// Create a sample component
		Path	modelsDir	= Files.createDirectories( moduleDir.resolve( "models" ) );
		String	sampleBx	= """
		                      class {

		                          function init() {
		                              return this;
		                          }

		                          function greet( name = "World" ) {
		                              return "Hello, " & name & "!";
		                          }

		                      }
		                      """;
		writeFile( modelsDir.resolve( "Greeter.bx" ), sampleBx );
	}

	private String generateBoxJson( String projectName, String type ) {
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

	private void writeFile( Path path, String content ) throws IOException {
		Files.writeString( path, content, StandardCharsets.UTF_8 );
	}
}
