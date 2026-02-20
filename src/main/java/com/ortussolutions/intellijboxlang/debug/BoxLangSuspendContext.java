package com.ortussolutions.intellijboxlang.debug;

import com.intellij.xdebugger.frame.XExecutionStack;
import com.intellij.xdebugger.frame.XSuspendContext;
import org.eclipse.lsp4j.debug.StoppedEventArguments;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents the suspended state of a debug session.
 * Contains information about why execution stopped and the current execution stacks.
 * 
 * This is a basic implementation for Phase 5. Phase 6 will add stack frame support.
 */
public class BoxLangSuspendContext extends XSuspendContext {

    private final BoxLangDebugProcess debugProcess;
    private final StoppedEventArguments stoppedEvent;
    private final int threadId;

    public BoxLangSuspendContext(@NotNull BoxLangDebugProcess debugProcess,
                                  @NotNull StoppedEventArguments stoppedEvent) {
        this.debugProcess = debugProcess;
        this.stoppedEvent = stoppedEvent;
        this.threadId = stoppedEvent.getThreadId() != null ? stoppedEvent.getThreadId() : 1;
    }

    /**
     * Returns the thread ID that caused the stop.
     */
    public int getThreadId() {
        return threadId;
    }

    /**
     * Returns the reason for stopping (breakpoint, step, pause, etc.)
     */
    public String getStopReason() {
        return stoppedEvent.getReason();
    }

    /**
     * Returns the debug process.
     */
    public BoxLangDebugProcess getDebugProcess() {
        return debugProcess;
    }

    @Override
    public @Nullable XExecutionStack getActiveExecutionStack() {
        // Phase 6 will implement BoxLangExecutionStack
        // For now, return null - the debugger will still work but won't show stack frames
        return null;
    }

    @Override
    public XExecutionStack @NotNull [] getExecutionStacks() {
        XExecutionStack activeStack = getActiveExecutionStack();
        if (activeStack != null) {
            return new XExecutionStack[]{activeStack};
        }
        return new XExecutionStack[0];
    }
}
