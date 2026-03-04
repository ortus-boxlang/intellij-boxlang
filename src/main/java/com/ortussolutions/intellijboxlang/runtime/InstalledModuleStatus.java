package com.ortussolutions.intellijboxlang.runtime;

import org.jetbrains.annotations.Nullable;

/**
 * Holds information about an installed module's status.
 */
public final class InstalledModuleStatus {

	public final boolean	installed;
	public final String		version;
	public final String		path;

	private InstalledModuleStatus( boolean installed, @Nullable String version, @Nullable String path ) {
		this.installed	= installed;
		this.version	= version;
		this.path		= path;
	}

	public static InstalledModuleStatus notInstalled() {
		return new InstalledModuleStatus( false, null, null );
	}

	public static InstalledModuleStatus installed( @Nullable String version, @Nullable String path ) {
		return new InstalledModuleStatus( true, version, path );
	}
}
