package com.ortussolutions.intellijboxlang.debug;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.execution.ParametersListUtil;
import com.ortussolutions.intellijboxlang.runtime.BoxLangLspBootstrapService;
import com.ortussolutions.intellijboxlang.runtime.LspBootstrapResult;
import com.ortussolutions.intellijboxlang.settings.BoxLangResolvedSettings;
import com.ortussolutions.intellijboxlang.settings.BoxLangSettingsResolver;
import org.eclipse.lsp4j.debug.*;
import org.eclipse.lsp4j.debug.launch.DSPLauncher;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * Service that manages the DAP (Debug Adapter Protocol) connection to the BoxLang debugger.
 * This is NOT a project-level service - instances are created per debug session.
 * 
 * Follows a similar pattern to BoxLangLspClientService but is designed for debug sessions
 * which have a defined lifecycle (start -> debug -> stop).
 */
public class BoxLangDapService implements Disposable {
    private static final Logger LOG = Logger.getInstance(BoxLangDapService.class);
    private static final int CONNECT_TIMEOUT_MS = 10000;
    private static final int INITIALIZE_TIMEOUT_MS = 20000;

    private final Project project;
    private final List<DapEventListener> eventListeners = new CopyOnWriteArrayList<>();

    private Process serverProcess;
    private Socket socket;
    private IDebugProtocolServer debugServer;
    private Capabilities serverCapabilities;
    private volatile boolean initialized = false;
    private volatile boolean terminated = false;

    public BoxLangDapService(@NotNull Project project) {
        this.project = project;
    }

    /**
     * Adds a listener for DAP events (stopped, output, terminated, etc.)
     */
    public void addEventListener(@NotNull DapEventListener listener) {
        eventListeners.add(listener);
    }

    /**
     * Removes a DAP event listener.
     */
    public void removeEventListener(@NotNull DapEventListener listener) {
        eventListeners.remove(listener);
    }

    /**
     * Returns true if the DAP server is connected and initialized.
     */
    public boolean isConnected() {
        return initialized && !terminated && debugServer != null;
    }

    /**
     * Returns the DAP server proxy for sending requests.
     */
    public @Nullable IDebugProtocolServer getServer() {
        return debugServer;
    }

    /**
     * Returns the server capabilities received during initialization.
     */
    public @Nullable Capabilities getServerCapabilities() {
        return serverCapabilities;
    }

    /**
     * Starts the DAP server and establishes connection.
     * This should be called at the beginning of a debug session.
     */
    public void start() throws Exception {
        if (initialized) {
            LOG.warn("DAP service already started");
            return;
        }

        LOG.info("Starting BoxLang DAP server");
        
        BoxLangResolvedSettings settings = BoxLangSettingsResolver.resolve(project);
        LspBootstrapResult bootstrap = BoxLangLspBootstrapService.prepare(project);
        int port = allocatePort();

        GeneralCommandLine commandLine = new GeneralCommandLine(resolveJavaExecutable(settings));
        commandLine.withCharset(StandardCharsets.UTF_8);
        commandLine.withEnvironment("BOXLANG_HOME", bootstrap.lspBoxLangHome.toString());
        commandLine.withEnvironment("CLASSPATH", bootstrap.boxLangJarPath.toString());
        if (settings.javaHome != null && !settings.javaHome.isBlank()) {
            commandLine.withEnvironment("JAVA_HOME", settings.javaHome);
        }

        commandLine.addParameters(buildJvmArgs(settings));
        commandLine.addParameter("ortus.boxlang.runtime.BoxRunner");
        // TODO: Update this when the DAP module is available
        // For now, we'll use a placeholder - this will need to be updated
        // to match the actual BoxLang DAP server module name
        commandLine.addParameter("module:bx-debugger");
        commandLine.addParameters("--port", String.valueOf(port));

        LOG.info("Starting DAP server process: " + commandLine.getCommandLineString());
        serverProcess = commandLine.createProcess();
        
        socket = connectWithRetries(port);
        LOG.info("Connected to DAP server on port " + port);

        BoxLangDapClient client = new BoxLangDapClient(this);
        var launcher = DSPLauncher.createClientLauncher(client, socket.getInputStream(), socket.getOutputStream());
        debugServer = launcher.getRemoteProxy();
        launcher.startListening();

        // Perform DAP initialization handshake
        InitializeRequestArguments initArgs = new InitializeRequestArguments();
        initArgs.setClientID("intellij-boxlang");
        initArgs.setClientName("IntelliJ BoxLang Plugin");
        initArgs.setAdapterID("boxlang");
        initArgs.setPathFormat("path");
        initArgs.setLinesStartAt1(true);
        initArgs.setColumnsStartAt1(true);
        initArgs.setSupportsVariableType(true);
        initArgs.setSupportsVariablePaging(false);
        initArgs.setSupportsRunInTerminalRequest(false);
        initArgs.setSupportsMemoryReferences(false);
        initArgs.setSupportsProgressReporting(false);
        initArgs.setSupportsInvalidatedEvent(true);

        LOG.info("Sending DAP initialize request");
        serverCapabilities = debugServer.initialize(initArgs)
                .get(INITIALIZE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        LOG.info("DAP initialize response received");

        initialized = true;
        LOG.info("DAP server initialized successfully");
    }

    /**
     * Sends a launch request to start debugging a script.
     */
    public CompletableFuture<Void> launch(@NotNull String scriptPath, @Nullable String workingDirectory,
                                          @Nullable List<String> args, boolean stopOnEntry) {
        if (!isConnected()) {
            return CompletableFuture.failedFuture(new IllegalStateException("DAP server not connected"));
        }

        // DAP launch request uses a Map<String, Object> for adapter-specific arguments
        Map<String, Object> launchArgs = new HashMap<>();
        launchArgs.put("program", scriptPath);
        launchArgs.put("stopOnEntry", stopOnEntry);
        
        if (workingDirectory != null && !workingDirectory.isBlank()) {
            launchArgs.put("cwd", workingDirectory);
        }
        
        if (args != null && !args.isEmpty()) {
            launchArgs.put("args", args);
        }
        
        // BoxLang-specific: indicate this is not an attach request
        launchArgs.put("noDebug", false);
        
        LOG.info("Sending DAP launch request for: " + scriptPath);
        return debugServer.launch(launchArgs).thenApply(response -> null);
    }

    /**
     * Sends a configurationDone request to indicate client is done with initial configuration.
     */
    public CompletableFuture<Void> configurationDone() {
        if (!isConnected()) {
            return CompletableFuture.failedFuture(new IllegalStateException("DAP server not connected"));
        }

        ConfigurationDoneArguments args = new ConfigurationDoneArguments();
        return debugServer.configurationDone(args).thenApply(response -> null);
    }

    /**
     * Sets breakpoints for a source file.
     */
    public CompletableFuture<SetBreakpointsResponse> setBreakpoints(@NotNull String sourcePath,
                                                                      @NotNull List<SourceBreakpoint> breakpoints) {
        if (!isConnected()) {
            return CompletableFuture.failedFuture(new IllegalStateException("DAP server not connected"));
        }

        SetBreakpointsArguments args = new SetBreakpointsArguments();
        Source source = new Source();
        source.setPath(sourcePath);
        source.setName(Path.of(sourcePath).getFileName().toString());
        args.setSource(source);
        args.setBreakpoints(breakpoints.toArray(new SourceBreakpoint[0]));

        LOG.debug("Setting breakpoints for: " + sourcePath + " (" + breakpoints.size() + " breakpoints)");
        return debugServer.setBreakpoints(args);
    }

    /**
     * Requests the current threads.
     */
    public CompletableFuture<ThreadsResponse> threads() {
        if (!isConnected()) {
            return CompletableFuture.failedFuture(new IllegalStateException("DAP server not connected"));
        }
        return debugServer.threads();
    }

    /**
     * Requests the stack trace for a thread.
     */
    public CompletableFuture<StackTraceResponse> stackTrace(int threadId) {
        if (!isConnected()) {
            return CompletableFuture.failedFuture(new IllegalStateException("DAP server not connected"));
        }

        StackTraceArguments args = new StackTraceArguments();
        args.setThreadId(threadId);
        return debugServer.stackTrace(args);
    }

    /**
     * Requests the scopes for a stack frame.
     */
    public CompletableFuture<ScopesResponse> scopes(int frameId) {
        if (!isConnected()) {
            return CompletableFuture.failedFuture(new IllegalStateException("DAP server not connected"));
        }

        ScopesArguments args = new ScopesArguments();
        args.setFrameId(frameId);
        return debugServer.scopes(args);
    }

    /**
     * Requests variables for a scope or variable reference.
     */
    public CompletableFuture<VariablesResponse> variables(int variablesReference) {
        if (!isConnected()) {
            return CompletableFuture.failedFuture(new IllegalStateException("DAP server not connected"));
        }

        VariablesArguments args = new VariablesArguments();
        args.setVariablesReference(variablesReference);
        return debugServer.variables(args);
    }

    /**
     * Continues execution of a thread.
     */
    public CompletableFuture<ContinueResponse> continueExecution(int threadId) {
        if (!isConnected()) {
            return CompletableFuture.failedFuture(new IllegalStateException("DAP server not connected"));
        }

        ContinueArguments args = new ContinueArguments();
        args.setThreadId(threadId);
        return debugServer.continue_(args);
    }

    /**
     * Steps over (next) in a thread.
     */
    public CompletableFuture<Void> stepOver(int threadId) {
        if (!isConnected()) {
            return CompletableFuture.failedFuture(new IllegalStateException("DAP server not connected"));
        }

        NextArguments args = new NextArguments();
        args.setThreadId(threadId);
        return debugServer.next(args).thenApply(response -> null);
    }

    /**
     * Steps into in a thread.
     */
    public CompletableFuture<Void> stepInto(int threadId) {
        if (!isConnected()) {
            return CompletableFuture.failedFuture(new IllegalStateException("DAP server not connected"));
        }

        StepInArguments args = new StepInArguments();
        args.setThreadId(threadId);
        return debugServer.stepIn(args).thenApply(response -> null);
    }

    /**
     * Steps out of the current function in a thread.
     */
    public CompletableFuture<Void> stepOut(int threadId) {
        if (!isConnected()) {
            return CompletableFuture.failedFuture(new IllegalStateException("DAP server not connected"));
        }

        StepOutArguments args = new StepOutArguments();
        args.setThreadId(threadId);
        return debugServer.stepOut(args).thenApply(response -> null);
    }

    /**
     * Pauses execution of a thread.
     */
    public CompletableFuture<Void> pause(int threadId) {
        if (!isConnected()) {
            return CompletableFuture.failedFuture(new IllegalStateException("DAP server not connected"));
        }

        PauseArguments args = new PauseArguments();
        args.setThreadId(threadId);
        return debugServer.pause(args).thenApply(response -> null);
    }

    /**
     * Evaluates an expression in the context of a stack frame.
     */
    public CompletableFuture<EvaluateResponse> evaluate(String expression, int frameId, String context) {
        if (!isConnected()) {
            return CompletableFuture.failedFuture(new IllegalStateException("DAP server not connected"));
        }

        EvaluateArguments args = new EvaluateArguments();
        args.setExpression(expression);
        args.setFrameId(frameId);
        args.setContext(context); // "watch", "repl", "hover", etc.
        return debugServer.evaluate(args);
    }

    /**
     * Disconnects from the DAP server and terminates the debug session.
     */
    public CompletableFuture<Void> disconnect(boolean terminateDebuggee) {
        if (debugServer == null) {
            return CompletableFuture.completedFuture(null);
        }

        DisconnectArguments args = new DisconnectArguments();
        args.setTerminateDebuggee(terminateDebuggee);
        return debugServer.disconnect(args).thenApply(response -> null);
    }

    @Override
    public void dispose() {
        LOG.info("Disposing BoxLang DAP service");
        terminated = true;
        
        try {
            if (debugServer != null) {
                disconnect(true).get(5, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            LOG.debug("Error during DAP disconnect", e);
        }

        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            LOG.debug("Error closing DAP socket", e);
        }

        if (serverProcess != null && serverProcess.isAlive()) {
            serverProcess.destroyForcibly();
        }

        eventListeners.clear();
        debugServer = null;
        socket = null;
        serverProcess = null;
        initialized = false;
    }

    // Event handlers called by BoxLangDapClient

    void handleStopped(StoppedEventArguments args) {
        for (DapEventListener listener : eventListeners) {
            try {
                listener.onStopped(args);
            } catch (Exception e) {
                LOG.warn("Error in DAP event listener", e);
            }
        }
    }

    void handleContinued(ContinuedEventArguments args) {
        for (DapEventListener listener : eventListeners) {
            try {
                listener.onContinued(args);
            } catch (Exception e) {
                LOG.warn("Error in DAP event listener", e);
            }
        }
    }

    void handleExited(ExitedEventArguments args) {
        for (DapEventListener listener : eventListeners) {
            try {
                listener.onExited(args);
            } catch (Exception e) {
                LOG.warn("Error in DAP event listener", e);
            }
        }
    }

    void handleTerminated(TerminatedEventArguments args) {
        terminated = true;
        for (DapEventListener listener : eventListeners) {
            try {
                listener.onTerminated(args);
            } catch (Exception e) {
                LOG.warn("Error in DAP event listener", e);
            }
        }
    }

    void handleThread(ThreadEventArguments args) {
        for (DapEventListener listener : eventListeners) {
            try {
                listener.onThread(args);
            } catch (Exception e) {
                LOG.warn("Error in DAP event listener", e);
            }
        }
    }

    void handleOutput(OutputEventArguments args) {
        for (DapEventListener listener : eventListeners) {
            try {
                listener.onOutput(args);
            } catch (Exception e) {
                LOG.warn("Error in DAP event listener", e);
            }
        }
    }

    void handleBreakpoint(BreakpointEventArguments args) {
        for (DapEventListener listener : eventListeners) {
            try {
                listener.onBreakpoint(args);
            } catch (Exception e) {
                LOG.warn("Error in DAP event listener", e);
            }
        }
    }

    void handleCapabilities(CapabilitiesEventArguments args) {
        if (args.getCapabilities() != null) {
            this.serverCapabilities = args.getCapabilities();
        }
    }

    // Private helper methods

    private int allocatePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private Socket connectWithRetries(int port) throws IOException {
        IOException lastError = null;
        long deadline = System.currentTimeMillis() + CONNECT_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            try {
                Socket socket = new Socket();
                socket.connect(new InetSocketAddress("127.0.0.1", port), CONNECT_TIMEOUT_MS);
                return socket;
            } catch (IOException e) {
                lastError = e;
                try {
                    java.lang.Thread.sleep(200);
                } catch (InterruptedException interrupted) {
                    java.lang.Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        if (lastError != null) {
            throw lastError;
        }
        throw new IOException("Unable to connect to BoxLang DAP server");
    }

    private String resolveJavaExecutable(BoxLangResolvedSettings settings) {
        String javaExecutable = SystemInfo.isWindows ? "java.exe" : "java";
        
        // First, try the configured Java home from settings
        if (settings.javaHome != null && !settings.javaHome.isBlank()) {
            Path javaPath = Path.of(settings.javaHome, "bin", javaExecutable);
            if (javaPath.toFile().exists()) {
                return javaPath.toString();
            }
        }
        
        // Second, try JAVA_HOME environment variable
        String javaHomeEnv = System.getenv("JAVA_HOME");
        if (javaHomeEnv != null && !javaHomeEnv.isBlank()) {
            Path javaPath = Path.of(javaHomeEnv, "bin", javaExecutable);
            if (javaPath.toFile().exists()) {
                return javaPath.toString();
            }
        }
        
        // Third, try to find Java in the current process (IntelliJ's JDK)
        String currentJavaHome = System.getProperty("java.home");
        if (currentJavaHome != null && !currentJavaHome.isBlank()) {
            Path javaPath = Path.of(currentJavaHome, "bin", javaExecutable);
            if (javaPath.toFile().exists()) {
                return javaPath.toString();
            }
        }
        
        // Last resort - use "java" from PATH
        return "java";
    }

    private List<String> buildJvmArgs(BoxLangResolvedSettings settings) {
        List<String> args = new ArrayList<>();
        int heapSize = settings.lspMaxHeapSize > 0 ? settings.lspMaxHeapSize : 512;
        args.add("-Xmx" + heapSize + "m");
        if (settings.lspJvmArgs != null && !settings.lspJvmArgs.isBlank()) {
            args.addAll(ParametersListUtil.parse(settings.lspJvmArgs));
        }
        return args;
    }

    /**
     * Interface for listening to DAP events.
     */
    public interface DapEventListener {
        default void onStopped(StoppedEventArguments args) {}
        default void onContinued(ContinuedEventArguments args) {}
        default void onExited(ExitedEventArguments args) {}
        default void onTerminated(TerminatedEventArguments args) {}
        default void onThread(ThreadEventArguments args) {}
        default void onOutput(OutputEventArguments args) {}
        default void onBreakpoint(BreakpointEventArguments args) {}
    }
}
