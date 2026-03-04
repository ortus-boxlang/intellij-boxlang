package com.ortussolutions.intellijboxlang.runtime;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.ortussolutions.intellijboxlang.settings.BoxLangResolvedSettings;
import com.ortussolutions.intellijboxlang.settings.BoxLangSettingsResolver;
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
	 * Returns the path to the debugger JAR, or null if it cannot be resolved.
	 *
	 * @param project   the current project
	 * @param bootstrap the LSP bootstrap result containing BoxLang home paths
	 * 
	 * @return the path to the debugger JAR, or null if not available
	 */
	@Nullable
	public static Path ensureDebugger( @NotNull Project project, @NotNull LspBootstrapResult bootstrap ) throws IOException {
		synchronized ( getProjectLock( project ) ) {
			BoxLangResolvedSettings settings = BoxLangSettingsResolver.resolve( project );

			// 1. Check configured path in settings
			if ( settings.debuggerJarPath != null && !settings.debuggerJarPath.isBlank() ) {
				Path configuredPath = Path.of( settings.debuggerJarPath );
				if ( Files.exists( configuredPath ) ) {
					LOG.info( "Using configured debugger JAR: " + configuredPath );
					return configuredPath;
				}
				LOG.warn( "Configured debugger JAR not found: " + settings.debuggerJarPath );
			}

			// 2. Check project BoxLang home
			Path	boxLangHome	= bootstrap.lspBoxLangHome;
			Path	debuggerJar	= resolveDebuggerInHome( boxLangHome );
			if ( debuggerJar != null ) {
				LOG.info( "Using debugger JAR from BoxLang home: " + debuggerJar );
				return debuggerJar;
			}

			// 3. Check user BoxLang home (~/.boxlang)
			String	userHomeStr		= System.getProperty( "user.home" );
			Path	userBoxLangHome	= Path.of( userHomeStr, ".boxlang" );
			debuggerJar = resolveDebuggerInHome( userBoxLangHome );
			if ( debuggerJar != null ) {
				LOG.info( "Using debugger JAR from user BoxLang home: " + debuggerJar );
				return debuggerJar;
			}

			// 4. Debugger not found - offer to download (use configured version or latest)
			String	debuggerVersion	= settings.debuggerVersion;
			String	versionDisplay	= ( debuggerVersion == null || debuggerVersion.isBlank() ) ? "latest" : debuggerVersion;

			// Prompt for download
			if ( settings.promptForDownloads && !hasPrompted( project ) ) {
				if ( !BoxLangPromptService.confirmDownload( project,
				    "Download BoxLang Debugger",
				    "BoxLang Debugger module (" + versionDisplay + ") is not installed. Download now?" ) ) {
					LOG.info( "Debugger download was declined by user" );
					return null;
				}
				DEBUGGER_PROMPTED.put( project, true );
			}

			// Download to project BoxLang home
			Path targetDir = boxLangHome.resolve( "modules" ).resolve( "bx-debugger" );
			downloadDebugger( project, debuggerVersion, targetDir );

			// Verify installation
			debuggerJar = resolveDebuggerInHome( boxLangHome );
			if ( debuggerJar == null ) {
				throw new IOException( "BoxLang Debugger module installation failed." );
			}

			LOG.info( "Debugger installed successfully: " + debuggerJar );
			return debuggerJar;
		}
	}

	/**
	 * Resolves the debugger JAR within a BoxLang home directory.
	 */
	@Nullable
	private static Path resolveDebuggerInHome( @Nullable Path boxLangHome ) {
		if ( boxLangHome == null || !Files.exists( boxLangHome ) ) {
			return null;
		}

		DebuggerModuleInfo moduleInfo = DebuggerModuleResolver.resolve( boxLangHome, null );
		if ( moduleInfo.needsDownload ) {
			return null;
		}

		return DebuggerModuleResolver.findDebuggerJar( moduleInfo.modulePath );
	}

	/**
	 * Downloads and installs the debugger module.
	 */
	private static void downloadDebugger( @NotNull Project project, @NotNull String version, @NotNull Path targetDir ) throws IOException {
		ProgressManager.getInstance().run( new Task.WithResult<Void, IOException>( project, "Downloading BoxLang Debugger", true ) {

			@Override
			protected Void compute( @NotNull ProgressIndicator indicator ) throws IOException {
				LOG.info( "Downloading bx-debugger " + version + " to " + targetDir );
				ForgeBoxDebuggerInstaller.install( version, targetDir, indicator );
				return null;
			}
		} );
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
