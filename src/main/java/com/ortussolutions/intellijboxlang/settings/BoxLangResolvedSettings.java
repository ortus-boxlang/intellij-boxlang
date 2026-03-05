package com.ortussolutions.intellijboxlang.settings;

public class BoxLangResolvedSettings {

	public String	boxLangVersion;
	public String	boxLangJarPath;
	public String	boxLangHome;
	public String	javaHome;
	public String	lspVersion;
	public String	lspBoxLangVersion;
	public String	lspBoxLangHome;
	public String	lspModules;
	public String	lspJvmArgs;
	/** Override folder path containing the bx-lsp module (bypasses managed install). */
	public String	lspModulePath;
	public int		lspMaxHeapSize;
	public String	debuggerVersion;
	/** Override folder path containing the bx-debugger module (bypasses managed install). */
	public String	debuggerModulePath;
	public boolean	useBvmrc;
}
