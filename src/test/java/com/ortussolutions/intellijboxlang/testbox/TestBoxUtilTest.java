package com.ortussolutions.intellijboxlang.testbox;

import com.intellij.openapi.util.SystemInfo;
import junit.framework.TestCase;

public class TestBoxUtilTest extends TestCase {

	public void testRelativizeToProjectBase_normalizesSeparators() {
		String relative = TestBoxUtil.relativizeToProjectBase(
		    "C:\\repo\\project",
		    "C:/repo/project/tests/specs/MySpec.bx"
		);
		assertEquals( "tests/specs/MySpec.bx", relative );
	}

	public void testRelativizeToProjectBase_rejectsPrefixCollision() {
		String relative = TestBoxUtil.relativizeToProjectBase(
		    "/repo/project",
		    "/repo/project-2/tests/specs/MySpec.bx"
		);
		assertNull( relative );
	}

	public void testRelativizeToProjectBase_windowsCaseInsensitive() {
		if ( !SystemInfo.isWindows ) {
			return;
		}
		String relative = TestBoxUtil.relativizeToProjectBase(
		    "C:/Users/Pete/Repo",
		    "c:/users/pete/repo/tests/specs/MySpec.bx"
		);
		assertEquals( "tests/specs/MySpec.bx", relative );
	}
}
