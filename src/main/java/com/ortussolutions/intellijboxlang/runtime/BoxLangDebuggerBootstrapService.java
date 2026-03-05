package com.ortussolutions.intellijboxlang.runtime;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.ortussolutions.intellijboxlang.settings.BoxLangResolvedSettings;
import com.ortussolutions.intellijboxlang.settings.BoxLangSettingsResolver;
import com.ortussolutions.intellijboxlang.settings.BoxLangStoragePaths;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Service responsible for ensuring the bx-debugger module is available.
 * Downloads and installs the debugger to the configured BoxLang home if needed.
 */
public final class BoxLangDebuggerBootstrapService {

	private static final Logger									LOG					= Logger.getInstance( BoxLangDebuggerBootstrapService.class );
	private static final ConcurrentHashMap<Project, Object>		PROJECT_LOCKS		= new ConcurrentHashMap<>();
	private static final ConcurrentHashMap<Project, Boolean>	DEBUGGER_PROMPTED	= new ConcurrentHashMap<>();

	private BoxLangDebuggerBootstrapService() {
	}

	/**
	 * Ensures the debugger module is available, downloading it if necessary.
	 * Returns the modules directory (the parent of {@code bx-debugger/}) to be used as
	 * {@code BOXLANG_MODULESDIRECTORY}, or null if the module cannot be resolved.
	 *
	 * <p>
	 * The debugger is now launched as a BoxLang module via {@code BoxRunner module:bx-debugger},
	 * matching how the LSP is invoked. The returned path should be passed as the
	 * {@code BOXLANG_MODULESDIRECTORY} environment variable.
	 *
	 * @param project   the current project
	 * @param bootstrap the LSP bootstrap result containing BoxLang home paths
	 *
	 * @return the modules directory containing {@code bx-debugger/}, or null if not available
	 */
	@Nullable
	public static Path ensureDebugger( @NotNull Project project, @NotNull LspBootstrapResult bootstrap ) throws IOException {
		synchronized ( getProjectLock( project ) ) {
			BoxLangResolvedSettings settings = BoxLangSettingsResolver.resolve( project );

			// 1. Check configured module path in settings
			if ( settings.debuggerModulePath != null && !settings.debuggerModulePath.isBlank() ) {
				Path	modulePath		= Path.of( settings.debuggerModulePath );
				Path	bxDebuggerDir	= modulePath.resolve( "bx-debugger" );
				if ( Files.isDirectory( bxDebuggerDir ) ) {
					LOG.info( "Using configured debugger module path: " + modulePath );
					return modulePath;
				}
				LOG.warn( "Configured debugger module path not found or missing bx-debugger: " + settings.debuggerModulePath );
			}

			// 2. Check project BoxLang home
			Path	boxLangHome	= bootstrap.lspBoxLangHome;
			Path	modulesDir	= resolveDebuggerModulesDir( boxLangHome );
			if ( modulesDir != null ) {
				LOG.info( "Using debugger module from BoxLang home: " + modulesDir );
				return modulesDir;
			}

			// 3. Check project-local .boxlang directory
			Path projectBoxLangHome = BoxLangStoragePaths.getProjectBoxLangHome( project );
			if ( projectBoxLangHome != null ) {
				modulesDir = resolveDebuggerModulesDir( projectBoxLangHome );
				if ( modulesDir != null ) {
					LOG.info( "Using debugger module from project BoxLang home: " + modulesDir );
					return modulesDir;
				}
			}

			// 4. Check user BoxLang home (~/.boxlang)
			modulesDir = resolveDebuggerModulesDir( BoxLangStoragePaths.getUserBoxLangHome() );
			if ( modulesDir != null ) {
				LOG.info( "Using debugger module from user BoxLang home: " + modulesDir );
				return modulesDir;
			}

			// 5. Debugger not found - show notification only if no explicit path is configured
			boolean hasExplicitConfig = settings.debuggerModulePath != null && !settings.debuggerModulePath.isBlank();
			if ( !hasExplicitConfig && !hasPrompted( project ) ) {
				DEBUGGER_PROMPTED.put( project, true );
				final Path targetDir = boxLangHome.resolve( "modules" ).resolve( "bx-debugger" );
				BoxLangPromptService.promptAndDownload(
				    project,
				    "Download BoxLang Debugger",
				    "BoxLang Debugger module is not installed. Would you like to download it?",
				    "BoxLang Debugger",
				    ForgeBoxVersionFetcher::fetchDebuggerVersions,
				    ( version, indicator ) -> ForgeBoxDebuggerInstaller.install( version, targetDir, indicator ) );
			}

			return null;
		}
	}

	/**
	 * Returns the modules directory (parent of {@code bx-debugger/}) if the module is installed
	 * in the given BoxLang home, or null if not present.
	 */
	@Nullable
	private static Path resolveDebuggerModulesDir( @Nullable Path boxLangHome ) {
		if ( boxLangHome == null || !Files.exists( boxLangHome ) ) {
			return null;
		}

		DebuggerModuleInfo moduleInfo = DebuggerModuleResolver.resolve( boxLangHome, null );
		if ( moduleInfo.needsDownload || moduleInfo.modulePath == null ) {
			return null;
		}

		// Return the modules directory (parent of bx-debugger/)
		Path modulesDir = moduleInfo.modulePath.getParent();
		return modulesDir != null ? modulesDir : null;
	}

	private static Object getProjectLock( @NotNull Project project ) {
		return PROJECT_LOCKS.computeIfAbsent( project, key -> new Object() );
	}

	private static boolean hasPrompted( @NotNull Project project ) {
		return Boolean.TRUE.equals( DEBUGGER_PROMPTED.get( project ) );
	}

	/**
	 * Clears the prompted state for a project (useful for testing or re-prompting).
	 */
	public static void clearPromptedState( @NotNull Project project ) {
		DEBUGGER_PROMPTED.remove( project );
	}
}
