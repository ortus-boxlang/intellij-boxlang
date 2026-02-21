package com.ortussolutions.intellijboxlang.run;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.Map;

public class BoxLangRunProfileStateTest extends BasePlatformTestCase {

	public void testParseEnvironmentVariablesParsesKeyValuePairs() {
		Map<String, String> variables = BoxLangRunProfileState.parseEnvironmentVariables(
		    "FOO=bar BAZ=\"hello world\" EMPTY="
		);

		assertEquals( 3, variables.size() );
		assertEquals( "bar", variables.get( "FOO" ) );
		assertEquals( "hello world", variables.get( "BAZ" ) );
		assertEquals( "", variables.get( "EMPTY" ) );
	}

	public void testParseEnvironmentVariablesIgnoresInvalidTokens() {
		Map<String, String> variables = BoxLangRunProfileState.parseEnvironmentVariables(
		    "VALID=value INVALID =missing MISSING_EQUALS"
		);

		assertEquals( 1, variables.size() );
		assertEquals( "value", variables.get( "VALID" ) );
	}

	public void testParseEnvironmentVariablesUsesLatestDuplicateValue() {
		Map<String, String> variables = BoxLangRunProfileState.parseEnvironmentVariables(
		    "FOO=first FOO=second"
		);

		assertEquals( 1, variables.size() );
		assertEquals( "second", variables.get( "FOO" ) );
	}
}
