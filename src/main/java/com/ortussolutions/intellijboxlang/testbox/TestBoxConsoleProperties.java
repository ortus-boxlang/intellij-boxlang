package com.ortussolutions.intellijboxlang.testbox;

import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.sm.SMCustomMessagesParsing;
import com.intellij.execution.testframework.sm.runner.OutputToGeneralTestEventsConverter;
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties;
import com.intellij.execution.testframework.sm.runner.SMTestLocator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * Console properties for TestBox test runs. Implements {@link SMCustomMessagesParsing}
 * so the SMTRunner framework uses our custom {@link TestBoxOutputToGeneralTestEventsConverter}
 * instead of the default one that expects {@code ##teamcity[...]} service messages.
 */
public class TestBoxConsoleProperties extends SMTRunnerConsoleProperties implements SMCustomMessagesParsing {

	private final boolean	hideSkippedSpecs;
	private Path			jsonReportPath;

	public TestBoxConsoleProperties(
	    @NotNull RunConfiguration configuration,
	    @NotNull String testFrameworkName,
	    @NotNull Executor executor ) {
		super( configuration, testFrameworkName, executor );
		// Use id-based test tree so that test/suite names don't need to be globally unique.
		// BDD-style specs commonly have duplicate names across describe blocks (e.g.,
		// it("should work") in multiple suites). The id-based convertor uses unique
		// ids for lookup instead of raw names, avoiding map collisions.
		setIdBasedTestTree( true );
		// When running a single spec or suite via filter-specs/filter-suites, hide skipped
		// specs from the tree. The user clicked on one test or suite; they want to see
		// that result, not all the sibling specs/suites that TestBox skipped.
		if ( configuration instanceof TestBoxRunConfiguration testBoxConfig ) {
			String	filterSpecs		= testBoxConfig.getFilterSpecs();
			String	filterSuites	= testBoxConfig.getFilterSuites();
			this.hideSkippedSpecs = ( filterSpecs != null && !filterSpecs.isBlank() )
			    || ( filterSuites != null && !filterSuites.isBlank() );
		} else {
			this.hideSkippedSpecs = false;
		}
	}

	/**
	 * Sets the path to the JSON report file that the converter will read after
	 * the process terminates. This is set by {@link TestBoxRunProfileState}
	 * after computing the report path.
	 */
	public void setJsonReportPath( @NotNull Path jsonReportPath ) {
		this.jsonReportPath = jsonReportPath;
	}

	/**
	 * Returns the path to the JSON report file.
	 */
	public @Nullable Path getJsonReportPath() {
		return jsonReportPath;
	}

	@Override
	public @Nullable SMTestLocator getTestLocator() {
		return TestBoxTestLocator.INSTANCE;
	}

	@Override
	public OutputToGeneralTestEventsConverter createTestEventsConverter(
	    @NotNull String testFrameworkName,
	    @NotNull TestConsoleProperties consoleProperties ) {
		return new TestBoxOutputToGeneralTestEventsConverter( testFrameworkName, consoleProperties, hideSkippedSpecs );
	}
}
