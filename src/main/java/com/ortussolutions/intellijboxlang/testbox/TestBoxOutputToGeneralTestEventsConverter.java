package com.ortussolutions.intellijboxlang.testbox;

import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.sm.runner.GeneralTestEventsProcessor;
import com.intellij.execution.testframework.sm.runner.OutputToGeneralTestEventsConverter;
import com.intellij.execution.testframework.sm.runner.events.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Parses TestBox JSON results and emits SMTRunner test events to populate
 * the IntelliJ test result tree.
 *
 * This converter extends the framework's {@link OutputToGeneralTestEventsConverter}
 * and is registered via {@link TestBoxConsoleProperties} implementing
 * {@link com.intellij.execution.testframework.sm.SMCustomMessagesParsing}.
 *
 * <h3>Architecture</h3>
 * TestBox runs with {@code --reporter=console} for pretty ANSI output on stdout
 * (displayed in the console panel) and {@code --write-json-report} to write
 * structured JSON to a file. This converter:
 * <ol>
 * <li>Forwards all stdout/stderr to the console panel as uncaptured output</li>
 * <li>When the process terminates, reads the JSON report file and emits
 * structured test events to populate the test tree</li>
 * </ol>
 */
public class TestBoxOutputToGeneralTestEventsConverter extends OutputToGeneralTestEventsConverter {

	private static final Logger			LOG				= Logger.getInstance( TestBoxOutputToGeneralTestEventsConverter.class );

	/**
	 * Counter for generating unique node IDs in the id-based test tree.
	 * Each suite and spec gets a unique ID to avoid name collisions when
	 * BDD-style specs have duplicate names across different describe blocks.
	 * Reset to 0 at the start of each test run (in emitTestEvents).
	 */
	private final AtomicInteger			nodeIdCounter	= new AtomicInteger( 0 );

	/**
	 * When true, specs with status "Skipped" are hidden from the test tree.
	 * This is set when running a single spec (filter-specs mode) so the user
	 * only sees the spec they clicked on, not all the sibling specs that
	 * TestBox skipped because they didn't match the filter.
	 */
	private final boolean				hideSkippedSpecs;

	/**
	 * Reference to the console properties for accessing the JSON report path.
	 */
	private final TestConsoleProperties	consoleProperties;

	public TestBoxOutputToGeneralTestEventsConverter(
	    @NotNull String testFrameworkName,
	    @NotNull TestConsoleProperties consoleProperties ) {
		this( testFrameworkName, consoleProperties, false );
	}

	public TestBoxOutputToGeneralTestEventsConverter(
	    @NotNull String testFrameworkName,
	    @NotNull TestConsoleProperties consoleProperties,
	    boolean hideSkippedSpecs ) {
		super( testFrameworkName, consoleProperties );
		this.consoleProperties	= consoleProperties;
		this.hideSkippedSpecs	= hideSkippedSpecs;
	}

	/**
	 * Forwards all process output to the console panel as uncaptured output.
	 * The pretty console reporter output from TestBox is displayed directly
	 * in the IDE's console tab. Test tree events are emitted separately
	 * from the JSON report file when the process terminates.
	 */
	@Override
	public void process( String text, Key outputType ) {
		fireOnUncapturedOutput( text, outputType );
	}

	/**
	 * Called by the framework when the process terminates, just before finishTesting().
	 * Reads the JSON report file written by {@code --write-json-report} and emits
	 * structured test events to populate the test tree.
	 */
	@Override
	public void flushBufferOnProcessTermination( int exitCode ) {
		Path jsonReportPath = resolveJsonReportPath();
		if ( jsonReportPath == null ) {
			LOG.warn( "No JSON report path configured; test tree will be empty" );
			return;
		}

		if ( !Files.exists( jsonReportPath ) ) {
			LOG.warn( "JSON report file not found: " + jsonReportPath
			    + "; TestBox may have failed to write results (exit code: " + exitCode + ")" );
			return;
		}

		try {
			String json = Files.readString( jsonReportPath, StandardCharsets.UTF_8 );
			tryParseResults( json );
		} catch ( IOException e ) {
			LOG.error( "Failed to read TestBox JSON report: " + jsonReportPath, e );
		} finally {
			// Clean up the temp report file and its parent directory
			cleanupReportFiles( jsonReportPath );
		}
	}

	/**
	 * Resolves the JSON report path from the console properties.
	 */
	private @Nullable Path resolveJsonReportPath() {
		if ( consoleProperties instanceof TestBoxConsoleProperties tbProps ) {
			return tbProps.getJsonReportPath();
		}
		return null;
	}

	/**
	 * Cleans up the JSON report file and its parent temp directory.
	 * The parent directory is only deleted if it is empty (i.e., it was
	 * our temp directory and the report file was the only file in it).
	 */
	private void cleanupReportFiles( @NotNull Path reportFile ) {
		try {
			Files.deleteIfExists( reportFile );
			Path parentDir = reportFile.getParent();
			if ( parentDir != null ) {
				// Only delete the parent if it's empty (our temp dir)
				try ( var stream = Files.list( parentDir ) ) {
					if ( stream.findFirst().isEmpty() ) {
						Files.deleteIfExists( parentDir );
					}
				}
			}
		} catch ( IOException e ) {
			LOG.debug( "Failed to clean up TestBox report files: " + e.getMessage() );
		}
	}

	/**
	 * Attempt to parse a JSON string as TestBox results.
	 *
	 * @return true if this was valid TestBox JSON and events were emitted
	 */
	boolean tryParseResults( String json ) {
		try {
			JsonObject root = JsonParser.parseString( json ).getAsJsonObject();
			// TestBox JSON has a top-level "bundleStats" array
			if ( root.has( "bundleStats" ) ) {
				emitTestEvents( root );
				return true;
			}
		} catch ( Exception e ) {
			LOG.warn( "Failed to parse TestBox JSON report: " + e.getMessage() );
		}
		return false;
	}

	/**
	 * Emit structured test events from the parsed TestBox JSON results.
	 *
	 * NOTE: We do NOT call processor.onStartTesting() or processor.onFinishTesting() here.
	 * The SMTRunner framework calls these automatically:
	 * - onStartTesting() is called via startTesting() when ProcessListener.startNotified fires
	 * - onFinishTesting() is called via finishTesting() when ProcessListener.processTerminated fires
	 * Calling them here would result in double-invocation, which causes the framework's
	 * isTreeComplete() check to run prematurely and mark the root as "TERMINATED".
	 */
	private void emitTestEvents( @NotNull JsonObject results ) {
		GeneralTestEventsProcessor processor = getProcessor();
		if ( processor == null ) {
			LOG.warn( "GeneralTestEventsProcessor is null; cannot emit test events" );
			return;
		}

		// Signal that the test reporter is attached. Without this, the framework
		// displays "Test framework quit unexpectedly" if no children are added to root.
		processor.onTestsReporterAttached();

		// Reset the ID counter for each run
		nodeIdCounter.set( 0 );

		JsonArray bundleStats = results.getAsJsonArray( "bundleStats" );
		if ( bundleStats == null ) {
			return;
		}

		// Top-level nodes use ROOT_NODE_ID ("0") as their parentId.
		// The id-based convertor requires a valid parentId for every node;
		// null parentId causes nodes to be silently dropped.
		String rootParentId = TreeNodeEvent.ROOT_NODE_ID;
		for ( JsonElement bundleElement : bundleStats ) {
			JsonObject bundle = bundleElement.getAsJsonObject();
			processBundleStats( processor, bundle, rootParentId );
		}
	}

	/**
	 * Generates a unique string ID for a test tree node.
	 */
	private String nextNodeId() {
		return String.valueOf( nodeIdCounter.incrementAndGet() );
	}

	private void processBundleStats( @NotNull GeneralTestEventsProcessor processor, @NotNull JsonObject bundle,
	    @NotNull String parentId ) {
		String		bundleName		= getStringOr( bundle, "name", "Unknown Bundle" );
		String		bundlePath		= getStringOr( bundle, "path", "" );
		String		locationUrl		= bundlePath.isEmpty() ? "" : TestBoxTestLocator.PROTOCOL + "://" + bundlePath;

		// Check for a global exception on the bundle
		String		globalException	= getGlobalExceptionMessage( bundle );
		JsonArray	suiteStats		= bundle.getAsJsonArray( "suiteStats" );

		// Collapse redundant nesting: when the bundle has exactly one top-level
		// suite whose name matches the bundle name (common in xUnit-style tests),
		// skip the bundle wrapper and just emit the suite directly.
		if ( globalException.isEmpty() && suiteStats != null && suiteStats.size() == 1 ) {
			JsonObject	onlySuite	= suiteStats.get( 0 ).getAsJsonObject();
			String		suiteName	= getStringOr( onlySuite, "name", "" );
			if ( bundleName.equals( suiteName ) ) {
				processSuiteStats( processor, onlySuite, bundlePath, parentId );
				return;
			}
		}

		// Start test suite for the bundle
		String bundleId = nextNodeId();
		processor.onSuiteStarted( new TestSuiteStartedEvent( bundleName, bundleId, parentId, locationUrl,
		    null, null, null, true ) );

		if ( !globalException.isEmpty() ) {
			String errorId = nextNodeId();
			processor.onTestStarted( new TestStartedEvent( "Bundle Error", errorId, bundleId, locationUrl,
			    null, null, null, true ) );
			processor.onTestFailure( new TestFailedEvent(
			    "Bundle Error", errorId, globalException, null, true, null, null, null, null, false, false, -1 ) );
			processor.onTestFinished( new TestFinishedEvent( "Bundle Error", errorId, 0L ) );
		}

		// Process suiteStats (nested suites containing specs)
		if ( suiteStats != null ) {
			for ( JsonElement suiteElement : suiteStats ) {
				processSuiteStats( processor, suiteElement.getAsJsonObject(), bundlePath, bundleId );
			}
		}

		// Finish the bundle suite
		processor.onSuiteFinished( new TestSuiteFinishedEvent( bundleName, bundleId ) );
	}

	private void processSuiteStats(
	    @NotNull GeneralTestEventsProcessor processor,
	    @NotNull JsonObject suite,
	    @NotNull String bundlePath,
	    @NotNull String parentId ) {
		String	suiteName	= getStringOr( suite, "name", "Unknown Suite" );
		String	suiteStatus	= getStringOr( suite, "status", "" ).toLowerCase();
		String	locationUrl	= bundlePath.isEmpty() ? "" : TestBoxTestLocator.PROTOCOL + "://" + bundlePath;

		// When hiding skipped specs (single-spec mode), skip suites that have
		// no non-skipped specs at any nesting level. We can't rely on suiteStatus
		// alone because TestBox may mark a suite as "Skipped" even when it contains
		// a passed spec (filter-specs suite status bug), or mark a suite as "Passed"
		// when all its specs are actually skipped. Instead, check the rolled-up
		// counters: if totalPass + totalFail + totalError == 0, the suite has
		// no non-skipped content.
		if ( hideSkippedSpecs ) {
			long	totalPass	= getLongOr( suite, "totalPass", 0 );
			long	totalFail	= getLongOr( suite, "totalFail", 0 );
			long	totalError	= getLongOr( suite, "totalError", 0 );
			if ( totalPass + totalFail + totalError == 0 ) {
				return;
			}
		}

		String suiteId = nextNodeId();
		processor.onSuiteStarted( new TestSuiteStartedEvent( suiteName, suiteId, parentId, locationUrl,
		    null, null, null, true ) );

		// Process specs in this suite
		JsonArray specStats = suite.getAsJsonArray( "specStats" );
		if ( specStats != null ) {
			for ( JsonElement specElement : specStats ) {
				processSpecStats( processor, specElement.getAsJsonObject(), bundlePath, suiteId );
			}
		}

		// Process nested suites
		JsonArray nestedSuites = suite.getAsJsonArray( "suiteStats" );
		if ( nestedSuites != null ) {
			for ( JsonElement nestedElement : nestedSuites ) {
				processSuiteStats( processor, nestedElement.getAsJsonObject(), bundlePath, suiteId );
			}
		}

		processor.onSuiteFinished( new TestSuiteFinishedEvent( suiteName, suiteId ) );
	}

	private void processSpecStats(
	    @NotNull GeneralTestEventsProcessor processor,
	    @NotNull JsonObject spec,
	    @NotNull String bundlePath,
	    @NotNull String parentId ) {
		String	specName	= getStringOr( spec, "name", "Unknown Spec" );
		String	status		= getStringOr( spec, "status", "passed" ).toLowerCase();
		long	duration	= getLongOr( spec, "totalDuration", 0 );
		String	locationUrl	= bundlePath.isEmpty() ? "" : TestBoxTestLocator.PROTOCOL + "://" + bundlePath;
		String	specId		= nextNodeId();

		switch ( status ) {
			case "skipped" :
				// When running a single spec, hide skipped specs entirely.
				// The user clicked on ONE test — they don't need to see all the
				// sibling specs that TestBox skipped because they didn't match.
				if ( !hideSkippedSpecs ) {
					// Must bracket onTestIgnored with onTestStarted/onTestFinished.
					// The name-based framework's onTestIgnored auto-starts the test (adding it to
					// myRunningTestsFullNameToProxy) but never finishes it (never removes it).
					// Without onTestFinished, orphan entries remain in the running tests map,
					// causing isTreeComplete() to return false and the root node to show "Terminated".
					// The id-based convertor handles this correctly via terminateNode(), but we
					// bracket anyway for robustness.
					processor.onTestStarted( new TestStartedEvent( specName, specId, parentId, locationUrl,
					    null, null, null, true ) );
					processor.onTestIgnored( new TestIgnoredEvent( specName, specId, "Skipped", null ) );
					processor.onTestFinished( new TestFinishedEvent( specName, specId, 0L ) );
				}
				break;

			case "failed" : {
				processor.onTestStarted( new TestStartedEvent( specName, specId, parentId, locationUrl,
				    null, null, null, true ) );
				String	failMessage	= getStringOr( spec, "failMessage", "Test failed" );
				String	stacktrace	= buildStacktrace( spec );
				processor.onTestFailure( new TestFailedEvent(
				    specName, specId, failMessage, stacktrace, false, null, null, null, null, false, false, -1 ) );
				processor.onTestFinished( new TestFinishedEvent( specName, specId, duration ) );
				break;
			}

			case "error" : {
				processor.onTestStarted( new TestStartedEvent( specName, specId, parentId, locationUrl,
				    null, null, null, true ) );
				String errorMessage = extractErrorMessage( spec );
				if ( errorMessage.isEmpty() ) {
					errorMessage = getStringOr( spec, "failMessage", "Test error" );
				}
				String stacktrace = buildStacktrace( spec );
				processor.onTestFailure( new TestFailedEvent(
				    specName, specId, errorMessage, stacktrace, true, null, null, null, null, false, false, -1 ) );
				processor.onTestFinished( new TestFinishedEvent( specName, specId, duration ) );
				break;
			}

			case "passed" :
			default :
				processor.onTestStarted( new TestStartedEvent( specName, specId, parentId, locationUrl,
				    null, null, null, true ) );
				processor.onTestFinished( new TestFinishedEvent( specName, specId, duration ) );
				break;
		}
	}

	/**
	 * Builds a human-readable stacktrace string from the spec's failure fields.
	 * Combines failDetail, failExtendedInfo, failStacktrace, and failOrigin
	 * (tag context array) into a single formatted string for display in the
	 * test runner's failure detail panel.
	 *
	 * TestBox failure fields:
	 * - failDetail: exception detail string
	 * - failExtendedInfo: extended info string
	 * - failStacktrace: Java-style stack trace string
	 * - failOrigin: array of tag context structs (template, lineNumber, codePrintPlain, etc.)
	 * - error: full exception struct with Type, Message, Detail, TagContext, StackTrace, etc.
	 */
	static @NotNull String buildStacktrace( @NotNull JsonObject spec ) {
		StringBuilder	sb		= new StringBuilder();

		// 1. failDetail
		String			detail	= getStringFieldOnly( spec, "failDetail" );
		if ( !detail.isEmpty() ) {
			sb.append( detail ).append( "\n" );
		}

		// 2. failExtendedInfo
		String extInfo = getStringFieldOnly( spec, "failExtendedInfo" );
		if ( !extInfo.isEmpty() ) {
			sb.append( extInfo ).append( "\n" );
		}

		// 3. failOrigin (tag context array) — format as stacktrace lines
		String tagContextTrace = formatTagContext( spec.get( "failOrigin" ) );
		if ( !tagContextTrace.isEmpty() ) {
			if ( sb.length() > 0 ) {
				sb.append( "\n" );
			}
			sb.append( tagContextTrace );
		}

		// 4. failStacktrace — Java-style trace; append if not redundant
		String stacktrace = getStringFieldOnly( spec, "failStacktrace" );
		if ( !stacktrace.isEmpty() ) {
			if ( sb.length() > 0 ) {
				sb.append( "\n" );
			}
			sb.append( stacktrace );
		}

		// 5. If we still have nothing, try extracting from the error struct
		if ( sb.length() == 0 ) {
			JsonElement errorEl = spec.get( "error" );
			if ( errorEl != null && errorEl.isJsonObject() ) {
				JsonObject	errorObj	= errorEl.getAsJsonObject();

				String		errorDetail	= getStringFieldOnly( errorObj, "Detail" );
				if ( errorDetail.isEmpty() ) {
					errorDetail = getStringFieldOnly( errorObj, "detail" );
				}
				if ( !errorDetail.isEmpty() ) {
					sb.append( errorDetail ).append( "\n" );
				}

				// TagContext from the error struct
				JsonElement tagCtx = errorObj.get( "TagContext" );
				if ( tagCtx == null ) {
					tagCtx = errorObj.get( "tagContext" );
				}
				String errorTagTrace = formatTagContext( tagCtx );
				if ( !errorTagTrace.isEmpty() ) {
					if ( sb.length() > 0 ) {
						sb.append( "\n" );
					}
					sb.append( errorTagTrace );
				}

				// StackTrace from the error struct
				String errorStacktrace = getStringFieldOnly( errorObj, "StackTrace" );
				if ( errorStacktrace.isEmpty() ) {
					errorStacktrace = getStringFieldOnly( errorObj, "stackTrace" );
				}
				if ( !errorStacktrace.isEmpty() ) {
					if ( sb.length() > 0 ) {
						sb.append( "\n" );
					}
					sb.append( errorStacktrace );
				}
			}
		}

		return sb.toString();
	}

	/**
	 * Formats a TagContext JSON array (from failOrigin or error.TagContext)
	 * into human-readable stacktrace-like lines:
	 * at /path/to/File.cfc:123
	 * at /path/to/OtherFile.bx:45
	 */
	static @NotNull String formatTagContext( JsonElement tagContextElement ) {
		if ( tagContextElement == null || tagContextElement.isJsonNull() ) {
			return "";
		}
		if ( !tagContextElement.isJsonArray() ) {
			return "";
		}

		JsonArray		tagContext	= tagContextElement.getAsJsonArray();
		StringBuilder	sb			= new StringBuilder();

		for ( JsonElement entry : tagContext ) {
			if ( !entry.isJsonObject() ) {
				continue;
			}
			JsonObject	ctx			= entry.getAsJsonObject();
			String		template	= getStringFieldOnly( ctx, "template" );
			long		lineNumber	= getLongOr( ctx, "lineNumber", getLongOr( ctx, "line", 0 ) );
			String		codePrint	= getStringFieldOnly( ctx, "codePrintPlain" );

			if ( template.isEmpty() ) {
				continue;
			}

			if ( sb.length() > 0 ) {
				sb.append( "\n" );
			}
			sb.append( "at " ).append( template );
			if ( lineNumber > 0 ) {
				sb.append( ":" ).append( lineNumber );
			}
			if ( !codePrint.isEmpty() ) {
				sb.append( " — " ).append( codePrint.trim() );
			}
		}

		return sb.toString();
	}

	/**
	 * Gets a string-only field value from a JSON object.
	 * Returns the default empty string if the field is missing, null,
	 * or not a primitive string (i.e., if it's a JSON object or array,
	 * this returns "" instead of dumping raw JSON).
	 */
	private static @NotNull String getStringFieldOnly( @NotNull JsonObject obj, @NotNull String key ) {
		JsonElement el = obj.get( key );
		if ( el == null || el.isJsonNull() ) {
			return "";
		}
		if ( el.isJsonPrimitive() ) {
			return el.getAsString();
		}
		// Don't dump JSON objects/arrays as strings
		return "";
	}

	/**
	 * Extract the globalException message from a bundle.
	 * The globalException field can be an empty string, a simple string,
	 * or a full exception struct with "message", "type", etc.
	 */
	private static @NotNull String getGlobalExceptionMessage( @NotNull JsonObject bundle ) {
		JsonElement el = bundle.get( "globalException" );
		if ( el == null || el.isJsonNull() ) {
			return "";
		}
		if ( el.isJsonPrimitive() ) {
			return el.getAsString();
		}
		if ( el.isJsonObject() ) {
			JsonObject	obj		= el.getAsJsonObject();
			String		message	= getStringOr( obj, "message", "" );
			String		type	= getStringOr( obj, "type", "" );
			if ( !message.isEmpty() && !type.isEmpty() ) {
				return type + ": " + message;
			}
			if ( !message.isEmpty() ) {
				return message;
			}
			if ( !type.isEmpty() ) {
				return type;
			}
			return obj.toString();
		}
		return el.toString();
	}

	static @NotNull String getStringOr( @NotNull JsonObject obj, @NotNull String key, @NotNull String defaultValue ) {
		JsonElement el = obj.get( key );
		if ( el == null || el.isJsonNull() ) {
			return defaultValue;
		}
		if ( el.isJsonObject() || el.isJsonArray() ) {
			return el.toString();
		}
		return el.getAsString();
	}

	/**
	 * Extract a human-readable error message from the spec's "error" field.
	 * In TestBox, the "error" field is the full exception struct with keys like
	 * "message", "type", "detail", "stackTrace", "tagContext".
	 * For xUnit-style errors, failMessage may not be set, so we extract from the error struct.
	 */
	static @NotNull String extractErrorMessage( @NotNull JsonObject spec ) {
		JsonElement errorEl = spec.get( "error" );
		if ( errorEl == null || errorEl.isJsonNull() ) {
			return "";
		}
		// If it's a full exception struct, extract the "message" key
		if ( errorEl.isJsonObject() ) {
			JsonObject	errorObj	= errorEl.getAsJsonObject();
			String		message		= getStringOr( errorObj, "message", "" );
			String		type		= getStringOr( errorObj, "type", "" );
			if ( !message.isEmpty() && !type.isEmpty() ) {
				return type + ": " + message;
			}
			if ( !message.isEmpty() ) {
				return message;
			}
			if ( !type.isEmpty() ) {
				return type;
			}
			return errorObj.toString();
		}
		// If it's a simple string (shouldn't happen normally), return it
		if ( errorEl.isJsonPrimitive() ) {
			return errorEl.getAsString();
		}
		return errorEl.toString();
	}

	private static long getLongOr( @NotNull JsonObject obj, @NotNull String key, long defaultValue ) {
		JsonElement el = obj.get( key );
		if ( el == null || el.isJsonNull() ) {
			return defaultValue;
		}
		try {
			return el.getAsLong();
		} catch ( Exception e ) {
			return defaultValue;
		}
	}
}
