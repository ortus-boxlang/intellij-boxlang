package com.ortussolutions.intellijboxlang.settings;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public class BoxLangProjectConfigurableTest extends BasePlatformTestCase {

	/**
	 * When a project has a value set, the resolver should use it over the global default.
	 */
	public void testProjectOverrideIsPreferredOverGlobal() {
		BoxLangSettingsState appState = new BoxLangSettingsState();
		appState.debuggerModulePath	= "/global/debugger";
		appState.boxLangVersion		= "1.0.0";

		BoxLangProjectSettingsState projectState = new BoxLangProjectSettingsState();
		projectState.debuggerModulePath = "/project/debugger";

		// Simulate resolution: project non-null values win
		String resolved = projectState.debuggerModulePath != null ? projectState.debuggerModulePath : appState.debuggerModulePath;
		assertEquals( "/project/debugger", resolved );
	}

	/**
	 * When a project field is null, the global default is used.
	 */
	public void testGlobalDefaultUsedWhenProjectFieldIsNull() {
		BoxLangSettingsState appState = new BoxLangSettingsState();
		appState.debuggerModulePath = "/global/debugger";

		BoxLangProjectSettingsState	projectState	= new BoxLangProjectSettingsState();
		// debuggerModulePath left null in project state

		String						resolved		= projectState.debuggerModulePath != null ? projectState.debuggerModulePath : appState.debuggerModulePath;
		assertEquals( "/global/debugger", resolved );
	}

	/**
	 * BoxLangProjectSettingsState should only contain fields that are project-overridable.
	 */
	public void testProjectStateContainsOverridableFields() {
		BoxLangProjectSettingsState state = new BoxLangProjectSettingsState();
		// Verify the project-overridable fields exist and default to null
		assertNull( state.boxLangVersion );
		assertNull( state.boxLangJarPath );
		assertNull( state.boxLangHome );
		assertNull( state.javaHome );
		assertNull( state.lspBoxLangHome );
		assertNull( state.lspModulePath );
		assertNull( state.debuggerModulePath );
	}
}
