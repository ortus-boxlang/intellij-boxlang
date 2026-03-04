package com.ortussolutions.intellijboxlang.settings;

public class BoxLangSettingsState {

	public String	boxLangVersion;
	public String	boxLangJarPath;
	public String	boxLangHome;
	public String	javaHome;
	public String	lspVersion;
	public String	lspBoxLangVersion;
	public String	lspBoxLangHome;
	public String	lspModules;
	public String	lspJvmArgs;
	public int		lspMaxHeapSize		= 512;
	public String	debuggerVersion;
	public String	debuggerJarPath;
	public boolean	useBvmrc			= true;
	public boolean	promptForDownloads	= true;
}
