package com.ortussolutions.intellijboxlang.runtime;

import com.intellij.openapi.project.Project;
import com.ortussolutions.intellijboxlang.settings.BoxLangResolvedSettings;
import com.ortussolutions.intellijboxlang.settings.BoxLangSettingsResolver;
import com.ortussolutions.intellijboxlang.settings.BoxLangStoragePaths;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves the installation status of LSP and Debugger modules.
 */
public final class ModuleStatusResolver {

	private ModuleStatusResolver() {
	}

	/**
	 * Resolves the LSP module installation status.
	 */
	@NotNull
	public static InstalledModuleStatus resolveLspStatus( @Nullable Project project ) {
		BoxLangResolvedSettings	settings			= project != null
		    ? BoxLangSettingsResolver.resolve( project )
		    : BoxLangSettingsResolver.resolveGlobal();

		String					requestedVersion	= settings.lspVersion;
		if ( requestedVersion == null || requestedVersion.isBlank() ) {
			return InstalledModuleStatus.notInstalled();
		}

		Path	moduleRoot	= BoxLangStoragePaths.getLspCacheRoot().resolve( requestedVersion );
		Path	modulePath	= moduleRoot.resolve( "bx-lsp" );
		Path	boxJsonPath	= findBoxJson( modulePath );

		if ( boxJsonPath == null ) {
			return InstalledModuleStatus.notInstalled();
		}

		String version = ModuleVersionReader.readVersion( boxJsonPath );
		return InstalledModuleStatus.installed( version, modulePath.toString() );
	}

	/**
	 * Resolves the Debugger module installation status.
	 */
	@NotNull
	public static InstalledModuleStatus resolveDebuggerStatus( @Nullable Project project ) {
		BoxLangResolvedSettings settings = project != null
		    ? BoxLangSettingsResolver.resolve( project )
		    : BoxLangSettingsResolver.resolveGlobal();

		// Check configured debugger path first
		if ( settings.debuggerJarPath != null && !settings.debuggerJarPath.isBlank() ) {
			Path configuredPath = Path.of( settings.debuggerJarPath );
			if ( Files.exists( configuredPath ) ) {
				// Try to find version from parent module's box.json
				Path modulePath = configuredPath.getParent();
				if ( modulePath != null ) {
					modulePath = modulePath.getParent(); // Go from libs/ to module root
				}
				String version = modulePath != null ? ModuleVersionReader.readVersion( findBoxJson( modulePath ) ) : null;
				return InstalledModuleStatus.installed( version, configuredPath.toString() );
			}
		}

		// Check LSP BoxLang home
		Path lspBoxLangHome = resolveLspBoxLangHome( project, settings );
		if ( lspBoxLangHome != null ) {
			InstalledModuleStatus status = checkDebuggerInHome( lspBoxLangHome );
			if ( status.installed ) {
				return status;
			}
		}

		// Check user BoxLang home (~/.boxlang)
		String					userHomeStr		= System.getProperty( "user.home" );
		Path					userBoxLangHome	= Path.of( userHomeStr, ".boxlang" );
		InstalledModuleStatus	status			= checkDebuggerInHome( userBoxLangHome );
		if ( status.installed ) {
			return status;
		}

		return InstalledModuleStatus.notInstalled();
	}

	@NotNull
	private static InstalledModuleStatus checkDebuggerInHome( @Nullable Path boxLangHome ) {
		if ( boxLangHome == null || !Files.exists( boxLangHome ) ) {
			return InstalledModuleStatus.notInstalled();
		}

		Path modulePath = boxLangHome.resolve( "modules" ).resolve( "bx-debugger" );
		if ( !Files.exists( modulePath ) ) {
			return InstalledModuleStatus.notInstalled();
		}

		Path boxJsonPath = findBoxJson( modulePath );
		if ( boxJsonPath == null ) {
			return InstalledModuleStatus.notInstalled();
		}

		Path debuggerJar = DebuggerModuleResolver.findDebuggerJar( modulePath );
		if ( debuggerJar == null ) {
			return InstalledModuleStatus.notInstalled();
		}

		String version = ModuleVersionReader.readVersion( boxJsonPath );
		return InstalledModuleStatus.installed( version, debuggerJar.toString() );
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
