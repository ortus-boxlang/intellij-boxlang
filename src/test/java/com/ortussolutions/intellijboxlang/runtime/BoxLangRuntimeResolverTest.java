package com.ortussolutions.intellijboxlang.runtime;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;

/**
 * Tests for version comparison logic in BoxLangRuntimeResolver.
 *
 * Specifically guards against the lexicographic comparison bug where "1.11.0" would
 * incorrectly compare as less than "1.6.0" because "1" < "6" as strings.
 */
public class BoxLangRuntimeResolverTest extends BasePlatformTestCase {

	// -------------------------------------------------------------------------
	// compareVersions
	// -------------------------------------------------------------------------

	public void testCompareVersions_equal() {
		assertEquals( 0, BoxLangRuntimeResolver.compareVersions( "1.6.0", "1.6.0" ) );
	}

	public void testCompareVersions_majorGreater() {
		assertTrue( BoxLangRuntimeResolver.compareVersions( "2.0.0", "1.11.0" ) > 0 );
	}

	public void testCompareVersions_majorLess() {
		assertTrue( BoxLangRuntimeResolver.compareVersions( "1.0.0", "2.0.0" ) < 0 );
	}

	/**
	 * The key regression: 1.11.0 must be treated as greater than 1.6.0,
	 * not less (which lexicographic comparison would give).
	 */
	public void testCompareVersions_minorDoubleDigitGreaterThanSingleDigit() {
		assertTrue( BoxLangRuntimeResolver.compareVersions( "1.11.0", "1.6.0" ) > 0 );
	}

	public void testCompareVersions_patchDifference() {
		assertTrue( BoxLangRuntimeResolver.compareVersions( "1.6.1", "1.6.0" ) > 0 );
		assertTrue( BoxLangRuntimeResolver.compareVersions( "1.6.0", "1.6.1" ) < 0 );
	}

	public void testCompareVersions_differentLengths() {
		// "1.6" treated as "1.6.0"
		assertEquals( 0, BoxLangRuntimeResolver.compareVersions( "1.6", "1.6.0" ) );
		assertTrue( BoxLangRuntimeResolver.compareVersions( "1.7", "1.6.0" ) > 0 );
	}

	public void testCompareVersions_snapshotSuffix() {
		// Snapshot suffix segment is non-numeric; numeric parts still compared first
		assertTrue( BoxLangRuntimeResolver.compareVersions( "1.11.0", "1.6.0-snapshot" ) > 0 );
	}
}
