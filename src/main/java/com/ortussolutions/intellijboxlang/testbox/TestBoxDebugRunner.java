package com.ortussolutions.intellijboxlang.testbox;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.GenericProgramRunner;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugProcessStarter;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.ortussolutions.intellijboxlang.debug.BoxLangDapService;
import com.ortussolutions.intellijboxlang.debug.BoxLangDebugProcess;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Program runner that handles debug execution of TestBox tests.
 * Starts the DAP server and creates a debug session using the TestBox runner script.
 *
 * Builds the same TestBox CLI arguments as {@link TestBoxRunProfileState}, including
 * MD5-hashed --filter-specs values (required due to TestBox's canRunSpec() bug),
 * --reporter=console for human-readable output, and --write-json-report for structured results.
 */
public class TestBoxDebugRunner extends GenericProgramRunner<RunnerSettings> {

	private static final String RUNNER_ID = "TestBoxDebugRunner";

	@Override
	public @NotNull String getRunnerId() {
		return RUNNER_ID;
	}

	@Override
	public boolean canRun( @NotNull String executorId, @NotNull RunProfile profile ) {
		return DefaultDebugExecutor.EXECUTOR_ID.equals( executorId )
		    && profile instanceof TestBoxRunConfiguration;
	}

	@Override
	protected @Nullable RunContentDescriptor doExecute( @NotNull RunProfileState state,
	    @NotNull ExecutionEnvironment environment ) throws ExecutionException {
		if ( ! ( state instanceof TestBoxRunProfileState ) ) {
			throw new ExecutionException( "Invalid run profile state" );
		}

		TestBoxRunConfiguration	configuration	= ( TestBoxRunConfiguration ) environment.getRunProfile();
		Project					project			= environment.getProject();

		// Resolve the testbox runner script path
		Path					runnerScript	= TestBoxUtil.findTestBoxRunner( project );
		if ( runnerScript == null ) {
			throw new ExecutionException(
			    "TestBox runner not found. Please ensure TestBox is installed in your project root (testbox/ folder).\n"
			        + "You can install it with: box install testbox --saveDev" );
		}

		// Build the script path with arguments for the runner
		String			scriptPath			= runnerScript.toString();
		List<String>	programArgs			= buildTestBoxArgs( configuration );

		String			workingDirectory	= configuration.getWorkingDirectory();
		if ( workingDirectory == null || workingDirectory.isBlank() ) {
			workingDirectory = project.getBasePath();
		}

		final String		finalScriptPath			= scriptPath;
		final String		finalWorkingDirectory	= workingDirectory;
		final List<String>	finalProgramArgs		= programArgs;

		try {
			XDebugSession debugSession = XDebuggerManager.getInstance( project ).startSession(
			    environment,
			    new XDebugProcessStarter() {

				    @Override
				    public @NotNull XDebugProcess start( @NotNull XDebugSession session ) throws ExecutionException {
					    return createDebugProcess(
					        session,
					        environment,
					        finalScriptPath,
					        finalWorkingDirectory,
					        finalProgramArgs
					    );
				    }
			    }
			);

			return debugSession.getRunContentDescriptor();
		} catch ( Exception e ) {
			throw new ExecutionException( "Failed to start debug session: " + e.getMessage(), e );
		}
	}

	private BoxLangDebugProcess createDebugProcess( @NotNull XDebugSession session,
	    @NotNull ExecutionEnvironment environment,
	    @NotNull String scriptPath,
	    @Nullable String workingDirectory,
	    @Nullable List<String> programArgs ) throws ExecutionException {
		Project				project		= environment.getProject();
		BoxLangDapService	dapService	= new BoxLangDapService( project );

		try {
			dapService.start();
			return new BoxLangDebugProcess( session, dapService, scriptPath, workingDirectory, programArgs );
		} catch ( Exception e ) {
			dapService.dispose();
			throw new ExecutionException( "Failed to start DAP server: " + e.getMessage(), e );
		}
	}

	/**
	 * Builds TestBox CLI arguments for the debug session.
	 * Uses --reporter=console for human-readable output and --write-json-report for structured data.
	 * Applies MD5 hashing to --filter-specs values (required due to TestBox's canRunSpec() bug).
	 */
	private List<String> buildTestBoxArgs( @NotNull TestBoxRunConfiguration configuration ) {
		List<String>	args	= new ArrayList<>();
		String			scope	= configuration.getTestScope();

		// Use console reporter for human-readable output + JSON report file for structured results.
		// This matches the run mode behavior in TestBoxRunProfileState.
		args.add( "--reporter=console" );
		args.add( "--write-json-report" );

		if ( "BUNDLE".equals( scope ) ) {
			String bundlePath = configuration.getBundlePath();
			if ( bundlePath != null && !bundlePath.isBlank() ) {
				args.add( "--bundles=" + bundlePath );
			}
		} else if ( "DIRECTORY".equals( scope ) ) {
			String directory = configuration.getDirectory();
			if ( directory != null && !directory.isBlank() ) {
				args.add( "--directory=" + directory );
			}
		}

		// TestBox's canRunSpec() matches filter-specs against spec.id (an MD5 hash),
		// not spec.name. We must hash the spec names before passing them.
		String filterSpecs = configuration.getFilterSpecs();
		if ( filterSpecs != null && !filterSpecs.isBlank() ) {
			args.add( "--filter-specs=" + TestBoxRunProfileState.hashSpecFilter( filterSpecs ) );
			// Tell the ConsoleReporter to hide skipped specs/suites from output.
			// When running a single spec, the user wants to see only the targeted test.
			args.add( "--hide-skipped" );
		}

		String filterSuites = configuration.getFilterSuites();
		if ( filterSuites != null && !filterSuites.isBlank() ) {
			args.add( "--filter-suites=" + filterSuites );
			// Tell the ConsoleReporter to hide skipped specs/suites from output.
			// When running a single suite, the user wants to see only the targeted suite.
			args.add( "--hide-skipped" );
		}

		String labels = configuration.getLabels();
		if ( labels != null && !labels.isBlank() ) {
			args.add( "--labels=" + labels );
		}

		String excludes = configuration.getExcludes();
		if ( excludes != null && !excludes.isBlank() ) {
			args.add( "--excludes=" + excludes );
		}

		if ( configuration.isVerbose() ) {
			args.add( "--verbose" );
		}

		if ( configuration.isEagerFailure() ) {
			args.add( "--eager-failure" );
		}

		return args;
	}
}
