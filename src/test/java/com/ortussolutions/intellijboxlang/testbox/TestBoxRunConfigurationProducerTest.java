package com.ortussolutions.intellijboxlang.testbox;

import junit.framework.TestCase;

/**
 * Tests for TestBoxRunConfigurationProducer spec detection logic.
 * Validates that the correct spec name and filter value (including suite context)
 * are extracted from BDD and xUnit style test files.
 */
public class TestBoxRunConfigurationProducerTest extends TestCase {

	// ===== BDD: it() inside describe() =====

	public void testBddItInsideDescribe() {
		String										text	= """
		                                                      describe("My Feature", function() {
		                                                      	it("should work correctly", function() {
		                                                      		expect(true).toBe(true);
		                                                      	});
		                                                      });
		                                                      """;
		int											offset	= text.indexOf( "should work correctly" );
		TestBoxRunConfigurationProducer.SpecInfo	info	= TestBoxRunConfigurationProducer.findNearestSpec( text, offset );
		assertNotNull( info );
		assertEquals( "should work correctly", info.displayName );
		// TestBox computes spec.id = hash(suiteContext + specTitle)
		// suiteContext = "My Feature", specTitle = "should work correctly"
		assertEquals( "My Featureshould work correctly", info.filterValue );
	}

	public void testBddItInsideNestedDescribe() {
		String										text	= """
		                                                      describe("Outer", function() {
		                                                      	describe("Inner", function() {
		                                                      		it("should pass", function() {
		                                                      			expect(1).toBe(1);
		                                                      		});
		                                                      	});
		                                                      });
		                                                      """;
		int											offset	= text.indexOf( "should pass" );
		TestBoxRunConfigurationProducer.SpecInfo	info	= TestBoxRunConfigurationProducer.findNearestSpec( text, offset );
		assertNotNull( info );
		assertEquals( "should pass", info.displayName );
		// For nested describes, suiteContext is the IMMEDIATE parent ("Inner"), not "Outer"
		assertEquals( "Innershould pass", info.filterValue );
	}

	public void testBddTestCallInsideDescribe() {
		String										text	= """
		                                                      describe("Calculator", function() {
		                                                      	test("can add numbers", function() {
		                                                      		expect(1 + 1).toBe(2);
		                                                      	});
		                                                      });
		                                                      """;
		int											offset	= text.indexOf( "can add numbers" );
		TestBoxRunConfigurationProducer.SpecInfo	info	= TestBoxRunConfigurationProducer.findNearestSpec( text, offset );
		assertNotNull( info );
		assertEquals( "can add numbers", info.displayName );
		assertEquals( "Calculatorcan add numbers", info.filterValue );
	}

	// ===== BDD: Suite aliases (story, feature, given, when, scenario) =====

	public void testBddItInsideFeature() {
		String										text	= """
		                                                      feature("Login", function() {
		                                                      	it("should authenticate users", function() {
		                                                      		expect(true).toBe(true);
		                                                      	});
		                                                      });
		                                                      """;
		int											offset	= text.indexOf( "should authenticate" );
		TestBoxRunConfigurationProducer.SpecInfo	info	= TestBoxRunConfigurationProducer.findNearestSpec( text, offset );
		assertNotNull( info );
		assertEquals( "should authenticate users", info.displayName );
		assertEquals( "Loginshould authenticate users", info.filterValue );
	}

	public void testBddItInsideStory() {
		String										text	= """
		                                                      story("User Registration", function() {
		                                                      	it("creates a new account", function() {
		                                                      		expect(true).toBe(true);
		                                                      	});
		                                                      });
		                                                      """;
		int											offset	= text.indexOf( "creates a new account" );
		TestBoxRunConfigurationProducer.SpecInfo	info	= TestBoxRunConfigurationProducer.findNearestSpec( text, offset );
		assertNotNull( info );
		assertEquals( "creates a new account", info.displayName );
		assertEquals( "User Registrationcreates a new account", info.filterValue );
	}

	// ===== xUnit style =====

	public void testXunitFunction() {
		String										text	= """
		                                                      function testAddition() {
		                                                      	expect(1 + 1).toBe(2);
		                                                      }
		                                                      """;
		int											offset	= text.indexOf( "testAddition" );
		TestBoxRunConfigurationProducer.SpecInfo	info	= TestBoxRunConfigurationProducer.findNearestSpec( text, offset );
		assertNotNull( info );
		assertEquals( "testAddition", info.displayName );
		// xUnit: spec.id = hash(funcName), no suite context
		assertEquals( "testAddition", info.filterValue );
	}

	// ===== findEnclosingDescribeName =====

	public void testFindEnclosingDescribe_simple() {
		String	text		= """
		                      describe("MySuite", function() {
		                      	it("test1", function() {});
		                      });
		                      """;
		int		specOffset	= text.indexOf( "it(" );
		String	result		= TestBoxRunConfigurationProducer.findEnclosingDescribeName( text, specOffset );
		assertEquals( "MySuite", result );
	}

	public void testFindEnclosingDescribe_nested() {
		String	text		= """
		                      describe("Outer", function() {
		                      	describe("Inner", function() {
		                      		it("test1", function() {});
		                      	});
		                      });
		                      """;
		int		specOffset	= text.indexOf( "it(" );
		String	result		= TestBoxRunConfigurationProducer.findEnclosingDescribeName( text, specOffset );
		// Innermost describe wins
		assertEquals( "Inner", result );
	}

	public void testFindEnclosingDescribe_outsideNested() {
		String	text		= """
		                      describe("Outer", function() {
		                      	describe("Inner", function() {
		                      		it("inner test", function() {});
		                      	});
		                      	it("outer test", function() {});
		                      });
		                      """;
		// The "outer test" it() is after the Inner describe block closes
		int		specOffset	= text.indexOf( "outer test" );
		String	result		= TestBoxRunConfigurationProducer.findEnclosingDescribeName( text, specOffset );
		assertEquals( "Outer", result );
	}

	public void testFindEnclosingDescribe_noDescribe() {
		String	text		= """
		                      function testSomething() {
		                      	expect(1).toBe(1);
		                      }
		                      """;
		int		specOffset	= text.indexOf( "function" );
		String	result		= TestBoxRunConfigurationProducer.findEnclosingDescribeName( text, specOffset );
		assertNull( result );
	}

	public void testFindEnclosingDescribe_withBracesInStrings() {
		String	text		= """
		                      describe("My {Suite}", function() {
		                      	it("test with {braces}", function() {
		                      		var x = "{ not a block }";
		                      	});
		                      });
		                      """;
		int		specOffset	= text.indexOf( "it(" );
		String	result		= TestBoxRunConfigurationProducer.findEnclosingDescribeName( text, specOffset );
		assertEquals( "My {Suite}", result );
	}

	// ===== extractStringArgument =====

	public void testExtractStringArgument_doubleQuotes() {
		String text = "\"hello world\"";
		assertEquals( "hello world", TestBoxRunConfigurationProducer.extractStringArgument( text, 0 ) );
	}

	public void testExtractStringArgument_singleQuotes() {
		String text = "'hello world'";
		assertEquals( "hello world", TestBoxRunConfigurationProducer.extractStringArgument( text, 0 ) );
	}

	public void testExtractStringArgument_withLeadingWhitespace() {
		String text = "  \"hello\"";
		assertEquals( "hello", TestBoxRunConfigurationProducer.extractStringArgument( text, 0 ) );
	}

	public void testExtractStringArgument_escapedQuote() {
		String text = "\"hello \\\"world\\\"\"";
		assertEquals( "hello \"world\"", TestBoxRunConfigurationProducer.extractStringArgument( text, 0 ) );
	}

	public void testExtractStringArgument_noQuote() {
		String text = "someFunction()";
		assertNull( TestBoxRunConfigurationProducer.extractStringArgument( text, 0 ) );
	}

	// ===== Arrow function / lambda style =====

	public void testBddWithArrowFunction() {
		String										text	= """
		                                                      describe("Arrow Suite", () => {
		                                                      	it("uses arrow", () => {
		                                                      		expect(true).toBe(true);
		                                                      	});
		                                                      });
		                                                      """;
		int											offset	= text.indexOf( "uses arrow" );
		TestBoxRunConfigurationProducer.SpecInfo	info	= TestBoxRunConfigurationProducer.findNearestSpec( text, offset );
		assertNotNull( info );
		assertEquals( "uses arrow", info.displayName );
		assertEquals( "Arrow Suiteuses arrow", info.filterValue );
	}

	// ===== Named argument support =====

	public void testExtractStringArgument_namedTitle() {
		String text = "title = \"A spec\"";
		assertEquals( "A spec", TestBoxRunConfigurationProducer.extractStringArgument( text, 0 ) );
	}

	public void testExtractStringArgument_namedTitleNoSpaces() {
		String text = "title=\"A spec\"";
		assertEquals( "A spec", TestBoxRunConfigurationProducer.extractStringArgument( text, 0 ) );
	}

	public void testExtractStringArgument_namedTitleSingleQuotes() {
		String text = "title = 'A spec'";
		assertEquals( "A spec", TestBoxRunConfigurationProducer.extractStringArgument( text, 0 ) );
	}

	public void testBddDescribeWithNamedArguments() {
		// Real-world TestBox pattern: describe( title = "A spec", labels = "luis", body = function(){ ... } )
		String										text	= """
		                                                      describe(
		                                                      	title  = "A spec",
		                                                      	labels = "luis",
		                                                      	body   = function(){
		                                                      		it( "can match strings with no case sensitivity", function(){
		                                                      			expect( "Luis" ).toMatch( "^luis" );
		                                                      		} );
		                                                      	}
		                                                      );
		                                                      """;
		int											offset	= text.indexOf( "can match strings" );
		TestBoxRunConfigurationProducer.SpecInfo	info	= TestBoxRunConfigurationProducer.findNearestSpec( text, offset );
		assertNotNull( "Should find spec inside named-arg describe", info );
		assertEquals( "can match strings with no case sensitivity", info.displayName );
		assertEquals( "A speccan match strings with no case sensitivity", info.filterValue );
	}

	public void testBddItWithNamedArguments() {
		// it( title = "spec name", body = function(){ ... } )
		String										text	= """
		                                                      describe("My Suite", function(){
		                                                      	it(
		                                                      		title = "is just a closure so it can contain code",
		                                                      		body  = function(){
		                                                      			expect( 1 ).toBe( 1 );
		                                                      		},
		                                                      		labels = "luis"
		                                                      	);
		                                                      });
		                                                      """;
		int											offset	= text.indexOf( "is just a closure" );
		TestBoxRunConfigurationProducer.SpecInfo	info	= TestBoxRunConfigurationProducer.findNearestSpec( text, offset );
		assertNotNull( "Should find named-arg it() spec", info );
		assertEquals( "is just a closure so it can contain code", info.displayName );
		assertEquals( "My Suiteis just a closure so it can contain code", info.filterValue );
	}

	public void testFindEnclosingDescribe_namedArguments() {
		String	text		= """
		                      describe(
		                      	title  = "Named Suite",
		                      	body   = function(){
		                      		it("test1", function() {});
		                      	}
		                      );
		                      """;
		int		specOffset	= text.indexOf( "it(" );
		String	result		= TestBoxRunConfigurationProducer.findEnclosingDescribeName( text, specOffset );
		assertEquals( "Named Suite", result );
	}

	public void testFindEnclosingDescribe_nestedNamedArguments() {
		String	text		= """
		                      describe( title = "Outer Named", body = function(){
		                      	describe( title = "Inner Named", body = function(){
		                      		it("test1", function() {});
		                      	});
		                      });
		                      """;
		int		specOffset	= text.indexOf( "it(" );
		String	result		= TestBoxRunConfigurationProducer.findEnclosingDescribeName( text, specOffset );
		assertEquals( "Inner Named", result );
	}

	// ===== Regression: Real-world BoxLangTest.bx structure =====
	// This tests the complex nesting structure from the actual TestBox test suite

	/**
	 * Simplified version of BoxLangTest.bx structure to verify spec resolution
	 * in a complex file with named arguments, nested describes, inner closures
	 * (beforeEach/afterEach), and multiple sibling suites.
	 */
	private static final String BOXLANG_TEST_STRUCTURE = """
	                                                     class extends="testbox.system.BaseSpec" {
	                                                     	function run(){
	                                                     		describe(
	                                                     			title  = "A spec",
	                                                     			labels = "luis",
	                                                     			body   = function(){
	                                                     				beforeEach( function(){
	                                                     					coldbox = 0;
	                                                     					coldbox++;
	                                                     					debug( "beforeEach suite: coldbox = #coldbox#" );
	                                                     				} );
	                                                     				afterEach( function(){
	                                                     					foo = 0;
	                                                     				} );
	                                                     				describe( "A nice /suite/with/slashes", function(){
	                                                     					it( "can have slashes/inthe/it", function(){
	                                                     						expect( true ).toBeTrue();
	                                                     					} );
	                                                     				} );
	                                                     				it( "can match strings with no case sensitivity", function(){
	                                                     					expect( "Luis" ).toMatch( "^luis" );
	                                                     				} );
	                                                     				it(
	                                                     					title = "is just a closure so it can contain code",
	                                                     					body  = function(){
	                                                     						expect( coldbox ).toBe( 1 );
	                                                     					},
	                                                     					labels = "luis"
	                                                     				);
	                                                     				it( "can have more than one expectation test", function(){
	                                                     					coldbox = coldbox * 8;
	                                                     					expect( coldbox ).toBeTypeOf( "numeric" ).toBeNumeric();
	                                                     				} );
	                                                     			}
	                                                     		);
	                                                     		describe( "Custom Matchers", function(){
	                                                     			beforeEach( function(){
	                                                     				addMatchers( {
	                                                     					toBeReallyFalse : function( expectation, args = {} ){
	                                                     						return ( expectation.actual eq false );
	                                                     					}
	                                                     				} );
	                                                     				foo = false;
	                                                     			} );
	                                                     			it( "are cool and foo should be really false", function(){
	                                                     				expect( foo ).toBeReallyFalse();
	                                                     			} );
	                                                     			describe( "Nested suite: Testing loading via a CFC", function(){
	                                                     				it( "should be awesome", function(){
	                                                     					expect( foofoo ).toBeAwesome();
	                                                     				} );
	                                                     				describe( "Yet another nested suite", function(){
	                                                     					it( "should have cascaded beforeEach() call from parent", function(){
	                                                     						expect( foofoo ).toBeAwesome();
	                                                     					} );
	                                                     				} );
	                                                     			} );
	                                                     			describe( "Another Nested Suite", function(){
	                                                     				it( "can also be awesome", function(){
	                                                     					expect( foo ).toBeFalse();
	                                                     				} );
	                                                     			} );
	                                                     		} );
	                                                     		describe( "A calculator test suite", function(){
	                                                     			it( "Can have a separate beforeEach for this suite", function(){
	                                                     				expect( request.calc ).toBeComponent();
	                                                     			} );
	                                                     		} );
	                                                     	}
	                                                     }
	                                                     """;

	public void testRealWorld_specDirectlyInNamedArgDescribe() {
		// it("can match strings with no case sensitivity") is a direct child of describe("A spec")
		int											offset	= BOXLANG_TEST_STRUCTURE.indexOf( "can match strings with no case sensitivity" );
		TestBoxRunConfigurationProducer.SpecInfo	info	= TestBoxRunConfigurationProducer.findNearestSpec( BOXLANG_TEST_STRUCTURE, offset );
		assertNotNull( "Should find spec", info );
		assertEquals( "can match strings with no case sensitivity", info.displayName );
		assertEquals( "A speccan match strings with no case sensitivity", info.filterValue );
	}

	public void testRealWorld_specInNestedDescribeInsideNamedArgDescribe() {
		// it("can have slashes/inthe/it") is inside describe("A nice /suite/with/slashes")
		// which is nested inside describe("A spec")
		// Immediate parent is "A nice /suite/with/slashes"
		int											offset	= BOXLANG_TEST_STRUCTURE.indexOf( "can have slashes/inthe/it" );
		TestBoxRunConfigurationProducer.SpecInfo	info	= TestBoxRunConfigurationProducer.findNearestSpec( BOXLANG_TEST_STRUCTURE, offset );
		assertNotNull( "Should find spec", info );
		assertEquals( "can have slashes/inthe/it", info.displayName );
		assertEquals( "A nice /suite/with/slashescan have slashes/inthe/it", info.filterValue );
	}

	public void testRealWorld_specWithNamedArgsInsideNamedArgDescribe() {
		// it( title = "is just a closure...", body = function(){...} )
		// inside describe( title = "A spec", ... )
		int											offset	= BOXLANG_TEST_STRUCTURE.indexOf( "is just a closure so it can contain code" );
		TestBoxRunConfigurationProducer.SpecInfo	info	= TestBoxRunConfigurationProducer.findNearestSpec( BOXLANG_TEST_STRUCTURE, offset );
		assertNotNull( "Should find named-arg it() spec", info );
		assertEquals( "is just a closure so it can contain code", info.displayName );
		assertEquals( "A specis just a closure so it can contain code", info.filterValue );
	}

	public void testRealWorld_specInSiblingDescribe() {
		// it("are cool and foo should be really false") inside describe("Custom Matchers")
		int											offset	= BOXLANG_TEST_STRUCTURE.indexOf( "are cool and foo should be really false" );
		TestBoxRunConfigurationProducer.SpecInfo	info	= TestBoxRunConfigurationProducer.findNearestSpec( BOXLANG_TEST_STRUCTURE, offset );
		assertNotNull( "Should find spec", info );
		assertEquals( "are cool and foo should be really false", info.displayName );
		assertEquals( "Custom Matchersare cool and foo should be really false", info.filterValue );
	}

	public void testRealWorld_specInDoubleNestedDescribe() {
		// it("should be awesome") inside describe("Nested suite: Testing loading via a CFC")
		// which is inside describe("Custom Matchers")
		// Immediate parent is "Nested suite: Testing loading via a CFC"
		int											offset	= BOXLANG_TEST_STRUCTURE.indexOf( "should be awesome" );
		TestBoxRunConfigurationProducer.SpecInfo	info	= TestBoxRunConfigurationProducer.findNearestSpec( BOXLANG_TEST_STRUCTURE, offset );
		assertNotNull( "Should find spec", info );
		assertEquals( "should be awesome", info.displayName );
		assertEquals( "Nested suite: Testing loading via a CFCshould be awesome", info.filterValue );
	}

	public void testRealWorld_specInTripleNestedDescribe() {
		// it("should have cascaded beforeEach() call from parent")
		// inside describe("Yet another nested suite")
		// inside describe("Nested suite: Testing loading via a CFC")
		// inside describe("Custom Matchers")
		// Immediate parent is "Yet another nested suite"
		int											offset	= BOXLANG_TEST_STRUCTURE.indexOf( "should have cascaded beforeEach() call from parent" );
		TestBoxRunConfigurationProducer.SpecInfo	info	= TestBoxRunConfigurationProducer.findNearestSpec( BOXLANG_TEST_STRUCTURE, offset );
		assertNotNull( "Should find spec", info );
		assertEquals( "should have cascaded beforeEach() call from parent", info.displayName );
		assertEquals( "Yet another nested suiteshould have cascaded beforeEach() call from parent", info.filterValue );
	}

	public void testRealWorld_specInSiblingNestedDescribe() {
		// it("can also be awesome") inside describe("Another Nested Suite")
		// which is a sibling of describe("Nested suite: Testing loading via a CFC")
		// both inside describe("Custom Matchers")
		// Immediate parent is "Another Nested Suite"
		int											offset	= BOXLANG_TEST_STRUCTURE.indexOf( "can also be awesome" );
		TestBoxRunConfigurationProducer.SpecInfo	info	= TestBoxRunConfigurationProducer.findNearestSpec( BOXLANG_TEST_STRUCTURE, offset );
		assertNotNull( "Should find spec", info );
		assertEquals( "can also be awesome", info.displayName );
		assertEquals( "Another Nested Suitecan also be awesome", info.filterValue );
	}

	public void testRealWorld_specInThirdTopLevelDescribe() {
		// it("Can have a separate beforeEach for this suite")
		// inside describe("A calculator test suite") — a top-level describe
		int											offset	= BOXLANG_TEST_STRUCTURE.indexOf( "Can have a separate beforeEach for this suite" );
		TestBoxRunConfigurationProducer.SpecInfo	info	= TestBoxRunConfigurationProducer.findNearestSpec( BOXLANG_TEST_STRUCTURE, offset );
		assertNotNull( "Should find spec", info );
		assertEquals( "Can have a separate beforeEach for this suite", info.displayName );
		assertEquals( "A calculator test suiteCan have a separate beforeEach for this suite", info.filterValue );
	}

	public void testRealWorld_lastSpecBeforeClosingNamedArgDescribe() {
		// it("can have more than one expectation test") — last it() inside describe("A spec")
		// Before the closing of the named-arg describe
		int											offset	= BOXLANG_TEST_STRUCTURE.indexOf( "can have more than one expectation test" );
		TestBoxRunConfigurationProducer.SpecInfo	info	= TestBoxRunConfigurationProducer.findNearestSpec( BOXLANG_TEST_STRUCTURE, offset );
		assertNotNull( "Should find spec", info );
		assertEquals( "can have more than one expectation test", info.displayName );
		assertEquals( "A speccan have more than one expectation test", info.filterValue );
		assertFalse( "Should be a spec, not a suite", info.isSuite );
	}

	// ===== Suite-level detection: describe() gutter clicks =====

	public void testSuiteDetection_simpleDescribe() {
		String										text	= """
		                                                      describe("My Suite", function() {
		                                                      	it("test1", function() {
		                                                      		expect(true).toBe(true);
		                                                      	});
		                                                      });
		                                                      """;
		// Click on the describe line itself
		int											offset	= text.indexOf( "describe(\"My Suite\"" );
		TestBoxRunConfigurationProducer.SpecInfo	info	= TestBoxRunConfigurationProducer.findNearestSpec( text, offset );
		assertNotNull( "Should detect suite", info );
		assertEquals( "My Suite", info.displayName );
		assertEquals( "My Suite", info.filterValue );
		assertTrue( "Should be a suite", info.isSuite );
	}

	public void testSuiteDetection_cursorOnDescribeKeyword() {
		String										text	= """
		                                                      describe("A spec", function() {
		                                                      	it("should work", function() {
		                                                      		expect(true).toBe(true);
		                                                      	});
		                                                      });
		                                                      """;
		// Cursor right at the 'd' in 'describe'
		int											offset	= text.indexOf( "describe(" );
		TestBoxRunConfigurationProducer.SpecInfo	info	= TestBoxRunConfigurationProducer.findNearestSpec( text, offset );
		assertNotNull( "Should detect suite", info );
		assertEquals( "A spec", info.displayName );
		assertTrue( "Should be a suite", info.isSuite );
	}

	public void testSuiteDetection_cursorOnSuiteNameString() {
		String										text	= """
		                                                      describe("A spec", function() {
		                                                      	it("should work", function() {
		                                                      		expect(true).toBe(true);
		                                                      	});
		                                                      });
		                                                      """;
		// Cursor on the suite name string (still before the opening brace)
		int											offset	= text.indexOf( "A spec" );
		TestBoxRunConfigurationProducer.SpecInfo	info	= TestBoxRunConfigurationProducer.findNearestSpec( text, offset );
		assertNotNull( "Should detect suite", info );
		assertEquals( "A spec", info.displayName );
		assertTrue( "Should be a suite", info.isSuite );
	}

	public void testSuiteDetection_nestedDescribeClicksInner() {
		String										text	= """
		                                                      describe("Outer", function() {
		                                                      	describe("Inner", function() {
		                                                      		it("test1", function() {});
		                                                      	});
		                                                      });
		                                                      """;
		// Click on the inner describe
		int											offset	= text.indexOf( "describe(\"Inner\"" );
		TestBoxRunConfigurationProducer.SpecInfo	info	= TestBoxRunConfigurationProducer.findNearestSpec( text, offset );
		assertNotNull( "Should detect inner suite", info );
		assertEquals( "Inner", info.displayName );
		assertTrue( "Should be a suite", info.isSuite );
	}

	public void testSuiteDetection_featureAlias() {
		String										text	= """
		                                                      feature("Login", function() {
		                                                      	it("authenticates users", function() {});
		                                                      });
		                                                      """;
		int											offset	= text.indexOf( "feature(\"Login\"" );
		TestBoxRunConfigurationProducer.SpecInfo	info	= TestBoxRunConfigurationProducer.findNearestSpec( text, offset );
		assertNotNull( "Should detect feature suite", info );
		assertEquals( "Login", info.displayName );
		assertTrue( "Should be a suite", info.isSuite );
	}

	public void testSuiteDetection_storyAlias() {
		String										text	= """
		                                                      story("User Registration", function() {
		                                                      	it("creates account", function() {});
		                                                      });
		                                                      """;
		int											offset	= text.indexOf( "story(\"User Registration\"" );
		TestBoxRunConfigurationProducer.SpecInfo	info	= TestBoxRunConfigurationProducer.findNearestSpec( text, offset );
		assertNotNull( "Should detect story suite", info );
		assertEquals( "User Registration", info.displayName );
		assertTrue( "Should be a suite", info.isSuite );
	}

	public void testSuiteDetection_givenAlias() {
		String										text	= """
		                                                      given("a user exists", function() {
		                                                      	it("can log in", function() {});
		                                                      });
		                                                      """;
		int											offset	= text.indexOf( "given(\"a user exists\"" );
		TestBoxRunConfigurationProducer.SpecInfo	info	= TestBoxRunConfigurationProducer.findNearestSpec( text, offset );
		assertNotNull( "Should detect given suite", info );
		assertEquals( "a user exists", info.displayName );
		assertTrue( "Should be a suite", info.isSuite );
	}

	public void testSuiteDetection_whenAlias() {
		String										text	= """
		                                                      when("user clicks login", function() {
		                                                      	it("shows the dashboard", function() {});
		                                                      });
		                                                      """;
		int											offset	= text.indexOf( "when(\"user clicks login\"" );
		TestBoxRunConfigurationProducer.SpecInfo	info	= TestBoxRunConfigurationProducer.findNearestSpec( text, offset );
		assertNotNull( "Should detect when suite", info );
		assertEquals( "user clicks login", info.displayName );
		assertTrue( "Should be a suite", info.isSuite );
	}

	public void testSuiteDetection_scenarioAlias() {
		String										text	= """
		                                                      scenario("happy path", function() {
		                                                      	it("completes successfully", function() {});
		                                                      });
		                                                      """;
		int											offset	= text.indexOf( "scenario(\"happy path\"" );
		TestBoxRunConfigurationProducer.SpecInfo	info	= TestBoxRunConfigurationProducer.findNearestSpec( text, offset );
		assertNotNull( "Should detect scenario suite", info );
		assertEquals( "happy path", info.displayName );
		assertTrue( "Should be a suite", info.isSuite );
	}

	public void testSuiteDetection_namedArguments() {
		String										text	= """
		                                                      describe(
		                                                      	title  = "A spec",
		                                                      	labels = "luis",
		                                                      	body   = function(){
		                                                      		it("test1", function() {});
		                                                      	}
		                                                      );
		                                                      """;
		int											offset	= text.indexOf( "describe(" );
		TestBoxRunConfigurationProducer.SpecInfo	info	= TestBoxRunConfigurationProducer.findNearestSpec( text, offset );
		assertNotNull( "Should detect suite with named args", info );
		assertEquals( "A spec", info.displayName );
		assertTrue( "Should be a suite", info.isSuite );
	}

	public void testSuiteDetection_insideBodyFindsSpecNotSuite() {
		// When cursor is INSIDE the body of a describe (at a blank line between specs),
		// and there's a preceding it(), we should find the spec, not the suite
		String	text	= """
		                  describe("My Suite", function() {
		                  	it("first test", function() {
		                  		expect(true).toBe(true);
		                  	});

		                  	it("second test", function() {
		                  		expect(true).toBe(true);
		                  	});
		                  });
		                  """;
		// Position cursor inside the body, on the blank line between specs
		// This is after "first test" it() and before "second test" it()
		int		offset	= text.indexOf( "\n\t\t\t\n\t\t\tit(\"second test\"" );
		if ( offset < 0 ) {
			offset = text.indexOf( "second test" ) - 10;
		}
		TestBoxRunConfigurationProducer.SpecInfo info = TestBoxRunConfigurationProducer.findNearestSpec( text, offset );
		assertNotNull( info );
		// Should find "first test" (the preceding it()) not "My Suite" (the enclosing describe)
		assertEquals( "first test", info.displayName );
		assertFalse( "Should be a spec, not a suite", info.isSuite );
	}

	public void testSpecDetection_allSpecsHaveIsSuiteFalse() {
		// Verify that normal spec detection still sets isSuite to false
		String										text	= """
		                                                      describe("MySuite", function() {
		                                                      	it("works", function() {});
		                                                      });
		                                                      """;
		int											offset	= text.indexOf( "works" );
		TestBoxRunConfigurationProducer.SpecInfo	info	= TestBoxRunConfigurationProducer.findNearestSpec( text, offset );
		assertNotNull( info );
		assertFalse( "Spec should not be marked as suite", info.isSuite );
	}

	public void testXunitSpecDetection_hasIsSuiteFalse() {
		String										text	= """
		                                                      function testSomething() {
		                                                      	expect(1).toBe(1);
		                                                      }
		                                                      """;
		int											offset	= text.indexOf( "testSomething" );
		TestBoxRunConfigurationProducer.SpecInfo	info	= TestBoxRunConfigurationProducer.findNearestSpec( text, offset );
		assertNotNull( info );
		assertFalse( "xUnit spec should not be marked as suite", info.isSuite );
	}

	// ===== Real-world suite detection from BoxLangTest.bx structure =====

	public void testRealWorld_suiteDetection_topLevelNamedArgDescribe() {
		// Click on the first describe( title = "A spec", ... )
		int											offset	= BOXLANG_TEST_STRUCTURE.indexOf( "describe(" );
		TestBoxRunConfigurationProducer.SpecInfo	info	= TestBoxRunConfigurationProducer.findNearestSpec( BOXLANG_TEST_STRUCTURE, offset );
		assertNotNull( "Should detect top-level suite", info );
		assertEquals( "A spec", info.displayName );
		assertEquals( "A spec", info.filterValue );
		assertTrue( "Should be a suite", info.isSuite );
	}

	public void testRealWorld_suiteDetection_customMatchersDescribe() {
		// Click on describe("Custom Matchers", ...)
		int											offset	= BOXLANG_TEST_STRUCTURE.indexOf( "describe( \"Custom Matchers\"" );
		TestBoxRunConfigurationProducer.SpecInfo	info	= TestBoxRunConfigurationProducer.findNearestSpec( BOXLANG_TEST_STRUCTURE, offset );
		assertNotNull( "Should detect Custom Matchers suite", info );
		assertEquals( "Custom Matchers", info.displayName );
		assertEquals( "Custom Matchers", info.filterValue );
		assertTrue( "Should be a suite", info.isSuite );
	}

	public void testRealWorld_suiteDetection_calculatorDescribe() {
		// Click on describe("A calculator test suite", ...)
		int											offset	= BOXLANG_TEST_STRUCTURE.indexOf( "describe( \"A calculator test suite\"" );
		TestBoxRunConfigurationProducer.SpecInfo	info	= TestBoxRunConfigurationProducer.findNearestSpec( BOXLANG_TEST_STRUCTURE, offset );
		assertNotNull( "Should detect calculator test suite", info );
		assertEquals( "A calculator test suite", info.displayName );
		assertEquals( "A calculator test suite", info.filterValue );
		assertTrue( "Should be a suite", info.isSuite );
	}

	public void testRealWorld_suiteDetection_nestedSuiteInsideCustomMatchers() {
		// Click on describe("Nested suite: Testing loading via a CFC", ...)
		int											offset	= BOXLANG_TEST_STRUCTURE.indexOf( "describe( \"Nested suite: Testing loading via a CFC\"" );
		TestBoxRunConfigurationProducer.SpecInfo	info	= TestBoxRunConfigurationProducer.findNearestSpec( BOXLANG_TEST_STRUCTURE, offset );
		assertNotNull( "Should detect nested suite", info );
		assertEquals( "Nested suite: Testing loading via a CFC", info.displayName );
		assertEquals( "Nested suite: Testing loading via a CFC", info.filterValue );
		assertTrue( "Should be a suite", info.isSuite );
	}
}
