package com.ortussolutions.intellijboxlang.debug;

import com.intellij.execution.ExecutionResult;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ExecutionConsole;
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
    private final ExecutionResult executionResult;
    
    // Track the current thread ID for stepping operations
    private volatile int activeThreadId = -1;

    public BoxLangDebugProcess(@NotNull XDebugSession session,
                                @NotNull BoxLangDapService dapService,
                                @Nullable ExecutionResult executionResult) {
        super(session);
        this.dapService = dapService;
        this.executionResult = executionResult;
        this.breakpointHandler = new BoxLangBreakpointHandler(this);
        this.editorsProvider = new BoxLangDebuggerEditorsProvider();
        
        // Register for DAP events
        dapService.addEventListener(this);
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
        if (executionResult != null) {
            return executionResult.getExecutionConsole();
        }
        return super.createConsole();
    }

    @Override
    public @Nullable ProcessHandler doGetProcessHandler() {
        if (executionResult != null) {
            return executionResult.getProcessHandler();
        }
        return null;
    }

    /**
     * Returns the DAP service for this debug session.
     */
    public BoxLangDapService getDapService() {
        return dapService;
    }

    /**
     * Called when the debug session is fully started.
     * Sends the configurationDone request to the DAP server.
     */
    public void sessionInitialized() {
        LOG.info("Debug session initialized, sending configurationDone");
        
        // Sync all breakpoints first
        breakpointHandler.syncAllBreakpoints();
        
        // Tell the server we're done configuring
        dapService.configurationDone()
            .exceptionally(ex -> {
                LOG.warn("Failed to send configurationDone", ex);
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
    public void onStopped(StoppedEventArguments args) {
        LOG.info("Execution stopped: " + args.getReason() + " on thread " + args.getThreadId());
        
        if (args.getThreadId() != null) {
            activeThreadId = args.getThreadId();
        }
        
        // Fetch stack trace and create suspend context
        // This will be implemented in Phase 6
        // For now, we'll create a placeholder suspend context
        getSession().positionReached(new BoxLangSuspendContext(this, args));
    }

    @Override
    public void onContinued(ContinuedEventArguments args) {
        LOG.debug("Execution continued on thread " + args.getThreadId());
    }

    @Override
    public void onExited(ExitedEventArguments args) {
        LOG.info("Process exited with code: " + args.getExitCode());
    }

    @Override
    public void onTerminated(TerminatedEventArguments args) {
        LOG.info("Debug session terminated");
        getSession().stop();
    }

    @Override
    public void onThread(ThreadEventArguments args) {
        LOG.debug("Thread event: " + args.getReason() + " for thread " + args.getThreadId());
    }

    @Override
    public void onOutput(OutputEventArguments args) {
        // Output will be handled by the console in Phase 10
        String output = args.getOutput();
        String category = args.getCategory();
        LOG.debug("Output [" + category + "]: " + output);
    }

    @Override
    public void onBreakpoint(BreakpointEventArguments args) {
        LOG.debug("Breakpoint event: " + args.getReason());
    }

    // Helper methods

    private int getThreadId(@Nullable XSuspendContext context) {
        if (context instanceof BoxLangSuspendContext) {
            return ((BoxLangSuspendContext) context).getThreadId();
        }
        return activeThreadId;
    }
}
