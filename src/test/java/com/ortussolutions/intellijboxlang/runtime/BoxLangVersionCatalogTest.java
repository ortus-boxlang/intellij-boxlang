package com.ortussolutions.intellijboxlang.runtime;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public class BoxLangVersionCatalogTest extends BasePlatformTestCase {

	public void testS3ListingKeepsValidRuntimeEntries() {
		String	listing	= "<ListBucketResult>"
		    + block( "ortussolutions/boxlang/boxlang-1.12.0.jar" )
		    + block( "ortussolutions/boxlang/boxlang-1.12.0-snapshot.jar" )
		    + block( "ortussolutions/boxlang/escape.jar" )
		    + "</ListBucketResult>";
		var		entries	= BoxLangVersionCatalog.parseEntries( listing );
		assertEquals( 1, entries.size() );
		assertEquals( "boxlang-1.12.0", entries.getFirst().name() );
		assertEquals( "https://downloads.ortussolutions.com/ortussolutions/boxlang/boxlang-1.12.0.jar",
		    entries.getFirst().downloadUrl() );
	}

	public void testUnclosedContentsCannotStallListingParser() {
		String listing = "<Contents>".repeat( 10_000 );
		assertTrue( BoxLangVersionCatalog.parseEntries( listing ).isEmpty() );
	}

	private String block( String key ) {
		return "<Contents><Key>" + key + "</Key><LastModified>2026-09-15T00:00:00Z</LastModified></Contents>";
	}
}
