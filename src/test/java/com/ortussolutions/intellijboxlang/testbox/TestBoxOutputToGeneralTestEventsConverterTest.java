package com.ortussolutions.intellijboxlang.testbox;

import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.ProcessOutputType;
import com.intellij.execution.testframework.sm.runner.GeneralTestEventsProcessor;
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.execution.testframework.sm.runner.events.*;
import com.intellij.execution.testframework.sm.runner.TestProxyPrinterProvider;
import com.intellij.openapi.util.Key;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests for TestBoxOutputToGeneralTestEventsConverter.
 * Validates that TestBox JSON reporter output is correctly parsed
 * into SMTRunner test events via the GeneralTestEventsProcessor.
 */
public class TestBoxOutputToGeneralTestEventsConverterTest extends BasePlatformTestCase {

	/**
	 * Simple recording of events emitted by the converter.
	 * We record events as descriptive strings for easy assertion.
	 */
	private List<String>								events;
	private TestBoxOutputToGeneralTestEventsConverter	converter;

	/**
	 * A minimal concrete subclass of GeneralTestEventsProcessor that records events.
	 */
	private static class RecordingProcessor extends GeneralTestEventsProcessor {

		final List<String> events;

		RecordingProcessor( @NotNull com.intellij.openapi.project.Project project, List<String> events ) {
			super( project, "TestBox", new SMTestProxy.SMRootTestProxy() );
			this.events = events;
		}

		@Override
		public void onStartTesting() {
			events.add( "onStartTesting" );
		}

		@Override
		public void onTestsCountInSuite( int count ) {
			events.add( "onTestsCountInSuite:" + count );
		}

		@Override
		public void onTestStarted( @NotNull TestStartedEvent event ) {
			events.add( "testStarted:" + event.getName() );
		}

		@Override
		public void onTestFinished( @NotNull TestFinishedEvent event ) {
			events.add( "testFinished:" + event.getName() + ":duration=" + event.getDuration() );
		}

		@Override
		public void onTestFailure( @NotNull TestFailedEvent event ) {
			events.add( "testFailed:" + event.getName()
			    + ":message=" + event.getLocalizedFailureMessage()
			    + ":error=" + event.isTestError() );
		}

		@Override
		public void onTestIgnored( @NotNull TestIgnoredEvent event ) {
			events.add( "testIgnored:" + event.getName() + ":comment=" + event.getIgnoreComment() );
		}

		@Override
		public void onTestOutput( @NotNull TestOutputEvent event ) {
			events.add( "testOutput:" + event.getName() );
		}

		@Override
		public void onSuiteStarted( @NotNull TestSuiteStartedEvent event ) {
			events.add( "suiteStarted:" + event.getName() );
		}

		@Override
		public void onSuiteFinished( @NotNull TestSuiteFinishedEvent event ) {
			events.add( "suiteFinished:" + event.getName() );
		}

		@Override
		public void onUncapturedOutput( @NotNull String text, Key outputType ) {
			events.add( "uncaptured:" + text.trim() );
		}

		@Override
		public void onError( @NotNull String localizedMessage, @Nullable String stackTrace, boolean isCritical ) {
			events.add( "error:" + localizedMessage );
		}

		@Override
		public void onTestsReporterAttached() {
		}

		@Override
		public void setPrinterProvider( @NotNull TestProxyPrinterProvider provider ) {
		}

		@Override
		public void onFinishTesting() {
			events.add( "onFinishTesting" );
		}

		@Override
		public void dispose() {
		}
	}

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		events = new ArrayList<>();

		// Create a minimal RunProfile for the console properties
		com.intellij.execution.configurations.RunProfile	dummyProfile	= new com.intellij.execution.configurations.RunProfile() {

																				@Override
																				public com.intellij.execution.configurations.RunProfileState getState(
																				    @NotNull com.intellij.execution.Executor executor,
																				    @NotNull com.intellij.execution.runners.ExecutionEnvironment environment ) {
																					return null;
																				}

																				@Override
																				public @NotNull String getName() {
																					return "TestBoxTest";
																				}

																				@Override
																				public @Nullable javax.swing.Icon getIcon() {
																					return null;
																				}
																			};

		SMTRunnerConsoleProperties							properties		= new SMTRunnerConsoleProperties(
		    getProject(),
		    dummyProfile,
		    "TestBox",
		    DefaultRunExecutor.getRunExecutorInstance()
		);

		converter = new TestBoxOutputToGeneralTestEventsConverter( "TestBox", properties );

		// Inject the recording processor so we can verify events
		converter.setProcessor( new RecordingProcessor( getProject(), events ) );
	}

	/**
	 * Helper to check if any recorded event contains the given substring.
	 */
	private boolean hasEvent( String substring ) {
		return events.stream().anyMatch( e -> e.contains( substring ) );
	}

	/**
	 * Helper to find all events matching a substring.
	 */
	private List<String> findEvents( String substring ) {
		return events.stream().filter( e -> e.contains( substring ) ).toList();
	}

	// -------------------------------------------------------------------------
	// Basic JSON parsing tests
	// -------------------------------------------------------------------------

	public void testPassingSpec() {
		String json = """
		              {
		                "bundleStats": [{
		                  "name": "MyBundle",
		                  "path": "tests.specs.MyBundleSpec",
		                  "totalDuration": 100,
		                  "globalException": "",
		                  "suiteStats": [{
		                    "name": "My Suite",
		                    "totalDuration": 50,
		                    "specStats": [{
		                      "name": "should pass",
		                      "status": "Passed",
		                      "totalDuration": 10,
		                      "error": {},
		                      "failMessage": "",
		                      "failOrigin": {}
		                    }],
		                    "suiteStats": []
		                  }]
		                }]
		              }
		              """;
		converter.tryParseResults( json );

		// NOTE: onStartTesting/onFinishTesting are NOT emitted by the converter.
		// In production, the SMTRunner framework fires these via ProcessListener callbacks.
		// In unit tests we verify only the test tree events.

		assertTrue( "Should emit suiteStarted for bundle", hasEvent( "suiteStarted:MyBundle" ) );
		assertTrue( "Should emit suiteStarted for suite", hasEvent( "suiteStarted:My Suite" ) );
		assertTrue( "Should emit testStarted for spec", hasEvent( "testStarted:should pass" ) );
		assertTrue( "Should emit testFinished for spec", hasEvent( "testFinished:should pass" ) );
		assertTrue( "Should emit suiteFinished for suite", hasEvent( "suiteFinished:My Suite" ) );
		assertTrue( "Should emit suiteFinished for bundle", hasEvent( "suiteFinished:MyBundle" ) );
		// Should NOT have testFailed
		assertFalse( "Should not emit testFailed", hasEvent( "testFailed" ) );
	}

	public void testFailedSpec() {
		String json = """
		              {
		                "bundleStats": [{
		                  "name": "MyBundle",
		                  "path": "tests.specs.MyBundleSpec",
		                  "totalDuration": 100,
		                  "globalException": "",
		                  "suiteStats": [{
		                    "name": "My Suite",
		                    "totalDuration": 50,
		                    "specStats": [{
		                      "name": "should fail",
		                      "status": "Failed",
		                      "totalDuration": 10,
		                      "error": {},
		                      "failMessage": "Expected true but got false",
		                      "failDetail": "at UserServiceTest.bx:42",
		                      "failOrigin": {},
		                      "failStacktrace": ""
		                    }],
		                    "suiteStats": []
		                  }]
		                }]
		              }
		              """;
		converter.tryParseResults( json );

		assertTrue( "Should emit testStarted", hasEvent( "testStarted:should fail" ) );
		assertTrue( "Should emit testFailed", hasEvent( "testFailed:should fail" ) );
		assertTrue( "Should include failure message", hasEvent( "Expected true but got false" ) );
		assertTrue( "Failure should not be error", hasEvent( "error=false" ) );
		assertTrue( "Should emit testFinished", hasEvent( "testFinished:should fail" ) );
	}

	public void testErrorSpec() {
		String json = """
		              {
		                "bundleStats": [{
		                  "name": "MyBundle",
		                  "path": "tests.specs.MyBundleSpec",
		                  "totalDuration": 100,
		                  "globalException": "",
		                  "suiteStats": [{
		                    "name": "My Suite",
		                    "totalDuration": 50,
		                    "specStats": [{
		                      "name": "should error",
		                      "status": "Error",
		                      "totalDuration": 10,
		                      "error": {
		                        "type": "NullPointerException",
		                        "message": "Cannot invoke method on null",
		                        "detail": "",
		                        "stackTrace": "at java.lang.NullPointerException"
		                      },
		                      "failMessage": "Cannot invoke method on null",
		                      "failOrigin": {},
		                      "failDetail": "",
		                      "failStacktrace": "at java.lang.NullPointerException"
		                    }],
		                    "suiteStats": []
		                  }]
		                }]
		              }
		              """;
		converter.tryParseResults( json );

		assertTrue( "Should emit testStarted", hasEvent( "testStarted:should error" ) );
		assertTrue( "Should emit testFailed with error=true", hasEvent( "error=true" ) );
		// Should extract type + message from the error struct
		assertTrue( "Should include error type and message", hasEvent( "NullPointerException" ) );
		assertTrue( "Should emit testFinished", hasEvent( "testFinished:should error" ) );
	}

	public void testSkippedSpec() {
		String json = """
		              {
		                "bundleStats": [{
		                  "name": "MyBundle",
		                  "path": "tests.specs.MyBundleSpec",
		                  "totalDuration": 100,
		                  "globalException": "",
		                  "suiteStats": [{
		                    "name": "My Suite",
		                    "totalDuration": 50,
		                    "specStats": [{
		                      "name": "should skip",
		                      "status": "Skipped",
		                      "totalDuration": 0,
		                      "error": {},
		                      "failMessage": "",
		                      "failOrigin": {}
		                    }],
		                    "suiteStats": []
		                  }]
		                }]
		              }
		              """;
		converter.tryParseResults( json );

		assertTrue( "Should emit testIgnored", hasEvent( "testIgnored:should skip" ) );
		// Skipped specs must be bracketed with testStarted/testFinished to avoid
		// orphan entries in the running tests map (which would cause "Terminated" root)
		assertTrue( "Should emit testStarted for skipped spec (required by framework)",
		    hasEvent( "testStarted:should skip" ) );
		assertTrue( "Should emit testFinished for skipped spec (required by framework)",
		    hasEvent( "testFinished:should skip" ) );
	}

	public void testNestedSuites() {
		String json = """
		              {
		                "bundleStats": [{
		                  "name": "MyBundle",
		                  "path": "tests.specs.MyBundleSpec",
		                  "totalDuration": 200,
		                  "globalException": "",
		                  "suiteStats": [{
		                    "name": "Outer Suite",
		                    "totalDuration": 150,
		                    "specStats": [{
		                      "name": "outer spec",
		                      "status": "Passed",
		                      "totalDuration": 5,
		                      "error": {},
		                      "failMessage": "",
		                      "failOrigin": {}
		                    }],
		                    "suiteStats": [{
		                      "name": "Inner Suite",
		                      "totalDuration": 100,
		                      "specStats": [{
		                        "name": "inner spec",
		                        "status": "Passed",
		                        "totalDuration": 3,
		                        "error": {},
		                        "failMessage": "",
		                        "failOrigin": {}
		                      }],
		                      "suiteStats": []
		                    }]
		                  }]
		                }]
		              }
		              """;
		converter.tryParseResults( json );

		assertTrue( "Should emit outer suite started", hasEvent( "suiteStarted:Outer Suite" ) );
		assertTrue( "Should emit inner suite started", hasEvent( "suiteStarted:Inner Suite" ) );
		assertTrue( "Should emit outer spec", hasEvent( "testStarted:outer spec" ) );
		assertTrue( "Should emit inner spec", hasEvent( "testStarted:inner spec" ) );
		assertTrue( "Should emit inner suite finished", hasEvent( "suiteFinished:Inner Suite" ) );
		assertTrue( "Should emit outer suite finished", hasEvent( "suiteFinished:Outer Suite" ) );
	}

	public void testGlobalException() {
		String json = """
		              {
		                "bundleStats": [{
		                  "name": "BrokenBundle",
		                  "path": "tests.specs.BrokenBundleSpec",
		                  "totalDuration": 5,
		                  "globalException": {"type": "RuntimeException", "message": "beforeAll failed"},
		                  "suiteStats": []
		                }]
		              }
		              """;
		converter.tryParseResults( json );

		assertTrue( "Should emit bundle suite started", hasEvent( "suiteStarted:BrokenBundle" ) );
		assertTrue( "Should emit Bundle Error test", hasEvent( "testStarted:Bundle Error" ) );
		assertTrue( "Should emit Bundle Error failed", hasEvent( "testFailed:Bundle Error" ) );
		assertTrue( "Should emit bundle suite finished", hasEvent( "suiteFinished:BrokenBundle" ) );
	}

	// -------------------------------------------------------------------------
	// JSON extraction from noisy output
	// -------------------------------------------------------------------------

	// -------------------------------------------------------------------------
	// process() forwarding and tryParseResults() tests
	// -------------------------------------------------------------------------

	public void testProcessForwardsAllOutputToUncaptured() {
		// process() should forward all output as uncaptured (for console panel display)
		// and should NOT parse JSON or emit test tree events
		converter.process( "TestBox v6.3.0\n", ProcessOutputType.STDOUT );
		converter.process( "Running tests...\n", ProcessOutputType.STDOUT );

		assertTrue( "Should forward first line as uncaptured", hasEvent( "uncaptured:TestBox v6.3.0" ) );
		assertTrue( "Should forward second line as uncaptured", hasEvent( "uncaptured:Running tests..." ) );
		assertFalse( "Should NOT emit any test events", hasEvent( "testStarted" ) );
		assertFalse( "Should NOT emit any suite events", hasEvent( "suiteStarted" ) );
	}

	public void testProcessForwardsJsonAsUncapturedWithoutParsing() {
		// Even if JSON is sent through process(), it should NOT be parsed for test events
		String json = """
		              {"bundleStats":[{"name":"MyBundle","path":"tests.specs.MyBundleSpec","totalDuration":100,"globalException":"","suiteStats":[{"name":"Suite","totalDuration":50,"specStats":[{"name":"test1","status":"Passed","totalDuration":10,"error":{},"failMessage":"","failOrigin":{}}],"suiteStats":[]}]}]}""";
		converter.process( json, ProcessOutputType.STDOUT );

		// Should be forwarded as uncaptured output
		assertTrue( "Should forward JSON as uncaptured", hasEvent( "uncaptured:" ) );
		// Should NOT parse it into test events
		assertFalse( "Should NOT emit testStarted from process()", hasEvent( "testStarted" ) );
	}

	public void testTryParseResultsReturnsFalseForNonTestBoxJson() {
		// A small JSON object that doesn't have bundleStats should return false
		boolean result = converter.tryParseResults( "{\"status\": \"ok\", \"version\": \"1.0\"}" );

		assertFalse( "Should return false for non-TestBox JSON", result );
		assertFalse( "Should not emit testStarted for non-TestBox JSON", hasEvent( "testStarted" ) );
		assertFalse( "Should not emit suiteStarted for non-TestBox JSON", hasEvent( "suiteStarted" ) );
	}

	public void testTryParseResultsReturnsTrueForValidTestBoxJson() {
		String	json	= """
		                  {"bundleStats":[{"name":"B","path":"","totalDuration":1,"globalException":"","suiteStats":[{"name":"S","totalDuration":1,"specStats":[{"name":"t","status":"Passed","totalDuration":1,"error":{},"failMessage":"","failOrigin":{}}],"suiteStats":[]}]}]}""";

		boolean	result	= converter.tryParseResults( json );

		assertTrue( "Should return true for valid TestBox JSON", result );
		assertTrue( "Should emit testStarted", hasEvent( "testStarted:t" ) );
	}

	public void testTryParseResultsReturnsFalseForInvalidJson() {
		boolean result = converter.tryParseResults( "not json at all" );

		assertFalse( "Should return false for invalid JSON", result );
		assertFalse( "Should not emit any events", hasEvent( "testStarted" ) );
	}

	public void testDurationIncluded() {
		String json = """
		              {
		                "bundleStats": [{
		                  "name": "MyBundle",
		                  "path": "",
		                  "totalDuration": 500,
		                  "globalException": "",
		                  "suiteStats": [{
		                    "name": "Suite",
		                    "totalDuration": 200,
		                    "specStats": [{
		                      "name": "test1",
		                      "status": "Passed",
		                      "totalDuration": 42,
		                      "error": {},
		                      "failMessage": "",
		                      "failOrigin": {}
		                    }],
		                    "suiteStats": []
		                  }]
		                }]
		              }
		              """;
		converter.tryParseResults( json );

		assertTrue( "Should include duration on spec", hasEvent( "testFinished:test1:duration=42" ) );
	}

	public void testMultipleBundles() {
		String json = """
		              {
		                "bundleStats": [
		                  {
		                    "name": "Bundle1",
		                    "path": "tests.specs.Bundle1Spec",
		                    "totalDuration": 100,
		                    "globalException": "",
		                    "suiteStats": [{
		                      "name": "Suite1",
		                      "totalDuration": 50,
		                      "specStats": [{
		                        "name": "test from bundle 1",
		                        "status": "Passed",
		                        "totalDuration": 10,
		                        "error": {},
		                        "failMessage": "",
		                        "failOrigin": {}
		                      }],
		                      "suiteStats": []
		                    }]
		                  },
		                  {
		                    "name": "Bundle2",
		                    "path": "tests.specs.Bundle2Spec",
		                    "totalDuration": 200,
		                    "globalException": "",
		                    "suiteStats": [{
		                      "name": "Suite2",
		                      "totalDuration": 100,
		                      "specStats": [{
		                        "name": "test from bundle 2",
		                        "status": "Failed",
		                        "totalDuration": 20,
		                        "error": {},
		                        "failMessage": "Assertion failed",
		                        "failDetail": "",
		                        "failOrigin": {},
		                        "failStacktrace": ""
		                      }],
		                      "suiteStats": []
		                    }]
		                  }
		                ]
		              }
		              """;
		converter.tryParseResults( json );

		assertTrue( "Should emit bundle 1", hasEvent( "suiteStarted:Bundle1" ) );
		assertTrue( "Should emit bundle 2", hasEvent( "suiteStarted:Bundle2" ) );
		assertTrue( "Should emit spec from bundle 1", hasEvent( "testStarted:test from bundle 1" ) );
		assertTrue( "Should emit spec from bundle 2", hasEvent( "testStarted:test from bundle 2" ) );
		assertTrue( "Should emit failure from bundle 2", hasEvent( "testFailed:test from bundle 2" ) );
	}

	public void testBundleCollapsedWhenSingleSuiteWithSameName() {
		// xUnit-style bundles often have the same name for the bundle and the single top-level suite.
		// We should collapse the redundant nesting: instead of Bundle > Suite > spec,
		// we emit just Suite > spec.
		String json = """
		              {
		                "bundleStats": [{
		                  "name": "My Test Suite",
		                  "path": "tests.specs.MyTestSuite",
		                  "totalDuration": 100,
		                  "globalException": "",
		                  "suiteStats": [{
		                    "name": "My Test Suite",
		                    "totalDuration": 50,
		                    "specStats": [{
		                      "name": "testSomething",
		                      "status": "Passed",
		                      "totalDuration": 10,
		                      "error": {},
		                      "failMessage": "",
		                      "failOrigin": {}
		                    }],
		                    "suiteStats": []
		                  }]
		                }]
		              }
		              """;
		converter.tryParseResults( json );

		// Should only have ONE suiteStarted for "My Test Suite", not two
		long suiteStartedCount = events.stream().filter( e -> e.equals( "suiteStarted:My Test Suite" ) ).count();
		assertEquals( "Should collapse duplicate suite nesting", 1, suiteStartedCount );

		long suiteFinishedCount = events.stream().filter( e -> e.equals( "suiteFinished:My Test Suite" ) ).count();
		assertEquals( "Should collapse duplicate suite finished", 1, suiteFinishedCount );

		assertTrue( "Should emit testStarted", hasEvent( "testStarted:testSomething" ) );
	}

	public void testBundleNotCollapsedWhenNamesDiffer() {
		// When bundle name differs from the single suite name, both should appear
		String json = """
		              {
		                "bundleStats": [{
		                  "name": "MyBundle",
		                  "path": "tests.specs.MyBundleSpec",
		                  "totalDuration": 100,
		                  "globalException": "",
		                  "suiteStats": [{
		                    "name": "Different Suite Name",
		                    "totalDuration": 50,
		                    "specStats": [{
		                      "name": "testSomething",
		                      "status": "Passed",
		                      "totalDuration": 10,
		                      "error": {},
		                      "failMessage": "",
		                      "failOrigin": {}
		                    }],
		                    "suiteStats": []
		                  }]
		                }]
		              }
		              """;
		converter.tryParseResults( json );

		// Both the bundle and suite should appear
		assertTrue( "Should emit bundle suite", hasEvent( "suiteStarted:MyBundle" ) );
		assertTrue( "Should emit inner suite", hasEvent( "suiteStarted:Different Suite Name" ) );
	}

	public void testBundleNotCollapsedWhenMultipleSuites() {
		// When the bundle has multiple top-level suites, the bundle wrapper should remain
		String json = """
		              {
		                "bundleStats": [{
		                  "name": "MyBundle",
		                  "path": "tests.specs.MyBundleSpec",
		                  "totalDuration": 200,
		                  "globalException": "",
		                  "suiteStats": [
		                    {
		                      "name": "MyBundle",
		                      "totalDuration": 100,
		                      "specStats": [{
		                        "name": "test1",
		                        "status": "Passed",
		                        "totalDuration": 5,
		                        "error": {},
		                        "failMessage": "",
		                        "failOrigin": {}
		                      }],
		                      "suiteStats": []
		                    },
		                    {
		                      "name": "Second Suite",
		                      "totalDuration": 100,
		                      "specStats": [{
		                        "name": "test2",
		                        "status": "Passed",
		                        "totalDuration": 5,
		                        "error": {},
		                        "failMessage": "",
		                        "failOrigin": {}
		                      }],
		                      "suiteStats": []
		                    }
		                  ]
		                }]
		              }
		              """;
		converter.tryParseResults( json );

		// Bundle wrapper should still appear because there are multiple suites
		assertTrue( "Should emit bundle suite", hasEvent( "suiteStarted:MyBundle" ) );
		// And both suites
		long bundleCount = events.stream().filter( e -> e.equals( "suiteStarted:MyBundle" ) ).count();
		assertEquals( "Bundle should appear twice (once as wrapper, once as suite)", 2, bundleCount );
		assertTrue( "Should emit second suite", hasEvent( "suiteStarted:Second Suite" ) );
	}

	public void testErrorSpecWithErrorStructOnly() {
		// xUnit-style error where failMessage is not set, only error struct
		String json = """
		              {
		                "bundleStats": [{
		                  "name": "MyBundle",
		                  "path": "tests.specs.MyBundleSpec",
		                  "totalDuration": 100,
		                  "globalException": "",
		                  "suiteStats": [{
		                    "name": "My Suite",
		                    "totalDuration": 50,
		                    "specStats": [{
		                      "name": "error only",
		                      "status": "Error",
		                      "totalDuration": 10,
		                      "error": {
		                        "type": "CustomError",
		                        "message": "Something went wrong"
		                      },
		                      "failMessage": "",
		                      "failOrigin": {},
		                      "failDetail": "",
		                      "failStacktrace": ""
		                    }],
		                    "suiteStats": []
		                  }]
		                }]
		              }
		              """;
		converter.tryParseResults( json );

		assertTrue( "Should extract error type and message",
		    hasEvent( "CustomError: Something went wrong" ) );
	}

	public void testTryParseResultsCanBeCalledMultipleTimes() {
		String json = """
		              {"bundleStats":[{"name":"B","path":"","totalDuration":1,"globalException":"","suiteStats":[{"name":"S","totalDuration":1,"specStats":[{"name":"t","status":"Passed","totalDuration":1,"error":{},"failMessage":"","failOrigin":{}}],"suiteStats":[]}]}]}""";
		converter.tryParseResults( json );
		converter.tryParseResults( json );

		// Both calls should emit events (the converter itself doesn't deduplicate;
		// in production, flushBufferOnProcessTermination is only called once)
		long testStartedCount = events.stream().filter( e -> e.startsWith( "testStarted:" ) ).count();
		assertEquals( "Should have two testStarted events from two calls", 2, testStartedCount );
	}

	// -------------------------------------------------------------------------
	// hideSkippedSpecs tests (single-spec run mode)
	// -------------------------------------------------------------------------

	/**
	 * Helper to create a converter with hideSkippedSpecs=true and a fresh event list.
	 */
	private TestBoxOutputToGeneralTestEventsConverter createHideSkippedConverter( List<String> targetEvents ) {
		com.intellij.execution.configurations.RunProfile	dummyProfile	= new com.intellij.execution.configurations.RunProfile() {

																				@Override
																				public com.intellij.execution.configurations.RunProfileState getState(
																				    @NotNull com.intellij.execution.Executor executor,
																				    @NotNull com.intellij.execution.runners.ExecutionEnvironment environment ) {
																					return null;
																				}

																				@Override
																				public @NotNull String getName() {
																					return "TestBoxTest";
																				}

																				@Override
																				public @Nullable javax.swing.Icon getIcon() {
																					return null;
																				}
																			};

		SMTRunnerConsoleProperties							properties		= new SMTRunnerConsoleProperties(
		    getProject(),
		    dummyProfile,
		    "TestBox",
		    DefaultRunExecutor.getRunExecutorInstance()
		);

		TestBoxOutputToGeneralTestEventsConverter			conv			= new TestBoxOutputToGeneralTestEventsConverter(
		    "TestBox",
		    properties,
		    true
		);
		conv.setProcessor( new RecordingProcessor( getProject(), targetEvents ) );
		return conv;
	}

	public void testHideSkippedSpecsHidesSkippedSpecs() {
		List<String>								hideEvents	= new ArrayList<>();
		TestBoxOutputToGeneralTestEventsConverter	hideConv	= createHideSkippedConverter( hideEvents );

		String										json		= """
		                                                          {
		                                                            "bundleStats": [{
		                                                              "name": "MyBundle",
		                                                              "path": "tests.specs.MyBundleSpec",
		                                                              "totalDuration": 100,
		                                                              "globalException": "",
		                                                              "suiteStats": [{
		                                                                "name": "My Suite",
		                                                                "totalDuration": 50,
		                                                                "totalPass": 1,
		                                                                "totalFail": 0,
		                                                                "totalError": 0,
		                                                                "specStats": [
		                                                                  {
		                                                                    "name": "targeted spec",
		                                                                    "status": "Passed",
		                                                                    "totalDuration": 10,
		                                                                    "error": {},
		                                                                    "failMessage": "",
		                                                                    "failOrigin": {}
		                                                                  },
		                                                                  {
		                                                                    "name": "skipped sibling",
		                                                                    "status": "Skipped",
		                                                                    "totalDuration": 0,
		                                                                    "error": {},
		                                                                    "failMessage": "",
		                                                                    "failOrigin": {}
		                                                                  }
		                                                                ],
		                                                                "suiteStats": []
		                                                              }]
		                                                            }]
		                                                          }
		                                                          """;
		hideConv.tryParseResults( json );

		// The targeted spec should appear
		assertTrue( "Should emit testStarted for targeted spec",
		    hideEvents.stream().anyMatch( e -> e.contains( "testStarted:targeted spec" ) ) );
		assertTrue( "Should emit testFinished for targeted spec",
		    hideEvents.stream().anyMatch( e -> e.contains( "testFinished:targeted spec" ) ) );

		// The skipped sibling should NOT appear at all (no testIgnored event)
		assertFalse( "Should NOT emit testIgnored for skipped sibling when hideSkippedSpecs=true",
		    hideEvents.stream().anyMatch( e -> e.contains( "testIgnored:skipped sibling" ) ) );
		assertFalse( "Should NOT emit testStarted for skipped sibling",
		    hideEvents.stream().anyMatch( e -> e.contains( "testStarted:skipped sibling" ) ) );
	}

	public void testHideSkippedSpecsHidesEntireSkippedSuites() {
		List<String>								hideEvents	= new ArrayList<>();
		TestBoxOutputToGeneralTestEventsConverter	hideConv	= createHideSkippedConverter( hideEvents );

		String										json		= """
		                                                          {
		                                                            "bundleStats": [{
		                                                              "name": "MyBundle",
		                                                              "path": "tests.specs.MyBundleSpec",
		                                                              "totalDuration": 200,
		                                                              "globalException": "",
		                                                              "suiteStats": [
		                                                                {
		                                                                  "name": "Active Suite",
		                                                                  "status": "Passed",
		                                                                  "totalDuration": 100,
		                                                                  "totalPass": 1,
		                                                                  "totalFail": 0,
		                                                                  "totalError": 0,
		                                                                  "specStats": [{
		                                                                    "name": "active test",
		                                                                    "status": "Passed",
		                                                                    "totalDuration": 10,
		                                                                    "error": {},
		                                                                    "failMessage": "",
		                                                                    "failOrigin": {}
		                                                                  }],
		                                                                  "suiteStats": []
		                                                                },
		                                                                {
		                                                                  "name": "Skipped Suite",
		                                                                  "status": "Skipped",
		                                                                  "totalDuration": 0,
		                                                                  "totalPass": 0,
		                                                                  "totalFail": 0,
		                                                                  "totalError": 0,
		                                                                  "specStats": [{
		                                                                    "name": "skipped test",
		                                                                    "status": "Skipped",
		                                                                    "totalDuration": 0,
		                                                                    "error": {},
		                                                                    "failMessage": "",
		                                                                    "failOrigin": {}
		                                                                  }],
		                                                                  "suiteStats": []
		                                                                }
		                                                              ]
		                                                            }]
		                                                          }
		                                                          """;
		hideConv.tryParseResults( json );

		// The active suite and its spec should appear
		assertTrue( "Should emit suiteStarted for Active Suite",
		    hideEvents.stream().anyMatch( e -> e.contains( "suiteStarted:Active Suite" ) ) );
		assertTrue( "Should emit testStarted for active test",
		    hideEvents.stream().anyMatch( e -> e.contains( "testStarted:active test" ) ) );

		// The entire skipped suite should NOT appear
		assertFalse( "Should NOT emit suiteStarted for Skipped Suite",
		    hideEvents.stream().anyMatch( e -> e.contains( "suiteStarted:Skipped Suite" ) ) );
		assertFalse( "Should NOT emit suiteFinished for Skipped Suite",
		    hideEvents.stream().anyMatch( e -> e.contains( "suiteFinished:Skipped Suite" ) ) );
		assertFalse( "Should NOT emit testIgnored for skipped test in skipped suite",
		    hideEvents.stream().anyMatch( e -> e.contains( "testIgnored:skipped test" ) ) );
	}

	public void testHideSkippedSpecsStillShowsFailedSpecs() {
		List<String>								hideEvents	= new ArrayList<>();
		TestBoxOutputToGeneralTestEventsConverter	hideConv	= createHideSkippedConverter( hideEvents );

		String										json		= """
		                                                          {
		                                                            "bundleStats": [{
		                                                              "name": "MyBundle",
		                                                              "path": "tests.specs.MyBundleSpec",
		                                                              "totalDuration": 100,
		                                                              "globalException": "",
		                                                              "suiteStats": [{
		                                                                "name": "My Suite",
		                                                                "totalDuration": 50,
		                                                                "totalPass": 0,
		                                                                "totalFail": 1,
		                                                                "totalError": 0,
		                                                                "specStats": [
		                                                                  {
		                                                                    "name": "failing spec",
		                                                                    "status": "Failed",
		                                                                    "totalDuration": 10,
		                                                                    "error": {},
		                                                                    "failMessage": "Expected true",
		                                                                    "failDetail": "",
		                                                                    "failOrigin": {},
		                                                                    "failStacktrace": ""
		                                                                  },
		                                                                  {
		                                                                    "name": "skipped spec",
		                                                                    "status": "Skipped",
		                                                                    "totalDuration": 0,
		                                                                    "error": {},
		                                                                    "failMessage": "",
		                                                                    "failOrigin": {}
		                                                                  }
		                                                                ],
		                                                                "suiteStats": []
		                                                              }]
		                                                            }]
		                                                          }
		                                                          """;
		hideConv.tryParseResults( json );

		// The failing spec should still appear
		assertTrue( "Should emit testStarted for failing spec",
		    hideEvents.stream().anyMatch( e -> e.contains( "testStarted:failing spec" ) ) );
		assertTrue( "Should emit testFailed for failing spec",
		    hideEvents.stream().anyMatch( e -> e.contains( "testFailed:failing spec" ) ) );

		// The skipped spec should be hidden
		assertFalse( "Should NOT emit testIgnored for skipped spec",
		    hideEvents.stream().anyMatch( e -> e.contains( "testIgnored:skipped spec" ) ) );
	}

	/**
	 * Regression test for the TestBox filter-specs suite status bug:
	 * When running a single spec, TestBox may mark the parent suite's status as "Skipped"
	 * even though it contains a passed spec (totalPass > 0). The hideSkippedSpecs logic
	 * must use totalPass + totalFail + totalError counters (not the status field) to
	 * determine whether a suite has non-skipped content.
	 */
	public void testHideSkippedSpecsShowsSuiteWithSkippedStatusButPassedContent() {
		List<String>								hideEvents	= new ArrayList<>();
		TestBoxOutputToGeneralTestEventsConverter	hideConv	= createHideSkippedConverter( hideEvents );

		// Simulate the TestBox bug: suite status is "Skipped" but totalPass is 1
		String										json		= """
		                                                          {
		                                                            "bundleStats": [{
		                                                              "name": "MyBundle",
		                                                              "path": "tests.specs.MyBundleSpec",
		                                                              "totalDuration": 200,
		                                                              "globalException": "",
		                                                              "suiteStats": [
		                                                                {
		                                                                  "name": "A spec",
		                                                                  "status": "Skipped",
		                                                                  "totalDuration": 100,
		                                                                  "totalPass": 1,
		                                                                  "totalFail": 0,
		                                                                  "totalError": 0,
		                                                                  "specStats": [
		                                                                    {
		                                                                      "name": "targeted spec",
		                                                                      "status": "Passed",
		                                                                      "totalDuration": 25,
		                                                                      "error": {},
		                                                                      "failMessage": "",
		                                                                      "failOrigin": {}
		                                                                    },
		                                                                    {
		                                                                      "name": "skipped sibling",
		                                                                      "status": "Skipped",
		                                                                      "totalDuration": 0,
		                                                                      "error": {},
		                                                                      "failMessage": "",
		                                                                      "failOrigin": {}
		                                                                    }
		                                                                  ],
		                                                                  "suiteStats": []
		                                                                },
		                                                                {
		                                                                  "name": "Custom Matchers",
		                                                                  "status": "Passed",
		                                                                  "totalDuration": 0,
		                                                                  "totalPass": 0,
		                                                                  "totalFail": 0,
		                                                                  "totalError": 0,
		                                                                  "specStats": [
		                                                                    {
		                                                                      "name": "skipped matcher",
		                                                                      "status": "Skipped",
		                                                                      "totalDuration": 0,
		                                                                      "error": {},
		                                                                      "failMessage": "",
		                                                                      "failOrigin": {}
		                                                                    }
		                                                                  ],
		                                                                  "suiteStats": []
		                                                                }
		                                                              ]
		                                                            }]
		                                                          }
		                                                          """;
		hideConv.tryParseResults( json );

		// "A spec" has status "Skipped" but totalPass=1, so it should be shown
		assertTrue( "Should emit suiteStarted for 'A spec' despite status=Skipped",
		    hideEvents.stream().anyMatch( e -> e.contains( "suiteStarted:A spec" ) ) );
		assertTrue( "Should emit testStarted for targeted spec",
		    hideEvents.stream().anyMatch( e -> e.contains( "testStarted:targeted spec" ) ) );
		assertTrue( "Should emit testFinished for targeted spec",
		    hideEvents.stream().anyMatch( e -> e.contains( "testFinished:targeted spec" ) ) );

		// "Custom Matchers" has status "Passed" but totalPass=0, so it should be hidden
		assertFalse( "Should NOT emit suiteStarted for 'Custom Matchers' despite status=Passed",
		    hideEvents.stream().anyMatch( e -> e.contains( "suiteStarted:Custom Matchers" ) ) );

		// Skipped specs should be hidden in both suites
		assertFalse( "Should NOT emit testIgnored for skipped sibling",
		    hideEvents.stream().anyMatch( e -> e.contains( "testIgnored:skipped sibling" ) ) );
		assertFalse( "Should NOT emit testIgnored for skipped matcher",
		    hideEvents.stream().anyMatch( e -> e.contains( "testIgnored:skipped matcher" ) ) );
	}

	public void testDefaultConverterShowsSkippedSpecs() {
		// Verify the default converter (hideSkippedSpecs=false) DOES show skipped specs
		String json = """
		              {
		                "bundleStats": [{
		                  "name": "MyBundle",
		                  "path": "tests.specs.MyBundleSpec",
		                  "totalDuration": 100,
		                  "globalException": "",
		                  "suiteStats": [{
		                    "name": "My Suite",
		                    "totalDuration": 50,
		                    "specStats": [
		                      {
		                        "name": "passing spec",
		                        "status": "Passed",
		                        "totalDuration": 10,
		                        "error": {},
		                        "failMessage": "",
		                        "failOrigin": {}
		                      },
		                      {
		                        "name": "skipped spec",
		                        "status": "Skipped",
		                        "totalDuration": 0,
		                        "error": {},
		                        "failMessage": "",
		                        "failOrigin": {}
		                      }
		                    ],
		                    "suiteStats": []
		                  }]
		                }]
		              }
		              """;
		converter.tryParseResults( json );

		// Default converter should show both
		assertTrue( "Should emit testStarted for passing spec", hasEvent( "testStarted:passing spec" ) );
		assertTrue( "Should emit testIgnored for skipped spec", hasEvent( "testIgnored:skipped spec" ) );
	}

	// -------------------------------------------------------------------------
	// buildStacktrace tests
	// -------------------------------------------------------------------------

	public void testBuildStacktraceWithFailDetailOnly() {
		JsonObject spec = new JsonObject();
		spec.addProperty( "failDetail", "Expected 5 but got 3" );
		spec.addProperty( "failExtendedInfo", "" );
		spec.addProperty( "failStacktrace", "" );
		spec.add( "failOrigin", new JsonObject() ); // not an array

		String result = TestBoxOutputToGeneralTestEventsConverter.buildStacktrace( spec );
		assertEquals( "Expected 5 but got 3\n", result );
	}

	public void testBuildStacktraceWithDetailAndExtendedInfo() {
		JsonObject spec = new JsonObject();
		spec.addProperty( "failDetail", "Assertion failed" );
		spec.addProperty( "failExtendedInfo", "Extra context here" );
		spec.addProperty( "failStacktrace", "" );
		spec.add( "failOrigin", new JsonObject() );

		String result = TestBoxOutputToGeneralTestEventsConverter.buildStacktrace( spec );
		assertTrue( "Should contain failDetail", result.contains( "Assertion failed" ) );
		assertTrue( "Should contain failExtendedInfo", result.contains( "Extra context here" ) );
	}

	public void testBuildStacktraceWithTagContextArray() {
		JsonObject spec = new JsonObject();
		spec.addProperty( "failDetail", "" );
		spec.addProperty( "failExtendedInfo", "" );
		spec.addProperty( "failStacktrace", "" );

		JsonArray	tagContext	= new JsonArray();
		JsonObject	entry1		= new JsonObject();
		entry1.addProperty( "template", "/app/tests/MySpec.bx" );
		entry1.addProperty( "lineNumber", 42 );
		entry1.addProperty( "codePrintPlain", "expect( result ).toBe( 5 )" );
		tagContext.add( entry1 );

		JsonObject entry2 = new JsonObject();
		entry2.addProperty( "template", "/app/tests/BaseSpec.cfc" );
		entry2.addProperty( "lineNumber", 100 );
		entry2.addProperty( "codePrintPlain", "" );
		tagContext.add( entry2 );

		spec.add( "failOrigin", tagContext );

		String result = TestBoxOutputToGeneralTestEventsConverter.buildStacktrace( spec );
		assertTrue( "Should contain first template with line", result.contains( "at /app/tests/MySpec.bx:42" ) );
		assertTrue( "Should contain code print", result.contains( "expect( result ).toBe( 5 )" ) );
		assertTrue( "Should contain second template", result.contains( "at /app/tests/BaseSpec.cfc:100" ) );
	}

	public void testBuildStacktraceWithJavaStacktrace() {
		JsonObject spec = new JsonObject();
		spec.addProperty( "failDetail", "" );
		spec.addProperty( "failExtendedInfo", "" );
		spec.addProperty( "failStacktrace", "at java.lang.NullPointerException\n\tat com.foo.Bar.baz(Bar.java:10)" );
		spec.add( "failOrigin", new JsonObject() );

		String result = TestBoxOutputToGeneralTestEventsConverter.buildStacktrace( spec );
		assertTrue( "Should contain Java stacktrace", result.contains( "java.lang.NullPointerException" ) );
	}

	public void testBuildStacktraceFallsBackToErrorStruct() {
		// When all failXxx fields are empty, should extract from error struct
		JsonObject spec = new JsonObject();
		spec.addProperty( "failDetail", "" );
		spec.addProperty( "failExtendedInfo", "" );
		spec.addProperty( "failStacktrace", "" );
		spec.add( "failOrigin", new JsonObject() );

		JsonObject error = new JsonObject();
		error.addProperty( "Detail", "Error detail from struct" );
		error.addProperty( "StackTrace", "at ortus.boxlang.SomeClass.run(SomeClass.java:99)" );
		spec.add( "error", error );

		String result = TestBoxOutputToGeneralTestEventsConverter.buildStacktrace( spec );
		assertTrue( "Should contain error detail", result.contains( "Error detail from struct" ) );
		assertTrue( "Should contain error stacktrace", result.contains( "SomeClass.run" ) );
	}

	public void testBuildStacktraceErrorStructWithTagContext() {
		JsonObject spec = new JsonObject();
		spec.addProperty( "failDetail", "" );
		spec.addProperty( "failExtendedInfo", "" );
		spec.addProperty( "failStacktrace", "" );
		spec.add( "failOrigin", new JsonObject() );

		JsonObject error = new JsonObject();
		error.addProperty( "Detail", "" );
		JsonArray	tagContext	= new JsonArray();
		JsonObject	entry		= new JsonObject();
		entry.addProperty( "template", "/app/src/Service.bx" );
		entry.addProperty( "lineNumber", 55 );
		entry.addProperty( "codePrintPlain", "" );
		tagContext.add( entry );
		error.add( "TagContext", tagContext );
		spec.add( "error", error );

		String result = TestBoxOutputToGeneralTestEventsConverter.buildStacktrace( spec );
		assertTrue( "Should format TagContext from error struct", result.contains( "at /app/src/Service.bx:55" ) );
	}

	public void testBuildStacktraceEmptyWhenNothingAvailable() {
		JsonObject spec = new JsonObject();
		spec.addProperty( "failDetail", "" );
		spec.addProperty( "failExtendedInfo", "" );
		spec.addProperty( "failStacktrace", "" );
		spec.add( "failOrigin", new JsonObject() );
		spec.add( "error", new JsonObject() );

		String result = TestBoxOutputToGeneralTestEventsConverter.buildStacktrace( spec );
		assertEquals( "Should be empty when no failure info", "", result );
	}

	public void testBuildStacktraceDoesNotDumpRawJsonForNonStringFields() {
		// failOrigin as an object (not array) and error as an object with no useful fields
		// should NOT result in raw JSON being dumped
		JsonObject spec = new JsonObject();
		spec.addProperty( "failDetail", "" );
		spec.addProperty( "failExtendedInfo", "" );
		spec.addProperty( "failStacktrace", "" );

		// failOrigin as a complex object instead of an array
		JsonObject weirdOrigin = new JsonObject();
		weirdOrigin.addProperty( "foo", "bar" );
		spec.add( "failOrigin", weirdOrigin );

		spec.add( "error", new JsonObject() );

		String result = TestBoxOutputToGeneralTestEventsConverter.buildStacktrace( spec );
		assertFalse( "Should NOT contain raw JSON braces", result.contains( "{" ) );
	}

	// -------------------------------------------------------------------------
	// formatTagContext tests
	// -------------------------------------------------------------------------

	public void testFormatTagContextWithValidArray() {
		JsonArray	tagContext	= new JsonArray();

		JsonObject	entry		= new JsonObject();
		entry.addProperty( "template", "/app/tests/UserSpec.bx" );
		entry.addProperty( "lineNumber", 25 );
		entry.addProperty( "codePrintPlain", "  expect( user ).toBeNull()" );
		tagContext.add( entry );

		String result = TestBoxOutputToGeneralTestEventsConverter.formatTagContext( tagContext );
		assertEquals( "at /app/tests/UserSpec.bx:25 — expect( user ).toBeNull()", result );
	}

	public void testFormatTagContextMultipleEntries() {
		JsonArray	tagContext	= new JsonArray();

		JsonObject	entry1		= new JsonObject();
		entry1.addProperty( "template", "/app/tests/MySpec.bx" );
		entry1.addProperty( "lineNumber", 10 );
		entry1.addProperty( "codePrintPlain", "" );
		tagContext.add( entry1 );

		JsonObject entry2 = new JsonObject();
		entry2.addProperty( "template", "/app/src/Service.bx" );
		entry2.addProperty( "lineNumber", 20 );
		entry2.addProperty( "codePrintPlain", "" );
		tagContext.add( entry2 );

		String result = TestBoxOutputToGeneralTestEventsConverter.formatTagContext( tagContext );
		assertEquals( "at /app/tests/MySpec.bx:10\nat /app/src/Service.bx:20", result );
	}

	public void testFormatTagContextNull() {
		assertEquals( "", TestBoxOutputToGeneralTestEventsConverter.formatTagContext( null ) );
	}

	public void testFormatTagContextJsonNull() {
		assertEquals( "", TestBoxOutputToGeneralTestEventsConverter.formatTagContext( JsonNull.INSTANCE ) );
	}

	public void testFormatTagContextNotAnArray() {
		assertEquals( "", TestBoxOutputToGeneralTestEventsConverter.formatTagContext( new JsonObject() ) );
		assertEquals( "", TestBoxOutputToGeneralTestEventsConverter.formatTagContext( new JsonPrimitive( "foo" ) ) );
	}

	public void testFormatTagContextSkipsEntriesWithoutTemplate() {
		JsonArray	tagContext	= new JsonArray();

		JsonObject	noTemplate	= new JsonObject();
		noTemplate.addProperty( "lineNumber", 5 );
		tagContext.add( noTemplate );

		JsonObject withTemplate = new JsonObject();
		withTemplate.addProperty( "template", "/app/tests/Foo.bx" );
		withTemplate.addProperty( "lineNumber", 10 );
		withTemplate.addProperty( "codePrintPlain", "" );
		tagContext.add( withTemplate );

		String result = TestBoxOutputToGeneralTestEventsConverter.formatTagContext( tagContext );
		assertEquals( "at /app/tests/Foo.bx:10", result );
	}

	public void testFormatTagContextWithZeroLineNumber() {
		JsonArray	tagContext	= new JsonArray();

		JsonObject	entry		= new JsonObject();
		entry.addProperty( "template", "/app/tests/Foo.bx" );
		entry.addProperty( "lineNumber", 0 );
		entry.addProperty( "codePrintPlain", "" );
		tagContext.add( entry );

		String result = TestBoxOutputToGeneralTestEventsConverter.formatTagContext( tagContext );
		assertEquals( "at /app/tests/Foo.bx", result );
	}

	// -------------------------------------------------------------------------
	// Integration test: failure event stacktrace format
	// -------------------------------------------------------------------------

	public void testFailedSpecEmitsFormattedStacktrace() {
		// Verify that when processing a failed spec with tag context,
		// the event's stacktrace is properly formatted (not raw JSON)
		String json = """
		              {
		                "bundleStats": [{
		                  "name": "MyBundle",
		                  "path": "tests.specs.MyBundleSpec",
		                  "totalDuration": 100,
		                  "globalException": "",
		                  "suiteStats": [{
		                    "name": "My Suite",
		                    "totalDuration": 50,
		                    "specStats": [{
		                      "name": "formatted failure",
		                      "status": "Failed",
		                      "totalDuration": 10,
		                      "error": {},
		                      "failMessage": "Expected 5 but got 3",
		                      "failDetail": "Values differ",
		                      "failOrigin": [
		                        {
		                          "template": "/app/tests/MathSpec.bx",
		                          "lineNumber": 42,
		                          "codePrintPlain": "expect( add(1,2) ).toBe( 5 )"
		                        }
		                      ],
		                      "failExtendedInfo": "",
		                      "failStacktrace": ""
		                    }],
		                    "suiteStats": []
		                  }]
		                }]
		              }
		              """;
		converter.tryParseResults( json );

		assertTrue( "Should emit testFailed", hasEvent( "testFailed:formatted failure" ) );
		// The testFailed event should have the formatted message, not raw JSON
		assertTrue( "Should have failure message", hasEvent( "Expected 5 but got 3" ) );
		// Raw JSON dumps should NOT appear in events
		assertFalse( "Should NOT dump raw failOrigin JSON",
		    events.stream().anyMatch( e -> e.contains( "\"template\"" ) ) );
	}

	public void testErrorSpecWithTagContextInErrorStruct() {
		String json = """
		              {
		                "bundleStats": [{
		                  "name": "MyBundle",
		                  "path": "tests.specs.MyBundleSpec",
		                  "totalDuration": 100,
		                  "globalException": "",
		                  "suiteStats": [{
		                    "name": "My Suite",
		                    "totalDuration": 50,
		                    "specStats": [{
		                      "name": "error with tag context",
		                      "status": "Error",
		                      "totalDuration": 10,
		                      "error": {
		                        "type": "RuntimeException",
		                        "message": "Null reference",
		                        "Detail": "Cannot invoke method on null",
		                        "TagContext": [
		                          {
		                            "template": "/app/src/UserService.bx",
		                            "lineNumber": 88,
		                            "codePrintPlain": "user.getName()"
		                          }
		                        ],
		                        "StackTrace": "at ortus.boxlang.runtime.Foo.bar(Foo.java:10)"
		                      },
		                      "failMessage": "",
		                      "failDetail": "",
		                      "failOrigin": {},
		                      "failExtendedInfo": "",
		                      "failStacktrace": ""
		                    }],
		                    "suiteStats": []
		                  }]
		                }]
		              }
		              """;
		converter.tryParseResults( json );

		assertTrue( "Should emit testFailed", hasEvent( "testFailed:error with tag context" ) );
		assertTrue( "Should extract error type+message", hasEvent( "RuntimeException: Null reference" ) );
	}
}
