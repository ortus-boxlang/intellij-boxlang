package com.ortussolutions.intellijboxlang.runtime;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.ortussolutions.intellijboxlang.settings.BoxLangResolvedSettings;
import com.ortussolutions.intellijboxlang.settings.BoxLangSettingsResolver;
import com.ortussolutions.intellijboxlang.settings.BoxLangStoragePaths;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class BoxLangLspBootstrapService {

	private static final java.util.concurrent.ConcurrentHashMap<Project, Object>	PROJECT_LOCKS		= new java.util.concurrent.ConcurrentHashMap<>();
	private static final java.util.concurrent.ConcurrentHashMap<Project, Boolean>	LSP_PROMPTED		= new java.util.concurrent.ConcurrentHashMap<>();
	private static final java.util.concurrent.ConcurrentHashMap<Project, Boolean>	RUNTIME_PROMPTED	= new java.util.concurrent.ConcurrentHashMap<>();
	private static final com.intellij.openapi.diagnostic.Logger						LOG					= com.intellij.openapi.diagnostic.Logger
	    .getInstance( BoxLangLspBootstrapService.class );

	private BoxLangLspBootstrapService() {
	}

	public static LspBootstrapResult prepare( Project project ) throws IOException {
		LOG.info( "Preparing BoxLang LSP bootstrap" );
		synchronized ( getProjectLock( project ) ) {
			BoxLangResolvedSettings	settings		= BoxLangSettingsResolver.resolve( project );
			LspModuleInfo			lspModule		= resolveLspModule( project, settings );
			LspRequirements			requirements	= LspRequirementsReader.read( lspModule.boxJsonPath );

			String					requiredVersion	= settings.lspBoxLangVersion != null ? settings.lspBoxLangVersion
			    : ( requirements != null ? requirements.minimumBoxLangVersion : null );
			if ( requiredVersion == null || requiredVersion.isBlank() ) {
				throw new IOException( "Unable to determine required BoxLang version for LSP." );
			}

			boolean				treatAsMinimum	= settings.lspBoxLangVersion == null || settings.lspBoxLangVersion.isBlank();
			BoxLangRuntimeInfo	runtimeInfo		= BoxLangRuntimeResolver.resolveLspRuntime( requiredVersion, treatAsMinimum );
			LOG.info( "Resolved BoxLang runtime for LSP: required=" + requiredVersion + ", resolved=" + runtimeInfo.resolvedVersion );
			BoxLangRuntimeSelection	runtimeSelection	= ensureRuntime( project, runtimeInfo, settings );

			LspBootstrapResult		result				= new LspBootstrapResult();
			Path					moduleRoot			= lspModule.modulePath.getParent();
			result.lspModulePath	= moduleRoot != null ? moduleRoot : lspModule.modulePath;
			result.lspBoxLangHome	= ensureLspHome( project, settings );
			result.boxLangJarPath	= runtimeSelection.jarPath;
			result.boxLangVersion	= runtimeSelection.resolvedVersion;
			return result;
		}
	}

	/**
	 * Prepares LSP bootstrap data for editor/LSP-client startup.
	 * Returns {@code null} when the LSP module is unavailable (after showing any
	 * applicable download prompt) so callers can skip startup without surfacing errors.
	 */
	public static @Nullable LspBootstrapResult tryPrepareForLspClient( Project project ) throws IOException {
		try {
			return prepare( project );
		} catch ( LspUnavailableException unavailable ) {
			LOG.info( "Skipping LSP client startup: " + unavailable.getMessage() );
			return null;
		}
	}

	private static Path ensureLspHome( Project project, BoxLangResolvedSettings settings ) throws IOException {
		Path home = BoxLangLspHomeResolver.resolve( project, settings );
		Files.createDirectories( home );
		Files.createDirectories( home.resolve( "modules" ) );
		return home;
	}

	private static LspModuleInfo resolveLspModule( Project project, BoxLangResolvedSettings settings ) throws IOException {
		// 0. If a module path override is configured, use it directly
		if ( settings.lspModulePath != null && !settings.lspModulePath.isBlank() ) {
			Path overridePath = Path.of( settings.lspModulePath );
			if ( Files.exists( overridePath ) ) {
				LspModuleInfo info = new LspModuleInfo();
				// lspModulePath points to the folder containing bx-lsp/
				info.modulePath		= overridePath.resolve( "bx-lsp" );
				info.boxJsonPath	= LspModuleResolver.findBoxJson( info.modulePath );
				info.needsDownload	= info.boxJsonPath == null;
				if ( !info.needsDownload ) {
					return info;
				}
			}
		}

		// 1. Prefer project-local module install when available
		Path projectHome = BoxLangStoragePaths.getProjectBoxLangHome( project );
		if ( projectHome != null ) {
			Path	projectModulePath	= projectHome.resolve( "modules" ).resolve( "bx-lsp" );
			Path	projectBoxJson		= LspModuleResolver.findBoxJson( projectModulePath );
			if ( projectBoxJson != null ) {
				LspModuleInfo info = new LspModuleInfo();
				info.modulePath		= projectModulePath;
				info.boxJsonPath	= projectBoxJson;
				info.needsDownload	= false;
				LOG.info( "Using project-local LSP module at " + projectModulePath );
				return info;
			}
		}

		String lspVersion = settings.lspVersion;

		// 2. If a specific version is configured, check if it's installed
		if ( lspVersion != null && !lspVersion.isBlank() ) {
			LspModuleInfo info = LspModuleResolver.resolveForVersion( lspVersion );
			if ( !info.needsDownload ) {
				return info;
			}
		}

		// 3. No specific version configured (or not installed) - try any installed version
		if ( lspVersion == null || lspVersion.isBlank() ) {
			LspModuleInfo anyInstalled = LspModuleResolver.findAnyInstalledVersion();
			if ( anyInstalled != null && !anyInstalled.needsDownload ) {
				String resolvedVersion = ModuleVersionReader.readVersion( anyInstalled.boxJsonPath );
				LOG.info( "Using installed LSP version: " + ( resolvedVersion != null ? resolvedVersion : anyInstalled.requestedVersion ) );
				return anyInstalled;
			}
		}

		// 4. Nothing usable installed - show notification only if no explicit version is configured
		// (if the user has a version set but it's not installed, that's a misconfiguration - don't auto-prompt)
		boolean hasExplicitConfig = ( lspVersion != null && !lspVersion.isBlank() )
		    || ( settings.lspModulePath != null && !settings.lspModulePath.isBlank() );
		if ( !hasExplicitConfig && !hasPrompted( LSP_PROMPTED, project ) ) {
			LSP_PROMPTED.put( project, true );
			BoxLangPromptService.promptAndDownload(
			    project,
			    "Download BoxLang LSP",
			    "BoxLang LSP module is not installed. Would you like to download it?",
			    "BoxLang LSP",
			    ForgeBoxVersionFetcher::fetchLspVersions,
			    ( version, indicator ) -> {
				    Path targetDir = BoxLangStoragePaths.getUserBoxLangHome()
				        .resolve( "modules" )
				        .resolve( "bx-lsp" );
				    ForgeBoxLspInstaller.install( version, targetDir, indicator );
			    } );
		}

		throw new LspUnavailableException( "BoxLang LSP module is not installed." );
	}

	private static Object getProjectLock( Project project ) {
		return PROJECT_LOCKS.computeIfAbsent( project, key -> new Object() );
	}

	private static boolean hasPrompted( java.util.concurrent.ConcurrentHashMap<Project, Boolean> map, Project project ) {
		return Boolean.TRUE.equals( map.get( project ) );
	}

	private static BoxLangRuntimeSelection ensureRuntime( Project project, BoxLangRuntimeInfo runtimeInfo, BoxLangResolvedSettings settings )
	    throws IOException {
		if ( !runtimeInfo.needsDownload ) {
			BoxLangRuntimeSelection selection = new BoxLangRuntimeSelection();
			selection.jarPath			= Path.of( runtimeInfo.jarPath );
			selection.resolvedVersion	= runtimeInfo.resolvedVersion;
			return selection;
		}

		if ( !hasPrompted( RUNTIME_PROMPTED, project ) ) {
			if ( !BoxLangPromptService.confirmDownload( project,
			    "Download BoxLang Runtime",
			    "BoxLang runtime ^" + runtimeInfo.requestedVersion + " is required for the LSP. Download now?" ) ) {
				throw new IOException( "BoxLang runtime download was declined." );
			}
			RUNTIME_PROMPTED.put( project, true );
		}

		ProgressManager.getInstance().run( new DownloadTask( project, "Downloading BoxLang runtime" ) {

			@Override
			protected void runTask( @NotNull ProgressIndicator indicator ) throws IOException {
				BoxLangRuntimeInstaller.installRuntime( runtimeInfo.resolvedVersion, runtimeInfo.downloadUrl, indicator );
			}
		} );

		BoxLangRuntimeSelection selection = BoxLangRuntimeInstaller.resolveCachedJar( runtimeInfo.resolvedVersion );
		if ( selection == null ) {
			throw new IOException( "BoxLang runtime installation failed." );
		}
		return selection;
	}

	private abstract static class DownloadTask extends Task.WithResult<Void, IOException> {

		protected DownloadTask( Project project, @NlsContexts.ProgressTitle String title ) {
			super( project, title, true );
		}

		@Override
		protected Void compute( @NotNull ProgressIndicator indicator ) throws IOException {
			runTask( indicator );
			return null;
		}

		protected abstract void runTask( @NotNull ProgressIndicator indicator ) throws IOException;
	}

	public static final class LspUnavailableException extends IOException {

		public LspUnavailableException( String message ) {
			super( message );
		}
	}
}
