package com.ortussolutions.intellijboxlang.settings;

import com.intellij.openapi.project.Project;

public final class BoxLangSettingsResolver {

	private BoxLangSettingsResolver() {
	}

	public static BoxLangResolvedSettings resolveGlobal() {
		BoxLangSettingsState	appState	= BoxLangApplicationSettings.getInstance().getSettings();
		BoxLangResolvedSettings	resolved	= new BoxLangResolvedSettings();

		resolved.boxLangVersion		= appState.boxLangVersion;
		resolved.boxLangJarPath		= appState.boxLangJarPath;
		resolved.boxLangHome		= appState.boxLangHome;
		resolved.javaHome			= appState.javaHome;
		resolved.lspVersion			= appState.lspVersion;
		resolved.lspBoxLangVersion	= appState.lspBoxLangVersion;
		resolved.lspBoxLangHome		= appState.lspBoxLangHome;
		resolved.lspModules			= appState.lspModules;
		resolved.lspJvmArgs			= appState.lspJvmArgs;
		resolved.lspModulePath		= appState.lspModulePath;
		resolved.lspMaxHeapSize		= appState.lspMaxHeapSize;
		resolved.debuggerVersion	= appState.debuggerVersion;
		resolved.debuggerModulePath	= appState.debuggerModulePath;
		resolved.useBvmrc			= appState.useBvmrc;

		return resolved;
	}

	/**
	 * Resolves effective settings for a project by merging global defaults with any
	 * project-level overrides. A project field overrides the global value whenever it is
	 * non-null and non-blank.
	 */
	public static BoxLangResolvedSettings resolve( Project project ) {
		BoxLangSettingsState		appState		= BoxLangApplicationSettings.getInstance().getSettings();
		BoxLangProjectSettingsState	projectState	= BoxLangProjectSettings.getInstance( project ).getSettings();
		BoxLangResolvedSettings		resolved		= new BoxLangResolvedSettings();

		// Per-project overridable fields
		resolved.boxLangVersion		= orFallback( projectState.boxLangVersion, appState.boxLangVersion );
		resolved.boxLangJarPath		= orFallback( projectState.boxLangJarPath, appState.boxLangJarPath );
		resolved.boxLangHome		= orFallback( projectState.boxLangHome, appState.boxLangHome );
		resolved.javaHome			= orFallback( projectState.javaHome, appState.javaHome );
		resolved.lspBoxLangVersion	= orFallback( projectState.lspBoxLangVersion, appState.lspBoxLangVersion );
		resolved.lspBoxLangHome		= orFallback( projectState.lspBoxLangHome, appState.lspBoxLangHome );
		resolved.lspModules			= orFallback( projectState.lspModules, appState.lspModules );
		resolved.lspJvmArgs			= orFallback( projectState.lspJvmArgs, appState.lspJvmArgs );
		resolved.lspModulePath		= orFallback( projectState.lspModulePath, appState.lspModulePath );
		resolved.lspMaxHeapSize		= projectState.lspMaxHeapSize > 0 ? projectState.lspMaxHeapSize : appState.lspMaxHeapSize;
		resolved.debuggerModulePath	= orFallback( projectState.debuggerModulePath, appState.debuggerModulePath );
		resolved.useBvmrc			= projectState.useBvmrc != null ? projectState.useBvmrc : appState.useBvmrc;

		// Global-only fields (not overridable per-project)
		resolved.lspVersion			= appState.lspVersion;
		resolved.debuggerVersion	= appState.debuggerVersion;

		return resolved;
	}

	private static String orFallback( String primary, String fallback ) {
		return ( primary != null && !primary.isBlank() ) ? primary : fallback;
	}
}
