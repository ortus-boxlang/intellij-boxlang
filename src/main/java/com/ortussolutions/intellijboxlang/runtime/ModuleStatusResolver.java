package com.ortussolutions.intellijboxlang.runtime;

import com.intellij.openapi.project.Project;
import com.ortussolutions.intellijboxlang.settings.BoxLangResolvedSettings;
import com.ortussolutions.intellijboxlang.settings.BoxLangSettingsResolver;
import com.ortussolutions.intellijboxlang.settings.BoxLangStoragePaths;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves the installation status of LSP and Debugger modules.
 */
public final class ModuleStatusResolver {

	private ModuleStatusResolver() {
	}

	/**
	 * Resolves the BoxLang runtime installation status.
	 *
	 * <p>
	 * Resolution order:
	 * <ol>
	 * <li>Plugin-managed IDE cache ({@code <IDE system>/boxlang/runtime/})</li>
	 * <li>System-installed runtime via BVM ({@code ~/.bvm/current}, {@code ~/.bvm/versions/*})
	 * or a manual installation ({@code ~/.local/boxlang})</li>
	 * </ol>
	 *
	 * @param latestVersion the latest version name from the S3 catalog, or null if not yet fetched
	 */
	@NotNull
	public static InstalledModuleStatus resolveRuntimeStatus( @Nullable String latestVersion ) {
		// 1. Check the plugin-managed IDE cache first
		Path cacheRoot = BoxLangStoragePaths.getRuntimeCacheRoot();
		if ( Files.exists( cacheRoot ) ) {
			try ( Stream<Path> versionDirs = Files.list( cacheRoot ) ) {
				Optional<Path> latest = versionDirs
				    .filter( Files::isDirectory )
				    .filter( dir -> {
					    String name = dir.getFileName().toString();
					    return Files.exists( dir.resolve( name + ".jar" ) );
				    } )
				    .max( Comparator.comparingLong( dir -> {
					    try {
						    return Files.getLastModifiedTime( dir ).toMillis();
					    } catch ( IOException e ) {
						    return 0L;
					    }
				    } ) );

				if ( latest.isPresent() ) {
					Path	versionDir		= latest.get();
					String	version			= versionDir.getFileName().toString();
					String	displayVersion	= version.startsWith( "boxlang-" ) ? version.substring( "boxlang-".length() ) : version;
					Path	jarPath			= versionDir.resolve( version + ".jar" );
					return InstalledModuleStatus.installed( displayVersion, jarPath.toString(), latestVersion );
				}
			} catch ( IOException ignored ) {
				// Fall through to system check
			}
		}

		// 2. Fall back to any system-installed runtime (BVM, ~/.local/boxlang, etc.)
		BoxLangSystemRuntimeLocator.SystemRuntimeInfo systemRuntime = BoxLangSystemRuntimeLocator.findBest();
		if ( systemRuntime != null ) {
			return InstalledModuleStatus.installed( systemRuntime.version, systemRuntime.jarPath.toString(),
			    latestVersion );
		}

		return InstalledModuleStatus.notInstalled( latestVersion );
	}

	/**
	 * Resolves the LSP module installation status.
	 */
	@NotNull
	public static InstalledModuleStatus resolveLspStatus( @Nullable Project project ) {
		return resolveLspStatus( project, null );
	}

	/**
	 * Resolves the LSP module installation status, with a known latest ForgeBox version for the
	 * outdated indicator.
	 *
	 * <p>
	 * Resolution order:
	 * <ol>
	 * <li>Explicit module path override in settings</li>
	 * <li>Project-local {@code <projectDir>/.boxlang/modules/bx-lsp} (when project is provided)</li>
	 * <li>Global BoxLang home {@code <globalBoxLangHome>/modules/bx-lsp} (flat install)</li>
	 * <li>Legacy version-nested cache (backwards compatibility)</li>
	 * </ol>
	 *
	 * @param latestVersion the latest version string from ForgeBox, or null if not yet fetched
	 */
	@NotNull
	public static InstalledModuleStatus resolveLspStatus( @Nullable Project project, @Nullable String latestVersion ) {
		BoxLangResolvedSettings	settings			= project != null
		    ? BoxLangSettingsResolver.resolve( project )
		    : BoxLangSettingsResolver.resolveGlobal();

		String					requestedVersion	= settings.lspVersion;

		// If an explicit module path override is set, check it first
		if ( settings.lspModulePath != null && !settings.lspModulePath.isBlank() ) {
			Path	overridePath	= ConfiguredPathResolver.resolvePath( project, settings.lspModulePath );
			Path	bxLspPath		= LspModuleResolver.resolveOverrideModulePath( overridePath );
			Path	boxJsonPath		= LspModuleResolver.findBoxJson( bxLspPath );
			if ( boxJsonPath != null ) {
				String version = ModuleVersionReader.readVersion( boxJsonPath );
				return InstalledModuleStatus.installed( version, bxLspPath.toString(), latestVersion );
			}
			return InstalledModuleStatus.notInstalled( latestVersion );
		}

		// Check project-local .boxlang home
		if ( project != null ) {
			Path projectBoxLangHome = BoxLangStoragePaths.getProjectBoxLangHome( project );
			if ( projectBoxLangHome != null ) {
				Path	bxLspPath	= projectBoxLangHome.resolve( "modules" ).resolve( "bx-lsp" );
				Path	boxJson		= LspModuleResolver.findBoxJson( bxLspPath );
				if ( boxJson != null ) {
					String version = ModuleVersionReader.readVersion( boxJson );
					return InstalledModuleStatus.installed( version, bxLspPath.toString(), latestVersion );
				}
			}
		}

		// If no version is configured, check global BoxLang home first, then scan legacy cache
		if ( requestedVersion == null || requestedVersion.isBlank() ) {
			Path	globalHome	= BoxLangStoragePaths.getUserBoxLangHome();
			Path	bxLspPath	= globalHome.resolve( "modules" ).resolve( "bx-lsp" );
			Path	boxJson		= findBoxJson( bxLspPath );
			if ( boxJson != null ) {
				String version = ModuleVersionReader.readVersion( boxJson );
				return InstalledModuleStatus.installed( version, bxLspPath.toString(), latestVersion );
			}

			// Legacy: scan the old version-nested cache structure
			LspModuleInfo found = LspModuleResolver.findAnyInstalledVersion();
			if ( found == null || found.needsDownload ) {
				return InstalledModuleStatus.notInstalled( latestVersion );
			}
			Path	boxJsonPath	= found.boxJsonPath;
			String	version		= boxJsonPath != null ? ModuleVersionReader.readVersion( boxJsonPath ) : null;
			Path	modulePath	= found.modulePath;
			return InstalledModuleStatus.installed( version,
			    modulePath != null ? modulePath.toString() : null,
			    latestVersion );
		}

		// Check the global BoxLang home for a flat module install
		Path	globalBoxLangHome	= BoxLangStoragePaths.getUserBoxLangHome();
		Path	modulePath			= globalBoxLangHome.resolve( "modules" ).resolve( "bx-lsp" );
		Path	boxJsonPath			= findBoxJson( modulePath );

		if ( boxJsonPath == null ) {
			return InstalledModuleStatus.notInstalled( latestVersion );
		}

		String version = ModuleVersionReader.readVersion( boxJsonPath );
		return InstalledModuleStatus.installed( version, modulePath.toString(), latestVersion );
	}

	/**
	 * Resolves the LSP module status from the global BoxLang home only (ignoring any project-local install).
	 * Used by the Project Overrides panel to show the "Global:" baseline.
	 */
	@NotNull
	public static InstalledModuleStatus resolveGlobalLspStatus( @Nullable String latestVersion ) {
		BoxLangResolvedSettings settings = BoxLangSettingsResolver.resolveGlobal();

		// Explicit global module path override
		if ( settings.lspModulePath != null && !settings.lspModulePath.isBlank() ) {
			Path	overridePath	= ConfiguredPathResolver.resolvePath( null, settings.lspModulePath );
			Path	bxLspPath		= LspModuleResolver.resolveOverrideModulePath( overridePath );
			Path	boxJson			= LspModuleResolver.findBoxJson( bxLspPath );
			if ( boxJson != null ) {
				String version = ModuleVersionReader.readVersion( boxJson );
				return InstalledModuleStatus.installed( version, bxLspPath.toString(), latestVersion );
			}
			return InstalledModuleStatus.notInstalled( latestVersion );
		}

		// Global BoxLang home flat install
		Path	globalHome	= BoxLangStoragePaths.getUserBoxLangHome();
		Path	bxLspPath	= globalHome.resolve( "modules" ).resolve( "bx-lsp" );
		Path	boxJson		= findBoxJson( bxLspPath );
		if ( boxJson != null ) {
			String version = ModuleVersionReader.readVersion( boxJson );
			return InstalledModuleStatus.installed( version, bxLspPath.toString(), latestVersion );
		}

		// Legacy version-nested cache scan
		LspModuleInfo found = LspModuleResolver.findAnyInstalledVersion();
		if ( found == null || found.needsDownload ) {
			return InstalledModuleStatus.notInstalled( latestVersion );
		}
		Path	boxJsonPath	= found.boxJsonPath;
		String	version		= boxJsonPath != null ? ModuleVersionReader.readVersion( boxJsonPath ) : null;
		Path	modulePath	= found.modulePath;
		return InstalledModuleStatus.installed( version, modulePath != null ? modulePath.toString() : null,
		    latestVersion );
	}

	/**
	 * Resolves the Debugger module status from the global BoxLang home only (ignoring any project-local install).
	 * Used by the Project Overrides panel to show the "Global:" baseline.
	 */
	@NotNull
	public static InstalledModuleStatus resolveGlobalDebuggerStatus( @Nullable String latestVersion ) {
		BoxLangResolvedSettings settings = BoxLangSettingsResolver.resolveGlobal();

		// Explicit global module path override
		if ( settings.debuggerModulePath != null && !settings.debuggerModulePath.isBlank() ) {
			Path modulePath = Path.of( settings.debuggerModulePath );
			if ( Files.exists( modulePath ) ) {
				Path	bxDebuggerPath	= modulePath.resolve( "bx-debugger" );
				Path	boxJsonPath		= findBoxJson( bxDebuggerPath );
				if ( boxJsonPath != null && Files.isDirectory( bxDebuggerPath ) ) {
					String version = ModuleVersionReader.readVersion( boxJsonPath );
					return InstalledModuleStatus.installed( version, bxDebuggerPath.toString(), latestVersion );
				}
			}
			return InstalledModuleStatus.notInstalled( latestVersion );
		}

		// Global user BoxLang home (~/.boxlang)
		return checkDebuggerInHome( BoxLangStoragePaths.getUserBoxLangHome(), latestVersion );
	}

	/**
	 * Resolves the LSP module status for a project-local install only (ignoring global).
	 * Returns not-installed if no project-local bx-lsp is found.
	 */
	@NotNull
	public static InstalledModuleStatus resolveProjectLspStatus( @NotNull Project project,
	    @Nullable String latestVersion ) {
		Path projectBoxLangHome = BoxLangStoragePaths.getProjectBoxLangHome( project );
		if ( projectBoxLangHome == null ) {
			return InstalledModuleStatus.notInstalled( latestVersion );
		}
		Path	bxLspPath	= projectBoxLangHome.resolve( "modules" ).resolve( "bx-lsp" );
		Path	boxJson		= LspModuleResolver.findBoxJson( bxLspPath );
		if ( boxJson != null ) {
			String version = ModuleVersionReader.readVersion( boxJson );
			return InstalledModuleStatus.installed( version, bxLspPath.toString(), latestVersion );
		}
		return InstalledModuleStatus.notInstalled( latestVersion );
	}

	/**
	 * Resolves the Debugger module status for a project-local install only (ignoring global).
	 * Returns not-installed if no project-local bx-debugger is found.
	 */
	@NotNull
	public static InstalledModuleStatus resolveProjectDebuggerStatus( @NotNull Project project,
	    @Nullable String latestVersion ) {
		Path projectBoxLangHome = BoxLangStoragePaths.getProjectBoxLangHome( project );
		if ( projectBoxLangHome == null ) {
			return InstalledModuleStatus.notInstalled( latestVersion );
		}
		return checkDebuggerInHome( projectBoxLangHome, latestVersion );
	}

	/**
	 * Resolves the Debugger module installation status.
	 */
	@NotNull
	public static InstalledModuleStatus resolveDebuggerStatus( @Nullable Project project ) {
		return resolveDebuggerStatus( project, null );
	}

	/**
	 * Resolves the Debugger module installation status, with a known latest ForgeBox version for the
	 * outdated indicator.
	 *
	 * <p>
	 * Resolution order:
	 * <ol>
	 * <li>Explicit module path override in settings</li>
	 * <li>LSP BoxLang home (modules directory)</li>
	 * <li>Project-local {@code <projectDir>/.boxlang} (when project is provided)</li>
	 * <li>Global user BoxLang home ({@code ~/.boxlang})</li>
	 * </ol>
	 *
	 * @param latestVersion the latest version string from ForgeBox, or null if not yet fetched
	 */
	@NotNull
	public static InstalledModuleStatus resolveDebuggerStatus( @Nullable Project project, @Nullable String latestVersion ) {
		BoxLangResolvedSettings settings = project != null
		    ? BoxLangSettingsResolver.resolve( project )
		    : BoxLangSettingsResolver.resolveGlobal();

		// Check configured debugger module path first
		if ( settings.debuggerModulePath != null && !settings.debuggerModulePath.isBlank() ) {
			Path modulePath = Path.of( settings.debuggerModulePath );
			if ( Files.exists( modulePath ) ) {
				Path	bxDebuggerPath	= modulePath.resolve( "bx-debugger" );
				Path	boxJsonPath		= findBoxJson( bxDebuggerPath );
				if ( boxJsonPath != null && Files.isDirectory( bxDebuggerPath ) ) {
					String version = ModuleVersionReader.readVersion( boxJsonPath );
					return InstalledModuleStatus.installed( version, bxDebuggerPath.toString(), latestVersion );
				}
			}
		}

		// Check LSP BoxLang home
		Path lspBoxLangHome = resolveLspBoxLangHome( project, settings );
		if ( lspBoxLangHome != null ) {
			InstalledModuleStatus status = checkDebuggerInHome( lspBoxLangHome, latestVersion );
			if ( status.installed ) {
				return status;
			}
		}

		// Check project-local .boxlang home
		if ( project != null ) {
			Path projectBoxLangHome = BoxLangStoragePaths.getProjectBoxLangHome( project );
			if ( projectBoxLangHome != null ) {
				InstalledModuleStatus status = checkDebuggerInHome( projectBoxLangHome, latestVersion );
				if ( status.installed ) {
					return status;
				}
			}
		}

		// Check user BoxLang home (~/.boxlang)
		InstalledModuleStatus status = checkDebuggerInHome( BoxLangStoragePaths.getUserBoxLangHome(), latestVersion );
		if ( status.installed ) {
			return status;
		}

		return InstalledModuleStatus.notInstalled( latestVersion );
	}

	@NotNull
	private static InstalledModuleStatus checkDebuggerInHome( @Nullable Path boxLangHome, @Nullable String latestVersion ) {
		if ( boxLangHome == null || !Files.exists( boxLangHome ) ) {
			return InstalledModuleStatus.notInstalled( latestVersion );
		}

		Path modulePath = boxLangHome.resolve( "modules" ).resolve( "bx-debugger" );
		if ( !Files.exists( modulePath ) ) {
			return InstalledModuleStatus.notInstalled( latestVersion );
		}

		Path boxJsonPath = findBoxJson( modulePath );
		if ( boxJsonPath == null ) {
			return InstalledModuleStatus.notInstalled( latestVersion );
		}

		String version = ModuleVersionReader.readVersion( boxJsonPath );
		return InstalledModuleStatus.installed( version, modulePath.toString(), latestVersion );
	}

	@Nullable
	private static Path resolveLspBoxLangHome( @Nullable Project project, BoxLangResolvedSettings settings ) {
		if ( settings.lspBoxLangHome != null && !settings.lspBoxLangHome.isBlank() ) {
			Path customPath = Path.of( settings.lspBoxLangHome );
			if ( !customPath.isAbsolute() && project != null ) {
				String basePath = project.getBasePath();
				if ( basePath != null ) {
					return Path.of( basePath ).resolve( customPath );
				}
			}
			return customPath;
		}

		if ( project != null ) {
			String projectKey = project.getLocationHash();
			return BoxLangStoragePaths.getCacheRoot().resolve( "lsp-home" ).resolve( projectKey );
		}

		return null;
	}

	@Nullable
	private static Path findBoxJson( @Nullable Path moduleRoot ) {
		if ( moduleRoot == null || !Files.exists( moduleRoot ) ) {
			return null;
		}
		Path boxJsonPath = moduleRoot.resolve( "box.json" );
		return Files.exists( boxJsonPath ) ? boxJsonPath : null;
	}
}
