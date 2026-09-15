package com.ortussolutions.intellijboxlang.runtime;

import com.intellij.openapi.project.Project;
import com.ortussolutions.intellijboxlang.settings.BoxLangResolvedSettings;
import com.ortussolutions.intellijboxlang.settings.BoxLangSettingsResolver;
import com.ortussolutions.intellijboxlang.settings.BoxLangStoragePaths;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jetbrains.annotations.Nullable;

public final class BoxLangLspBootstrapService {

	private static final com.intellij.openapi.util.Key<Object>										PROJECT_LOCK		= com.intellij.openapi.util.Key
	    .create( "boxlang.bootstrap.lock" );
	private static final com.intellij.openapi.util.Key<Boolean>										LSP_PROMPTED		= com.intellij.openapi.util.Key
	    .create( "boxlang.lsp.prompted" );
	private static final com.intellij.openapi.util.Key<java.util.concurrent.atomic.AtomicBoolean>	RUNTIME_PROMPTED	= com.intellij.openapi.util.Key
	    .create( "boxlang.runtime.download.prompted" );
	private static final com.intellij.openapi.diagnostic.Logger										LOG					= com.intellij.openapi.diagnostic.Logger
	    .getInstance( BoxLangLspBootstrapService.class );

	private BoxLangLspBootstrapService() {
	}

	public static LspBootstrapResult prepare( Project project ) throws IOException {
		LOG.info( "Preparing BoxLang LSP bootstrap" );
		synchronized ( getProjectLock( project ) ) {
			BoxLangResolvedSettings	settings	= BoxLangSettingsResolver.resolve( project );
			LspModuleInfo			lspModule	= resolveLspModule( project, settings );
			BoxLangToolingStatus.get( project ).record( "BoxLang LSP module", BoxLangToolingStatus.Phase.INSTALLED,
			    ModuleVersionReader.readVersion( lspModule.boxJsonPath ) + " at " + lspModule.modulePath );
			LspRequirements	requirements	= LspRequirementsReader.read( lspModule.boxJsonPath );

			String			requiredVersion	= settings.lspBoxLangVersion != null ? settings.lspBoxLangVersion
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
			BoxLangToolingStatus.get( project ).record( "BoxLang language server", BoxLangToolingStatus.Phase.WAITING, unavailable.getMessage() );
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
			Path overridePath = ConfiguredPathResolver.resolvePath( project, settings.lspModulePath );
			if ( overridePath != null ) {
				Path modulePath = LspModuleResolver.resolveOverrideModulePath( overridePath );
				if ( modulePath != null ) {
					Path boxJsonPath = LspModuleResolver.findBoxJson( modulePath );
					if ( boxJsonPath != null ) {
						LspModuleInfo info = new LspModuleInfo();
						info.modulePath		= modulePath;
						info.boxJsonPath	= boxJsonPath;
						info.needsDownload	= false;
						LOG.info( "Using LSP module override at " + modulePath );
						return info;
					}
				}
				throw new IOException(
				    "Configured LSP module path does not contain a bx-lsp module: " + overridePath + ". Correct the module override in BoxLang settings." );
			} else {
				throw new IOException( "Configured LSP module path is invalid. Correct the module override in BoxLang settings." );
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
		if ( hasExplicitConfig )
			throw new IOException( "The configured LSP module is not installed. Check the LSP version and module override in BoxLang settings." );
		if ( !Boolean.TRUE.equals( project.getUserData( LSP_PROMPTED ) ) ) {
			project.putUserData( LSP_PROMPTED, true );
			BoxLangToolingStatus.get( project ).record( "BoxLang LSP module", BoxLangToolingStatus.Phase.WAITING,
			    "Choose Download in the LSP notification or use BoxLang settings." );
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
		synchronized ( project ) {
			Object lock = project.getUserData( PROJECT_LOCK );
			if ( lock == null ) {
				lock = new Object();
				project.putUserData( PROJECT_LOCK, lock );
			}
			return lock;
		}
	}

	private static BoxLangRuntimeSelection ensureRuntime( Project project, BoxLangRuntimeInfo runtimeInfo, BoxLangResolvedSettings settings )
	    throws IOException {
		if ( !runtimeInfo.needsDownload ) {
			BoxLangRuntimeSelection selection = new BoxLangRuntimeSelection();
			selection.jarPath			= Path.of( runtimeInfo.jarPath );
			selection.resolvedVersion	= runtimeInfo.resolvedVersion;
			BoxLangToolingStatus.get( project ).record( "BoxLang runtime", BoxLangToolingStatus.Phase.INSTALLED,
			    runtimeInfo.resolvedVersion + " at " + runtimeInfo.jarPath );
			return selection;
		}

		if ( claimRuntimePrompt( project ) ) {
			BoxLangToolingStatus.get( project ).record( "BoxLang runtime", BoxLangToolingStatus.Phase.WAITING,
			    "A runtime is required for the LSP. Choose Download or use BoxLang settings." );
			BoxLangPromptService.promptDownload( project, "Download BoxLang Runtime",
			    "BoxLang runtime " + runtimeInfo.requestedVersion
			        + " is required for the LSP. Download now? You can also download it later from BoxLang settings.",
			    () -> BoxLangSetupTasks.download( project, "BoxLang runtime",
			        indicator -> {
				        // Re-resolve on each explicit attempt so a failed catalog lookup or stale URL is recoverable.
				        BoxLangVersionInfo available = settings.lspBoxLangVersion == null || settings.lspBoxLangVersion.isBlank()
				            ? BoxLangVersionCatalog.resolveLatestAtLeastInfo( runtimeInfo.requestedVersion )
				            : BoxLangVersionCatalog.resolveVersionInfo( runtimeInfo.requestedVersion );
				        if ( available == null )
					        throw new IOException(
					            "No runtime download matches " + runtimeInfo.requestedVersion + ". Check the LSP runtime version in BoxLang settings." );
				        BoxLangRuntimeInstaller.installRuntime( available.name(), available.downloadUrl(), indicator );
			        },
			        () -> com.ortussolutions.intellijboxlang.lsp.BoxLangLspClientService.getInstance( project ).retryStartup() ) );
		}
		throw new LspUnavailableException( "BoxLang runtime is not installed. Use the download notification or BoxLang settings." );
	}

	static boolean claimRuntimePrompt( Project project ) {
		java.util.concurrent.atomic.AtomicBoolean prompted;
		synchronized ( project ) {
			prompted = project.getUserData( RUNTIME_PROMPTED );
			if ( prompted == null ) {
				prompted = new java.util.concurrent.atomic.AtomicBoolean();
				project.putUserData( RUNTIME_PROMPTED, prompted );
			}
		}
		return prompted.compareAndSet( false, true );
	}

	public static final class LspUnavailableException extends IOException {

		public LspUnavailableException( String message ) {
			super( message );
		}
	}
}
