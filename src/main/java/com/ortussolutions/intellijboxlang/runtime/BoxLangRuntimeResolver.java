package com.ortussolutions.intellijboxlang.runtime;

import com.intellij.openapi.project.Project;
import com.ortussolutions.intellijboxlang.settings.BoxLangResolvedSettings;
import java.io.IOException;
import java.nio.file.Path;

public final class BoxLangRuntimeResolver {

	private BoxLangRuntimeResolver() {
	}

	public static BoxLangRuntimeInfo resolve( Project project, BoxLangResolvedSettings settings ) {
		BoxLangRuntimeInfo	info				= new BoxLangRuntimeInfo();

		String				requestedVersion	= settings.boxLangVersion;
		if ( settings.useBvmrc ) {
			String bvmrcVersion = BoxLangBvmrcService.findVersion( project );
			if ( bvmrcVersion != null ) {
				requestedVersion = bvmrcVersion;
			}
		}

		return resolveRuntime( requestedVersion, settings.boxLangJarPath, false );
	}

	public static BoxLangRuntimeInfo resolveLspRuntime( String requestedVersion, boolean treatAsMinimum ) {
		return resolveRuntime( requestedVersion, null, treatAsMinimum );
	}

	private static BoxLangRuntimeInfo resolveRuntime( String requestedVersion, String jarPathOverride, boolean treatAsMinimum ) {
		BoxLangRuntimeInfo info = new BoxLangRuntimeInfo();
		info.requestedVersion	= requestedVersion;
		info.resolvedVersion	= requestedVersion;

		if ( jarPathOverride != null ) {
			info.jarPath		= jarPathOverride;
			info.needsDownload	= false;
			return info;
		}

		if ( requestedVersion == null || requestedVersion.isBlank() ) {
			info.needsDownload = true;
			return info;
		}

		try {
			BoxLangVersionInfo versionInfo = treatAsMinimum
			    ? BoxLangVersionCatalog.resolveLatestAtLeastInfo( requestedVersion )
			    : BoxLangVersionCatalog.resolveVersionInfo( requestedVersion );
			if ( versionInfo == null ) {
				info.needsDownload = true;
				return info;
			}
			info.resolvedVersion	= versionInfo.name();
			info.downloadUrl		= versionInfo.downloadUrl();
			BoxLangRuntimeSelection selection = BoxLangRuntimeInstaller.resolveCachedJar( info.resolvedVersion );
			if ( selection != null ) {
				info.jarPath			= selection.jarPath.toString();
				info.resolvedVersion	= selection.resolvedVersion;
				info.needsDownload		= false;
				return info;
			}
		} catch ( IOException ignored ) {
			info.needsDownload = true;
			return info;
		}

		info.needsDownload = true;
		return info;
	}
}
