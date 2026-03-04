package com.ortussolutions.intellijboxlang.settings;

/**
 * Persisted per-project settings overrides for BoxLang.
 * Only fields that are meaningful to override per-project are included.
 * All other settings are controlled globally via {@link BoxLangApplicationSettings}.
 */
public class BoxLangProjectSettingsState {

	/** Override the BoxLang runtime version for this project. */
	public String	boxLangVersion;
	/** Override the BoxLang jar path for this project. */
	public String	boxLangJarPath;
	/** Override the BoxLang home directory for this project. */
	public String	boxLangHome;
	/** Override the Java home for this project. */
	public String	javaHome;
	/** Override the LSP BoxLang version for this project. */
	public String	lspBoxLangVersion;
	/** Override the LSP BoxLang home directory for this project. */
	public String	lspBoxLangHome;
	/** Override the extra modules loaded by the LSP for this project. */
	public String	lspModules;
	/** Override the LSP JVM args for this project. */
	public String	lspJvmArgs;
	/** Override the LSP jar path for this project. */
	public String	lspModulePath;
	/** Override the LSP max heap size (MB) for this project. 0 = inherit global. */
	public int		lspMaxHeapSize;
	/** Override the Debugger jar path for this project. */
	public String	debuggerModulePath;
	/** Override the .bvmrc usage setting for this project. null = inherit global. */
	public Boolean	useBvmrc;
}
