package com.ortussolutions.intellijboxlang.testbox;

import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.CommandLineState;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.*;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.execution.ParametersListUtil;
import com.ortussolutions.intellijboxlang.runtime.BoxLangLspBootstrapService;
import com.ortussolutions.intellijboxlang.runtime.LspBootstrapResult;
import com.ortussolutions.intellijboxlang.settings.BoxLangResolvedSettings;
import com.ortussolutions.intellijboxlang.settings.BoxLangSettingsResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Defines how to execute TestBox tests.
 * Builds the command line for the TestBox CLI runner and wires up the SMTRunner
 * test console for displaying results in IntelliJ's test runner UI.
 *
 * The SMTRunner integration uses {@link TestBoxConsoleProperties} which implements
 * {@link com.intellij.execution.testframework.sm.SMCustomMessagesParsing} to provide
 * a custom {@link TestBoxOutputToGeneralTestEventsConverter}. This converter intercepts
 * the process stdout, extracts the TestBox JSON result blob, and emits structured
 * test events directly to the {@link com.intellij.execution.testframework.sm.runner.GeneralTestEventsProcessor}.
 */
public class TestBoxRunProfileState extends CommandLineState {

	private static final String				TEST_FRAMEWORK_NAME	= "TestBox";
	private final TestBoxRunConfiguration	configuration;
	private Path							reportPath;

	public TestBoxRunProfileState( TestBoxRunConfiguration configuration, ExecutionEnvironment environment ) {
		super( environment );
		this.configuration = configuration;
	}

	@Override
	protected @NotNull ProcessHandler startProcess() throws ExecutionException {
		GeneralCommandLine	commandLine		= createCommandLine();
		OSProcessHandler	processHandler	= ProcessHandlerFactory.getInstance()
		    .createColoredProcessHandler( commandLine );
		ProcessTerminatedListener.attach( processHandler );
		return processHandler;
	}

	@Override
	public @NotNull ExecutionResult execute( @NotNull Executor executor, @NotNull ProgramRunner<?> runner ) throws ExecutionException {
		ProcessHandler				processHandler	= startProcess();

		// Create TestBox console properties with SMCustomMessagesParsing support.
		// This tells the SMTRunner framework to use our TestBoxOutputToGeneralTestEventsConverter
		// which parses JSON output instead of expecting ##teamcity[...] service messages.
		TestBoxConsoleProperties	properties		= new TestBoxConsoleProperties(
		    configuration,
		    TEST_FRAMEWORK_NAME,
		    executor
		);

		// Tell the converter where to find the JSON report file after the process exits.
		// The report path was computed during createCommandLine().
		if ( reportPath != null ) {
			properties.setJsonReportPath( reportPath.resolve( "report.json" ) );
		}

		// Create the test console view with SMTRunner integration.
		// The framework will call properties.createTestEventsConverter() to get our
		// custom converter, wire it into the event pipeline, and attach it to the
		// process handler automatically.
		ConsoleView console = SMTestRunnerConnectionUtil.createAndAttachConsole(
		    TEST_FRAMEWORK_NAME,
		    processHandler,
		    properties
		);

		return new DefaultExecutionResult( console, processHandler );
	}

	private GeneralCommandLine createCommandLine() throws ExecutionException {
		Project					project		= getEnvironment().getProject();
		BoxLangResolvedSettings	settings	= BoxLangSettingsResolver.resolve( project );

		LspBootstrapResult		bootstrap;
		try {
			bootstrap = BoxLangLspBootstrapService.prepare( project );
		} catch ( Exception e ) {
			throw new ExecutionException( "Failed to prepare BoxLang runtime: " + e.getMessage(), e );
		}

		// Resolve the TestBox BoxLang runner (system/runners/BoxLangRunner.bx)
		Path runnerScript = TestBoxUtil.findTestBoxRunner( project );
		if ( runnerScript == null ) {
			throw new ExecutionException(
			    "TestBox runner not found. Please ensure TestBox is installed in your project "
			        + "(testbox/ folder with system/runners/BoxLangRunner.bx).\n"
			        + "You can install it with: box install testbox --saveDev" );
		}

		// Create a temp directory for the JSON report so we don't pollute the project
		try {
			reportPath = Files.createTempDirectory( "testbox-results-" );
		} catch ( IOException e ) {
			throw new ExecutionException( "Failed to create temp directory for TestBox report: " + e.getMessage(), e );
		}

		GeneralCommandLine commandLine = new GeneralCommandLine( resolveJavaExecutable( settings ) );
		commandLine.withCharset( StandardCharsets.UTF_8 );

		// Set up environment
		String boxLangHome = configuration.getBoxLangHome();
		if ( boxLangHome == null || boxLangHome.isBlank() ) {
			boxLangHome = bootstrap.lspBoxLangHome.toString();
		}
		commandLine.withEnvironment( "BOXLANG_HOME", boxLangHome );
		commandLine.withEnvironment( "CLASSPATH", bootstrap.boxLangJarPath.toString() );

		if ( settings.javaHome != null && !settings.javaHome.isBlank() ) {
			commandLine.withEnvironment( "JAVA_HOME", settings.javaHome );
		}
		applyEnvironmentVariables( commandLine );

		// Set working directory
		String workingDir = configuration.getWorkingDirectory();
		if ( workingDir != null && !workingDir.isBlank() ) {
			commandLine.withWorkDirectory( workingDir );
		} else {
			String projectBasePath = project.getBasePath();
			if ( projectBasePath != null ) {
				commandLine.withWorkDirectory( projectBasePath );
			}
		}

		// Add JVM arguments
		commandLine.addParameters( buildJvmArgs( settings ) );

		// Main class: BoxRunner
		commandLine.addParameter( "ortus.boxlang.runtime.BoxRunner" );

		// The TestBox runner script path
		commandLine.addParameter( runnerScript.toString() );

		// Add TestBox-specific arguments
		addTestBoxArguments( commandLine );

		return commandLine;
	}

	private void addTestBoxArguments( @NotNull GeneralCommandLine commandLine ) {
		String scope = configuration.getTestScope();

		// Use console reporter for pretty output in the IDE console panel.
		// The structured test tree is populated from the JSON report file written
		// by --write-json-report. This gives us the best of both worlds:
		// - Human-readable console output with ANSI colors
		// - Structured JSON data for the test tree
		commandLine.addParameter( "--reporter=console" );
		commandLine.addParameter( "--write-json-report" );

		// Direct reports to our temp directory to avoid polluting the project
		if ( reportPath != null ) {
			commandLine.addParameter( "--reportpath=" + reportPath.toString() );
		}

		// Test scope: bundle vs directory
		if ( "BUNDLE".equals( scope ) ) {
			String bundlePath = configuration.getBundlePath();
			if ( bundlePath != null && !bundlePath.isBlank() ) {
				commandLine.addParameter( "--bundles=" + bundlePath );
			}
		} else if ( "DIRECTORY".equals( scope ) ) {
			String directory = configuration.getDirectory();
			if ( directory != null && !directory.isBlank() ) {
				commandLine.addParameter( "--directory=" + directory );
			}
		}
		// SCOPE_ALL: no --bundles or --directory, runner defaults apply

		// Filtering options
		// TestBox's canRunSpec() matches filter-specs against spec.id which is hash(specName).
		// We must hash the spec name to match TestBox's internal ID computation.
		String filterSpecs = configuration.getFilterSpecs();
		if ( filterSpecs != null && !filterSpecs.isBlank() ) {
			commandLine.addParameter( "--filter-specs=" + hashSpecFilter( filterSpecs ) );
			// Tell the ConsoleReporter to hide skipped specs/suites from output.
			// When running a single spec, the user wants to see only the targeted test,
			// not all the sibling specs that TestBox skipped because they didn't match.
			commandLine.addParameter( "--hide-skipped" );
		}

		String filterSuites = configuration.getFilterSuites();
		if ( filterSuites != null && !filterSuites.isBlank() ) {
			commandLine.addParameter( "--filter-suites=" + filterSuites );
			// Tell the ConsoleReporter to hide skipped specs/suites from output.
			// When running a single suite, the user wants to see only the targeted suite,
			// not all the sibling suites that TestBox skipped.
			commandLine.addParameter( "--hide-skipped" );
		}

		String labels = configuration.getLabels();
		if ( labels != null && !labels.isBlank() ) {
			commandLine.addParameter( "--labels=" + labels );
		}

		String excludes = configuration.getExcludes();
		if ( excludes != null && !excludes.isBlank() ) {
			commandLine.addParameter( "--excludes=" + excludes );
		}

		// Execution options
		if ( configuration.isVerbose() ) {
			commandLine.addParameter( "--verbose" );
		}

		if ( configuration.isEagerFailure() ) {
			commandLine.addParameter( "--eager-failure" );
		}
	}

	private void applyEnvironmentVariables( GeneralCommandLine commandLine ) {
		for ( Map.Entry<String, String> entry : parseEnvironmentVariables( configuration.getEnvironmentVariables() ).entrySet() ) {
			commandLine.withEnvironment( entry.getKey(), entry.getValue() );
		}
	}

	static Map<String, String> parseEnvironmentVariables( @Nullable String rawEnvironmentVariables ) {
		Map<String, String> variables = new LinkedHashMap<>();
		if ( rawEnvironmentVariables == null || rawEnvironmentVariables.isBlank() ) {
			return variables;
		}

		for ( String token : ParametersListUtil.parse( rawEnvironmentVariables ) ) {
			int separatorIndex = token.indexOf( '=' );
			if ( separatorIndex <= 0 ) {
				continue;
			}
			String key = token.substring( 0, separatorIndex ).trim();
			if ( key.isEmpty() ) {
				continue;
			}
			String value = token.substring( separatorIndex + 1 );
			variables.put( key, value );
		}
		return variables;
	}

	private String resolveJavaExecutable( BoxLangResolvedSettings settings ) {
		String javaExecutable = SystemInfo.isWindows ? "java.exe" : "java";

		if ( settings.javaHome != null && !settings.javaHome.isBlank() ) {
			Path javaPath = Path.of( settings.javaHome, "bin", javaExecutable );
			if ( javaPath.toFile().exists() ) {
				return javaPath.toString();
			}
		}

		String javaHomeEnv = System.getenv( "JAVA_HOME" );
		if ( javaHomeEnv != null && !javaHomeEnv.isBlank() ) {
			Path javaPath = Path.of( javaHomeEnv, "bin", javaExecutable );
			if ( javaPath.toFile().exists() ) {
				return javaPath.toString();
			}
		}

		String currentJavaHome = System.getProperty( "java.home" );
		if ( currentJavaHome != null && !currentJavaHome.isBlank() ) {
			Path javaPath = Path.of( currentJavaHome, "bin", javaExecutable );
			if ( javaPath.toFile().exists() ) {
				return javaPath.toString();
			}
		}

		return "java";
	}

	private List<String> buildJvmArgs( BoxLangResolvedSettings settings ) {
		List<String>	args			= new ArrayList<>();

		String			configJvmArgs	= configuration.getJvmArgs();
		if ( configJvmArgs != null && !configJvmArgs.isBlank() ) {
			args.addAll( ParametersListUtil.parse( configJvmArgs ) );
		}

		boolean hasHeapSize = args.stream().anyMatch( arg -> arg.startsWith( "-Xmx" ) );
		if ( !hasHeapSize ) {
			int heapSize = settings.lspMaxHeapSize > 0 ? settings.lspMaxHeapSize : 512;
			args.add( "-Xmx" + heapSize + "m" );
		}

		return args;
	}

	/**
	 * Hashes the filter-specs value to match TestBox's internal spec ID computation.
	 * TestBox computes spec.id as hash(specName) using MD5 (the default hash algorithm).
	 * The canRunSpec() method in BaseRunner.cfc only matches against spec.id, not spec.name,
	 * so we must pass the MD5 hash of the spec name for filtering to work.
	 *
	 * If the filter contains multiple comma-separated specs, each one is hashed individually.
	 */
	static String hashSpecFilter( @NotNull String filterSpecs ) {
		String[]		parts	= filterSpecs.split( "," );
		StringBuilder	result	= new StringBuilder();
		for ( int i = 0; i < parts.length; i++ ) {
			String part = parts[ i ].trim();
			if ( !part.isEmpty() ) {
				if ( result.length() > 0 ) {
					result.append( "," );
				}
				result.append( md5( part ) );
			}
		}
		return result.toString();
	}

	/**
	 * Computes the MD5 hash of a string, returning the lowercase hex digest.
	 * This matches the behavior of BoxLang's hash() function which uses
	 * Integer.toHexString() producing lowercase hex characters.
	 */
	private static String md5( @NotNull String input ) {
		try {
			java.security.MessageDigest	md		= java.security.MessageDigest.getInstance( "MD5" );
			byte[]						digest	= md.digest( input.getBytes( StandardCharsets.UTF_8 ) );
			StringBuilder				hex		= new StringBuilder();
			for ( byte b : digest ) {
				int v = b & 0xFF;
				if ( v < 16 ) {
					hex.append( '0' );
				}
				hex.append( Integer.toHexString( v ) );
			}
			return hex.toString();
		} catch ( java.security.NoSuchAlgorithmException e ) {
			// MD5 is always available in the JDK
			throw new RuntimeException( "MD5 algorithm not available", e );
		}
	}
}
