package com.ortussolutions.intellijboxlang.runtime;

import com.intellij.mock.MockProject;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public class BoxLangRuntimePromptTest extends BasePlatformTestCase {

	public void testOnlyOneAutomaticPromptPerProjectSession() {
		var project = new MockProject( null, getTestRootDisposable() );
		assertTrue( BoxLangLspBootstrapService.claimRuntimePrompt( project ) );
		for ( int i = 0; i < 10; i++ )
			assertFalse( BoxLangLspBootstrapService.claimRuntimePrompt( project ) );
	}

	public void testNewProjectCanPromptIndependently() {
		assertTrue( BoxLangLspBootstrapService.claimRuntimePrompt( new MockProject( null, getTestRootDisposable() ) ) );
		assertTrue( BoxLangLspBootstrapService.claimRuntimePrompt( new MockProject( null, getTestRootDisposable() ) ) );
	}
}
