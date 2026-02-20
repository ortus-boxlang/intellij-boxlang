package com.ortussolutions.intellijboxlang.debug;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.GenericProgramRunner;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.execution.ParametersListUtil;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugProcessStarter;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.ortussolutions.intellijboxlang.run.BoxLangRunConfiguration;
import com.ortussolutions.intellijboxlang.run.BoxLangRunProfileState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Program runner that handles debug execution of BoxLang scripts.
 * Starts the DAP server and creates a debug session.
 */
public class BoxLangDebugRunner extends GenericProgramRunner<RunnerSettings> {
    private static final Logger LOG = Logger.getInstance(BoxLangDebugRunner.class);
    private static final String RUNNER_ID = "BoxLangDebugRunner";

    @Override
    public @NotNull String getRunnerId() {
        return RUNNER_ID;
    }

    @Override
    public boolean canRun(@NotNull String executorId, @NotNull RunProfile profile) {
        return DefaultDebugExecutor.EXECUTOR_ID.equals(executorId) 
                && profile instanceof BoxLangRunConfiguration;
    }

    @Override
    protected @Nullable RunContentDescriptor doExecute(@NotNull RunProfileState state,
                                                         @NotNull ExecutionEnvironment environment) throws ExecutionException {
        if (!(state instanceof BoxLangRunProfileState)) {
            throw new ExecutionException("Invalid run profile state");
        }

        BoxLangRunProfileState profileState = (BoxLangRunProfileState) state;
        BoxLangRunConfiguration configuration = (BoxLangRunConfiguration) environment.getRunProfile();
        Project project = environment.getProject();

        // Resolve the script path
        String scriptPath = resolveScriptPath(configuration, project);
        if (scriptPath == null || scriptPath.isBlank()) {
            throw new ExecutionException("No BoxLang script to debug. Please open a BoxLang file or specify a script path.");
        }

        // Get program arguments
        String programArgsString = configuration.getProgramArguments();
        List<String> programArgs = null;
        if (programArgsString != null && !programArgsString.isBlank()) {
            programArgs = ParametersListUtil.parse(programArgsString);
        }

        // Get working directory
        String workingDirectory = configuration.getWorkingDirectory();
        if (workingDirectory == null || workingDirectory.isBlank()) {
            workingDirectory = project.getBasePath();
        }

        final String finalScriptPath = scriptPath;
        final String finalWorkingDirectory = workingDirectory;
        final List<String> finalProgramArgs = programArgs;

        try {
            // Create and start the debug session
            XDebugSession debugSession = XDebuggerManager.getInstance(project).startSession(
                    environment,
                    new XDebugProcessStarter() {
                        @Override
                        public @NotNull XDebugProcess start(@NotNull XDebugSession session) throws ExecutionException {
                            return createDebugProcess(session, profileState, environment, 
                                    finalScriptPath, finalWorkingDirectory, finalProgramArgs);
                        }
                    }
            );

            return debugSession.getRunContentDescriptor();
        } catch (Exception e) {
            throw new ExecutionException("Failed to start debug session: " + e.getMessage(), e);
        }
    }

    private BoxLangDebugProcess createDebugProcess(@NotNull XDebugSession session,
                                                    @NotNull BoxLangRunProfileState profileState,
                                                    @NotNull ExecutionEnvironment environment,
                                                    @NotNull String scriptPath,
                                                    @Nullable String workingDirectory,
                                                    @Nullable List<String> programArgs) throws ExecutionException {
        Project project = environment.getProject();
        
        // Create DAP service for this debug session
        BoxLangDapService dapService = new BoxLangDapService(project);
        
        try {
            // Start the DAP server
            LOG.info("Starting DAP server for debugging: " + scriptPath);
            dapService.start();
            
            // Execute the run profile to get the console
            ExecutionResult executionResult = null;
            try {
                executionResult = profileState.execute(environment.getExecutor(), this);
            } catch (Exception e) {
                LOG.warn("Could not create execution result for console", e);
            }
            
            // Create the debug process
            BoxLangDebugProcess debugProcess = new BoxLangDebugProcess(session, dapService, executionResult);
            
            // Launch the script in debug mode
            dapService.launch(scriptPath, workingDirectory, programArgs, false)
                .thenRun(() -> {
                    LOG.info("Launch request sent successfully");
                    debugProcess.sessionInitialized();
                })
                .exceptionally(ex -> {
                    LOG.error("Failed to launch debug session", ex);
                    return null;
                });
            
            return debugProcess;
            
        } catch (Exception e) {
            dapService.dispose();
            throw new ExecutionException("Failed to start DAP server: " + e.getMessage(), e);
        }
    }

    private String resolveScriptPath(BoxLangRunConfiguration configuration, Project project) {
        // If not using current file, return the configured script path
        if (!configuration.isUseCurrentFile()) {
            return configuration.getScriptPath();
        }
        
        // Get the currently open file in the editor
        VirtualFile[] selectedFiles = FileEditorManager.getInstance(project).getSelectedFiles();
        if (selectedFiles.length == 0) {
            return null;
        }
        
        VirtualFile currentFile = selectedFiles[0];
        
        // Verify it's a BoxLang file
        String ext = currentFile.getExtension();
        if (ext == null || (!ext.equalsIgnoreCase("bx") 
                && !ext.equalsIgnoreCase("bxm") 
                && !ext.equalsIgnoreCase("bxs"))) {
            return null;
        }
        
        return currentFile.getPath();
    }
}
