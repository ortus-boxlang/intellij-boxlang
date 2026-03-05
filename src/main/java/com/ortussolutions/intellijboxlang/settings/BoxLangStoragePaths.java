package com.ortussolutions.intellijboxlang.settings;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.Project;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class BoxLangStoragePaths {

	private static final String ROOT_DIR_NAME = "boxlang";

	private BoxLangStoragePaths() {
	}

	public static Path getCacheRoot() {
		return Paths.get( PathManager.getSystemPath(), ROOT_DIR_NAME );
	}

	public static Path getLspCacheRoot() {
		return getCacheRoot().resolve( "lsp" );
	}

	public static Path getRuntimeCacheRoot() {
		return getCacheRoot().resolve( "runtime" );
	}

	/**
	 * Returns the global user BoxLang home directory (~/.boxlang on Unix/macOS,
	 * %USERPROFILE%\.boxlang on Windows). This is where globally installed modules live.
	 */
	public static Path getUserBoxLangHome() {
		return Path.of( System.getProperty( "user.home" ), ".boxlang" );
	}

	/**
	 * Returns the per-project BoxLang home directory (&lt;projectDir&gt;/.boxlang).
	 * This is where project-local module installs are stored.
	 *
	 * @param project the IntelliJ project
	 * 
	 * @return the project-local .boxlang directory, or null if the project has no base path
	 */
	public static Path getProjectBoxLangHome( Project project ) {
		String basePath = project.getBasePath();
		if ( basePath == null ) {
			return null;
		}
		return Path.of( basePath, ".boxlang" );
	}
}
