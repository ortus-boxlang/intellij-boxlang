package com.ortussolutions.intellijboxlang.runtime;

import java.nio.file.Path;

public class DebuggerModuleInfo {

	public String	requestedVersion;
	public Path		modulePath;
	public Path		boxJsonPath;
	public boolean	needsDownload;
}
