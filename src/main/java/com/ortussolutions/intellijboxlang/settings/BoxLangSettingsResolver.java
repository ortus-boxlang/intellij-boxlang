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
		resolved.lspMaxHeapSize		= appState.lspMaxHeapSize;
		resolved.debuggerVersion	= appState.debuggerVersion;
		resolved.debuggerJarPath	= appState.debuggerJarPath;
		resolved.useBvmrc			= appState.useBvmrc;
		resolved.promptForDownloads	= appState.promptForDownloads;

		return resolved;
	}

	public static BoxLangResolvedSettings resolve( Project project ) {
		BoxLangSettingsState		appState		= BoxLangApplicationSettings.getInstance().getSettings();
		BoxLangProjectSettingsState	projectState	= BoxLangProjectSettings.getInstance( project ).getSettings();
		BoxLangResolvedSettings		resolved		= new BoxLangResolvedSettings();

		if ( projectState.useProjectSettings ) {
			resolved.boxLangVersion		= orFallback( projectState.boxLangVersion, appState.boxLangVersion );
			resolved.boxLangJarPath		= orFallback( projectState.boxLangJarPath, appState.boxLangJarPath );
			resolved.boxLangHome		= orFallback( projectState.boxLangHome, appState.boxLangHome );
			resolved.javaHome			= orFallback( projectState.javaHome, appState.javaHome );
			resolved.lspVersion			= orFallback( projectState.lspVersion, appState.lspVersion );
			resolved.lspBoxLangVersion	= orFallback( projectState.lspBoxLangVersion, appState.lspBoxLangVersion );
			resolved.lspBoxLangHome		= orFallback( projectState.lspBoxLangHome, appState.lspBoxLangHome );
			resolved.lspModules			= orFallback( projectState.lspModules, appState.lspModules );
			resolved.lspJvmArgs			= orFallback( projectState.lspJvmArgs, appState.lspJvmArgs );
			resolved.lspMaxHeapSize		= projectState.lspMaxHeapSize != 0 ? projectState.lspMaxHeapSize : appState.lspMaxHeapSize;
			resolved.debuggerVersion	= orFallback( projectState.debuggerVersion, appState.debuggerVersion );
			resolved.debuggerJarPath	= orFallback( projectState.debuggerJarPath, appState.debuggerJarPath );
			resolved.useBvmrc			= projectState.useBvmrc;
			resolved.promptForDownloads	= projectState.promptForDownloads;
		} else {
			resolved.boxLangVersion		= appState.boxLangVersion;
			resolved.boxLangJarPath		= appState.boxLangJarPath;
			resolved.boxLangHome		= appState.boxLangHome;
			resolved.javaHome			= appState.javaHome;
			resolved.lspVersion			= appState.lspVersion;
			resolved.lspBoxLangVersion	= appState.lspBoxLangVersion;
			resolved.lspBoxLangHome		= appState.lspBoxLangHome;
			resolved.lspModules			= appState.lspModules;
			resolved.lspJvmArgs			= appState.lspJvmArgs;
			resolved.lspMaxHeapSize		= appState.lspMaxHeapSize;
			resolved.debuggerVersion	= appState.debuggerVersion;
			resolved.debuggerJarPath	= appState.debuggerJarPath;
			resolved.useBvmrc			= appState.useBvmrc;
			resolved.promptForDownloads	= appState.promptForDownloads;
		}

		return resolved;
	}

	private static String orFallback( String primary, String fallback ) {
		return primary != null ? primary : fallback;
	}
}
