package com.ortussolutions.intellijboxlang.run;

import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.CommandLineState;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessHandlerFactory;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.execution.ParametersListUtil;
import com.ortussolutions.intellijboxlang.file.BoxLangFileUtil;
import com.ortussolutions.intellijboxlang.runtime.BoxLangLspBootstrapService;
import com.ortussolutions.intellijboxlang.runtime.LspBootstrapResult;
import com.ortussolutions.intellijboxlang.settings.BoxLangResolvedSettings;
import com.ortussolutions.intellijboxlang.settings.BoxLangSettingsResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Defines how to execute a BoxLang script.
 */
public class BoxLangRunProfileState extends CommandLineState {

	private final BoxLangRunConfiguration configuration;

	public BoxLangRunProfileState( BoxLangRunConfiguration configuration, ExecutionEnvironment environment ) {
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
		ProcessHandler	processHandler	= startProcess();
		ConsoleView		console			= createConsole( executor );
		if ( console != null ) {
			console.attachToProcess( processHandler );
		}
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

		// Main class
		commandLine.addParameter( "ortus.boxlang.runtime.BoxRunner" );

		// Script path - use current file if configured
		String scriptPath = resolveScriptPath( project );
		if ( scriptPath == null || scriptPath.isBlank() ) {
			throw new ExecutionException( "No BoxLang script to run. Please open a BoxLang file or specify a script path." );
		}
		commandLine.addParameter( scriptPath );

		// Program arguments
		String programArgs = configuration.getProgramArguments();
		if ( programArgs != null && !programArgs.isBlank() ) {
			commandLine.addParameters( ParametersListUtil.parse( programArgs ) );
		}

		return commandLine;
	}

	private void applyEnvironmentVariables( GeneralCommandLine commandLine ) {
		for ( Map.Entry<String, String> entry : parseEnvironmentVariables( configuration.getEnvironmentVariables() ).entrySet() ) {
			String	key		= entry.getKey();
			String	value	= entry.getValue();
			commandLine.withEnvironment( key, value );
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

		// First, try the configured Java home from settings
		if ( settings.javaHome != null && !settings.javaHome.isBlank() ) {
			Path javaPath = Path.of( settings.javaHome, "bin", javaExecutable );
			if ( javaPath.toFile().exists() ) {
				return javaPath.toString();
			}
		}

		// Second, try JAVA_HOME environment variable
		String javaHomeEnv = System.getenv( "JAVA_HOME" );
		if ( javaHomeEnv != null && !javaHomeEnv.isBlank() ) {
			Path javaPath = Path.of( javaHomeEnv, "bin", javaExecutable );
			if ( javaPath.toFile().exists() ) {
				return javaPath.toString();
			}
		}

		// Third, try to find Java in the current process (IntelliJ's JDK)
		String currentJavaHome = System.getProperty( "java.home" );
		if ( currentJavaHome != null && !currentJavaHome.isBlank() ) {
			Path javaPath = Path.of( currentJavaHome, "bin", javaExecutable );
			if ( javaPath.toFile().exists() ) {
				return javaPath.toString();
			}
		}

		// Last resort - use "java" from PATH and hope it works
		return "java";
	}

	private List<String> buildJvmArgs( BoxLangResolvedSettings settings ) {
		List<String>	args			= new ArrayList<>();

		// Add configuration-specific JVM args first
		String			configJvmArgs	= configuration.getJvmArgs();
		if ( configJvmArgs != null && !configJvmArgs.isBlank() ) {
			args.addAll( ParametersListUtil.parse( configJvmArgs ) );
		}

		// Add default heap size if not specified
		boolean hasHeapSize = args.stream().anyMatch( arg -> arg.startsWith( "-Xmx" ) );
		if ( !hasHeapSize ) {
			int heapSize = settings.lspMaxHeapSize > 0 ? settings.lspMaxHeapSize : 512;
			args.add( "-Xmx" + heapSize + "m" );
		}

		return args;
	}

	private String resolveScriptPath( Project project ) {
		// If not using current file, return the configured script path
		if ( !configuration.isUseCurrentFile() ) {
			return configuration.getScriptPath();
		}

		// Get the currently open file in the editor
		VirtualFile[] selectedFiles = FileEditorManager.getInstance( project ).getSelectedFiles();
		if ( selectedFiles.length == 0 ) {
			return null;
		}

		VirtualFile currentFile = selectedFiles[ 0 ];

		// Verify it's a BoxLang file
		if ( !BoxLangFileUtil.isBoxLangFile( currentFile ) ) {
			return null;
		}

		return currentFile.getPath();
	}
}
