package com.ortussolutions.intellijboxlang.runtime;

import com.intellij.openapi.project.Project;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import org.jetbrains.annotations.Nullable;

final class ConfiguredPathResolver {

	private ConfiguredPathResolver() {
	}

	@Nullable
	static Path resolvePath( @Nullable Project project, @Nullable String configuredPath ) {
		if ( configuredPath == null || configuredPath.isBlank() ) {
			return null;
		}

		String	resolved	= configuredPath.trim();
		String	userHome	= System.getProperty( "user.home" );
		if ( userHome != null && !userHome.isBlank() ) {
			resolved = resolved.replace( "$USER_HOME$", userHome );
			if ( resolved.equals( "~" ) ) {
				resolved = userHome;
			} else if ( resolved.startsWith( "~/" ) || resolved.startsWith( "~\\" ) ) {
				resolved = userHome + resolved.substring( 1 );
			}
		}

		try {
			Path path = Path.of( resolved );
			if ( !path.isAbsolute() && project != null ) {
				String basePath = project.getBasePath();
				if ( basePath != null && !basePath.isBlank() ) {
					path = Path.of( basePath ).resolve( path );
				}
			}
			return path.normalize();
		} catch ( InvalidPathException ignored ) {
			return null;
		}
	}
}
