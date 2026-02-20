package com.ortussolutions.intellijboxlang.debug;

import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.XBreakpointHandler;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.frame.XSuspendContext;
import org.eclipse.lsp4j.debug.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The main debug process for BoxLang debugging.
 * Manages the connection to the DAP server and coordinates debugging operations.
 */
public class BoxLangDebugProcess extends XDebugProcess implements BoxLangDapService.DapEventListener {
    private static final Logger LOG = Logger.getInstance(BoxLangDebugProcess.class);

    private final BoxLangDapService dapService;
    private final BoxLangBreakpointHandler breakpointHandler;
    private final BoxLangDebuggerEditorsProvider editorsProvider;
    private final BoxLangDapProcessHandler processHandler;
    
    // Launch parameters - stored so we can send launch request at the right time
    private final String scriptPath;
    private final String workingDirectory;
    private final List<String> programArgs;
    
    // Track the current thread ID for stepping operations
    private volatile int activeThreadId = -1;
    
    // Track the top frame ID of the current suspension, used for REPL evaluation
    private volatile int activeFrameId = -1;
    
    // Track whether we've received the 'initialized' event from DAP
    private volatile boolean dapInitialized = false;
    
    // Track whether IntelliJ has finished registering breakpoints (sessionInitialized called)
    private volatile boolean sessionReady = false;
    
    // Track whether we've already completed configuration (prevent double calls)
    private volatile boolean configurationCompleted = false;

    public BoxLangDebugProcess(@NotNull XDebugSession session,
                                @NotNull BoxLangDapService dapService,
                                @NotNull String scriptPath,
                                @Nullable String workingDirectory,
                                @Nullable List<String> programArgs) {
        super(session);
        this.dapService = dapService;
        this.scriptPath = scriptPath;
        this.workingDirectory = workingDirectory;
        this.programArgs = programArgs;
        this.breakpointHandler = new BoxLangBreakpointHandler(this);
        this.editorsProvider = new BoxLangDebuggerEditorsProvider();
        
        // Create a custom ProcessHandler that stays alive until DAP terminated/exited events.
        // This is the key fix: previously we used the ProcessHandler from profileState.execute()
        // which would terminate when the normal (non-debugged) BoxLang process finished,
        // causing IntelliJ to tear down the debug session prematurely.
        this.processHandler = new BoxLangDapProcessHandler();
        this.processHandler.startNotified();
        
        // Register for DAP events
        dapService.addEventListener(this);
        
        // Check if the 'initialized' event was already received before we registered
        // This can happen because dapService.start() sends initialize and may receive
        // the initialized event before BoxLangDebugProcess is created
        if (dapService.isConfigurationReady()) {
            LOG.info("DAP server was already initialized before we registered - setting dapInitialized flag");
            dapInitialized = true;
        }
    }

    @Override
    public @NotNull XDebuggerEditorsProvider getEditorsProvider() {
        return editorsProvider;
    }

    @Override
    public XBreakpointHandler<?>[] getBreakpointHandlers() {
        return new XBreakpointHandler[]{breakpointHandler};
    }

    @Override
    public @Nullable ExecutionConsole createConsole() {
        // Create a debug console with REPL support for expression evaluation
        BoxLangDebugConsole debugConsole = new BoxLangDebugConsole(
                getSession().getProject(), this);
        debugConsole.attachToProcess(processHandler);
        return debugConsole.getConsoleView();
    }

    @Override
    public @Nullable ProcessHandler doGetProcessHandler() {
        return processHandler;
    }

    /**
     * Returns the DAP service for this debug session.
     */
    public BoxLangDapService getDapService() {
        return dapService;
    }

    /**
     * Called when the debug session is fully started and IntelliJ has registered all breakpoints.
     * This is the signal that we can complete the DAP configuration sequence.
     * 
     * IMPORTANT: This method is called automatically by IntelliJ AFTER all initial breakpoints
     * have been registered via registerBreakpoint() on our XBreakpointHandler. Do NOT call this
     * method manually from outside.
     */
    @Override
    public void sessionInitialized() {
        LOG.info("Debug session initialized - IntelliJ has registered all breakpoints");
        LOG.info("  dapInitialized=" + dapInitialized + ", sessionReady=" + sessionReady + ", configurationCompleted=" + configurationCompleted);
        sessionReady = true;
        
        // If DAP is already initialized, complete the configuration now
        // Otherwise, onInitialized will do it when the DAP server is ready
        if (dapInitialized) {
            LOG.info("DAP already initialized, calling completeConfiguration()");
            completeConfiguration();
        } else {
            LOG.info("Waiting for DAP 'initialized' event before completing configuration");
        }
    }
    
    /**
     * Complete the DAP configuration by syncing breakpoints, launching, and sending configurationDone.
     * This should only be called when BOTH the DAP server is initialized AND IntelliJ has
     * finished registering breakpoints.
     * 
     * The correct DAP message order is:
     * 1. setBreakpoints (for all files)
     * 2. launch
     * 3. configurationDone
     */
    private synchronized void completeConfiguration() {
        // Guard against being called multiple times
        if (configurationCompleted) {
            LOG.debug("Configuration already completed, skipping duplicate call");
            return;
        }
        configurationCompleted = true;
        
        LOG.info("Completing DAP configuration - correct order: breakpoints -> launch -> configurationDone");
        LOG.info("  scriptPath=" + scriptPath + ", workingDirectory=" + workingDirectory);
        
        // Step 1: Sync all breakpoints to the DAP server FIRST
        breakpointHandler.syncAllBreakpoints()
            .thenCompose(v -> {
                // Step 2: Send launch request AFTER breakpoints are set
                LOG.info("All breakpoints synced, sending launch request for: " + scriptPath);
                return dapService.launch(scriptPath, workingDirectory, programArgs, false);
            })
            .thenCompose(v -> {
                // Step 3: Send configurationDone AFTER launch
                LOG.info("Launch request completed, sending configurationDone");
                return dapService.configurationDone();
            })
            .thenRun(() -> {
                LOG.info("configurationDone sent successfully - debug session fully configured");
                // Mark configuration as complete so future breakpoint changes are sent immediately
                breakpointHandler.markConfigurationComplete();
            })
            .exceptionally(ex -> {
                LOG.error("Failed to complete DAP configuration", ex);
                return null;
            });
    }

    // Stepping operations

    @Override
    public void startStepOver(@Nullable XSuspendContext context) {
        int threadId = getThreadId(context);
        if (threadId != -1) {
            dapService.stepOver(threadId)
                .exceptionally(ex -> {
                    LOG.warn("Step over failed", ex);
                    return null;
                });
        }
    }

    @Override
    public void startStepInto(@Nullable XSuspendContext context) {
        int threadId = getThreadId(context);
        if (threadId != -1) {
            dapService.stepInto(threadId)
                .exceptionally(ex -> {
                    LOG.warn("Step into failed", ex);
                    return null;
                });
        }
    }

    @Override
    public void startStepOut(@Nullable XSuspendContext context) {
        int threadId = getThreadId(context);
        if (threadId != -1) {
            dapService.stepOut(threadId)
                .exceptionally(ex -> {
                    LOG.warn("Step out failed", ex);
                    return null;
                });
        }
    }

    @Override
    public void resume(@Nullable XSuspendContext context) {
        int threadId = getThreadId(context);
        if (threadId != -1) {
            dapService.continueExecution(threadId)
                .exceptionally(ex -> {
                    LOG.warn("Resume failed", ex);
                    return null;
                });
        }
    }

    @Override
    public void startPausing() {
        if (activeThreadId != -1) {
            dapService.pause(activeThreadId)
                .exceptionally(ex -> {
                    LOG.warn("Pause failed", ex);
                    return null;
                });
        }
    }

    @Override
    public void stop() {
        LOG.info("Stopping debug process");
        breakpointHandler.clear();
        dapService.removeEventListener(this);
        dapService.dispose();
    }

    @Override
    public void runToPosition(@NotNull XSourcePosition position, @Nullable XSuspendContext context) {
        // TODO: Implement run to cursor using temporary breakpoint
        LOG.info("Run to position not yet implemented");
    }

    // DAP Event Handlers

    @Override
    public void onInitialized() {
        LOG.info("DAP server initialized event received");
        LOG.info("  dapInitialized=" + dapInitialized + ", sessionReady=" + sessionReady + ", configurationCompleted=" + configurationCompleted);
        dapInitialized = true;
        
        // If IntelliJ has already registered breakpoints (sessionInitialized called),
        // complete the configuration now. Otherwise, sessionInitialized will do it.
        if (sessionReady) {
            LOG.info("IntelliJ session already ready, calling completeConfiguration()");
            completeConfiguration();
        } else {
            LOG.info("Waiting for IntelliJ to finish registering breakpoints before completing configuration");
        }
    }

    @Override
    public void onStopped(StoppedEventArguments args) {
        LOG.info("Execution stopped: " + args.getReason() + " on thread " + args.getThreadId());
        
        int threadId = args.getThreadId() != null ? args.getThreadId() : 1;
        activeThreadId = threadId;
        
        // Fetch stack trace from DAP server before creating the suspend context.
        // This ensures IntelliJ has real stack frames to display in the Frames panel
        // and can highlight the correct source line.
        dapService.stackTrace(threadId)
            .thenAccept(stackTraceResponse -> {
                org.eclipse.lsp4j.debug.StackFrame[] frames = stackTraceResponse.getStackFrames();
                if (frames == null) {
                    frames = new org.eclipse.lsp4j.debug.StackFrame[0];
                }
                
                LOG.info("Stack trace received: " + frames.length + " frames for thread " + threadId);
                for (org.eclipse.lsp4j.debug.StackFrame frame : frames) {
                    LOG.debug("  Frame: " + frame.getName() + " at " + 
                        (frame.getSource() != null ? frame.getSource().getPath() : "<no source>") + 
                        ":" + frame.getLine());
                }
                
                // Track the top frame ID for REPL evaluation
                if (frames.length > 0) {
                    activeFrameId = frames[0].getId();
                }
                
                // Build a thread name for display
                String threadName = "Thread " + threadId;
                
                BoxLangSuspendContext suspendContext = new BoxLangSuspendContext(
                    this, args, threadName, frames);
                
                // positionReached must be called on the EDT for IntelliJ to properly
                // activate the debug toolbar (step over/into/out, resume buttons)
                ApplicationManager.getApplication().invokeLater(() -> {
                    getSession().positionReached(suspendContext);
                });
            })
            .exceptionally(ex -> {
                LOG.warn("Failed to fetch stack trace for thread " + threadId + ", creating empty suspend context", ex);
                // Fall back to an empty stack trace so the session still pauses
                BoxLangSuspendContext suspendContext = new BoxLangSuspendContext(
                    this, args, "Thread " + threadId, new org.eclipse.lsp4j.debug.StackFrame[0]);
                ApplicationManager.getApplication().invokeLater(() -> {
                    getSession().positionReached(suspendContext);
                });
                return null;
            });
    }

    @Override
    public void onContinued(ContinuedEventArguments args) {
        LOG.debug("Execution continued on thread " + args.getThreadId());
    }

    @Override
    public void onExited(ExitedEventArguments args) {
        LOG.info("Process exited with code: " + args.getExitCode());
        processHandler.onDapExited(args.getExitCode());
    }

    @Override
    public void onTerminated(TerminatedEventArguments args) {
        LOG.info("Debug session terminated");
        processHandler.onDapTerminated();
        getSession().stop();
    }

    @Override
    public void onThread(ThreadEventArguments args) {
        LOG.debug("Thread event: " + args.getReason() + " for thread " + args.getThreadId());
    }

    @Override
    public void onOutput(OutputEventArguments args) {
        String output = args.getOutput();
        String category = args.getCategory();
        LOG.debug("Output [" + category + "]: " + output);
        
        // Forward output to the console via the ProcessHandler
        processHandler.onDapOutput(output, category);
    }

    @Override
    public void onBreakpoint(BreakpointEventArguments args) {
        LOG.debug("Breakpoint event: " + args.getReason());
    }

    // Helper methods

    /**
     * Returns the frame ID of the top frame in the current suspension.
     * Used by the debug console for REPL evaluation context.
     */
    public int getActiveFrameId() {
        return activeFrameId;
    }

    private int getThreadId(@Nullable XSuspendContext context) {
        if (context instanceof BoxLangSuspendContext) {
            return ((BoxLangSuspendContext) context).getThreadId();
        }
        return activeThreadId;
    }
}
