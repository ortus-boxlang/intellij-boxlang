package com.ortussolutions.intellijboxlang.runtime;

import org.jetbrains.annotations.Nullable;

/**
 * Holds information about an installed module's status.
 */
public final class InstalledModuleStatus {

	public final boolean	installed;
	public final String		version;
	public final String		path;
	/** The latest version available on ForgeBox, or null if not yet fetched. */
	public final String		latestVersion;

	private InstalledModuleStatus( boolean installed, @Nullable String version, @Nullable String path,
	    @Nullable String latestVersion ) {
		this.installed		= installed;
		this.version		= version;
		this.path			= path;
		this.latestVersion	= latestVersion;
	}

	public static InstalledModuleStatus notInstalled() {
		return new InstalledModuleStatus( false, null, null, null );
	}

	public static InstalledModuleStatus notInstalled( @Nullable String latestVersion ) {
		return new InstalledModuleStatus( false, null, null, latestVersion );
	}

	public static InstalledModuleStatus installed( @Nullable String version, @Nullable String path ) {
		return new InstalledModuleStatus( true, version, path, null );
	}

	public static InstalledModuleStatus installed( @Nullable String version, @Nullable String path,
	    @Nullable String latestVersion ) {
		return new InstalledModuleStatus( true, version, path, latestVersion );
	}

	/**
	 * Returns true if there is a newer version available on ForgeBox.
	 */
	public boolean isOutdated() {
		return installed && latestVersion != null && version != null && !latestVersion.equals( version );
	}
}
