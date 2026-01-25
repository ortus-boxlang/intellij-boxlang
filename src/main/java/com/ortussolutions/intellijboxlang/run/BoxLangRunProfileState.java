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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.execution.ParametersListUtil;
import com.ortussolutions.intellijboxlang.runtime.BoxLangLspBootstrapService;
import com.ortussolutions.intellijboxlang.runtime.LspBootstrapResult;
import com.ortussolutions.intellijboxlang.settings.BoxLangResolvedSettings;
import com.ortussolutions.intellijboxlang.settings.BoxLangSettingsResolver;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Defines how to execute a BoxLang script.
 */
public class BoxLangRunProfileState extends CommandLineState {

    private final BoxLangRunConfiguration configuration;

    public BoxLangRunProfileState(BoxLangRunConfiguration configuration, ExecutionEnvironment environment) {
        super(environment);
        this.configuration = configuration;
    }

    @Override
    protected @NotNull ProcessHandler startProcess() throws ExecutionException {
        GeneralCommandLine commandLine = createCommandLine();
        OSProcessHandler processHandler = ProcessHandlerFactory.getInstance()
                .createColoredProcessHandler(commandLine);
        ProcessTerminatedListener.attach(processHandler);
        return processHandler;
    }

    @Override
    public @NotNull ExecutionResult execute(@NotNull Executor executor, @NotNull ProgramRunner<?> runner) throws ExecutionException {
        ProcessHandler processHandler = startProcess();
        ConsoleView console = createConsole(executor);
        if (console != null) {
            console.attachToProcess(processHandler);
        }
        return new DefaultExecutionResult(console, processHandler);
    }

    private GeneralCommandLine createCommandLine() throws ExecutionException {
        Project project = getEnvironment().getProject();
        BoxLangResolvedSettings settings = BoxLangSettingsResolver.resolve(project);
        
        LspBootstrapResult bootstrap;
        try {
            bootstrap = BoxLangLspBootstrapService.prepare(project);
        } catch (Exception e) {
            throw new ExecutionException("Failed to prepare BoxLang runtime: " + e.getMessage(), e);
        }

        GeneralCommandLine commandLine = new GeneralCommandLine(resolveJavaExecutable(settings));
        commandLine.withCharset(StandardCharsets.UTF_8);
        
        // Set up environment
        String boxLangHome = configuration.getBoxLangHome();
        if (boxLangHome == null || boxLangHome.isBlank()) {
            boxLangHome = bootstrap.lspBoxLangHome.toString();
        }
        commandLine.withEnvironment("BOXLANG_HOME", boxLangHome);
        commandLine.withEnvironment("CLASSPATH", bootstrap.boxLangJarPath.toString());
        
        if (settings.javaHome != null && !settings.javaHome.isBlank()) {
            commandLine.withEnvironment("JAVA_HOME", settings.javaHome);
        }

        // Set working directory
        String workingDir = configuration.getWorkingDirectory();
        if (workingDir != null && !workingDir.isBlank()) {
            commandLine.withWorkDirectory(workingDir);
        } else {
            String projectBasePath = project.getBasePath();
            if (projectBasePath != null) {
                commandLine.withWorkDirectory(projectBasePath);
            }
        }

        // Add JVM arguments
        commandLine.addParameters(buildJvmArgs(settings));

        // Main class
        commandLine.addParameter("ortus.boxlang.runtime.BoxRunner");

        // Script path
        commandLine.addParameter(configuration.getScriptPath());

        // Program arguments
        String programArgs = configuration.getProgramArguments();
        if (programArgs != null && !programArgs.isBlank()) {
            commandLine.addParameters(ParametersListUtil.parse(programArgs));
        }

        return commandLine;
    }

    private String resolveJavaExecutable(BoxLangResolvedSettings settings) {
        if (settings.javaHome == null || settings.javaHome.isBlank()) {
            return "java";
        }
        String javaExecutable = SystemInfo.isWindows ? "java.exe" : "java";
        return Path.of(settings.javaHome, "bin", javaExecutable).toString();
    }

    private List<String> buildJvmArgs(BoxLangResolvedSettings settings) {
        List<String> args = new ArrayList<>();
        
        // Add configuration-specific JVM args first
        String configJvmArgs = configuration.getJvmArgs();
        if (configJvmArgs != null && !configJvmArgs.isBlank()) {
            args.addAll(ParametersListUtil.parse(configJvmArgs));
        }
        
        // Add default heap size if not specified
        boolean hasHeapSize = args.stream().anyMatch(arg -> arg.startsWith("-Xmx"));
        if (!hasHeapSize) {
            int heapSize = settings.lspMaxHeapSize > 0 ? settings.lspMaxHeapSize : 512;
            args.add("-Xmx" + heapSize + "m");
        }
        
        return args;
    }
}
