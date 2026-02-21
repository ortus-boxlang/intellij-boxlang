package com.ortussolutions.intellijboxlang.settings;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public class BoxLangProjectConfigurableTest extends BasePlatformTestCase {

	public void testClearOverridesClearsDebuggerJarPath() {
		BoxLangSettingsState defaults = new BoxLangSettingsState();
		defaults.lspMaxHeapSize		= 1024;
		defaults.useBvmrc			= false;
		defaults.promptForDownloads	= false;

		BoxLangProjectSettingsState state = new BoxLangProjectSettingsState();
		state.boxLangVersion	= "1.0.0";
		state.boxLangJarPath	= "/project/boxlang.jar";
		state.boxLangHome		= "/project/home";
		state.javaHome			= "/project/java";
		state.lspVersion		= "2.0.0";
		state.lspBoxLangVersion	= "2.0.0";
		state.lspBoxLangHome	= "/project/lsp-home";
		state.lspModules		= "/project/modules";
		state.lspJvmArgs		= "-Xmx2g";
		state.debuggerJarPath	= "/project/debugger.jar";

		BoxLangProjectConfigurable.clearOverrides( state, defaults );

		assertNull( state.boxLangVersion );
		assertNull( state.boxLangJarPath );
		assertNull( state.boxLangHome );
		assertNull( state.javaHome );
		assertNull( state.lspVersion );
		assertNull( state.lspBoxLangVersion );
		assertNull( state.lspBoxLangHome );
		assertNull( state.lspModules );
		assertNull( state.lspJvmArgs );
		assertNull( state.debuggerJarPath );
		assertEquals( 1024, state.lspMaxHeapSize );
		assertFalse( state.useBvmrc );
		assertFalse( state.promptForDownloads );
	}

	public void testMergeWithDefaultsIncludesDebuggerJarPath() {
		BoxLangSettingsState defaults = new BoxLangSettingsState();
		defaults.debuggerJarPath	= "/global/debugger.jar";
		defaults.lspMaxHeapSize		= 512;
		defaults.useBvmrc			= true;
		defaults.promptForDownloads	= true;

		BoxLangProjectSettingsState	state				= new BoxLangProjectSettingsState();

		BoxLangSettingsState		mergedWithGlobal	= BoxLangProjectConfigurable.mergeWithDefaults( state, defaults );
		assertEquals( "/global/debugger.jar", mergedWithGlobal.debuggerJarPath );

		state.debuggerJarPath = "/project/debugger.jar";
		BoxLangSettingsState mergedWithProject = BoxLangProjectConfigurable.mergeWithDefaults( state, defaults );
		assertEquals( "/project/debugger.jar", mergedWithProject.debuggerJarPath );
	}
}
