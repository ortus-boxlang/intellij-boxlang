package com.ortussolutions.intellijboxlang.lsp;

import com.intellij.testFramework.LightVirtualFile;
import junit.framework.TestCase;

public class BoxLangLspClientServiceTest extends TestCase {

	public void testSafeToUri_returnsNullForLightVirtualFile() {
		LightVirtualFile file = new LightVirtualFile( "BoxLang Debug Console", "println('hi')" );
		assertNull( BoxLangLspClientService.safeToUri( file ) );
	}

	public void testSafeToUri_handlesNullInput() {
		assertNull( BoxLangLspClientService.safeToUri( null ) );
	}
}
