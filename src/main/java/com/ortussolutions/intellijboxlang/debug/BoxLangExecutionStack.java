package com.ortussolutions.intellijboxlang.debug;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.xdebugger.frame.XExecutionStack;
import com.intellij.xdebugger.frame.XStackFrame;
import org.eclipse.lsp4j.debug.StackFrame;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the execution stack for a single thread in the BoxLang debugger.
 * Contains the list of stack frames retrieved from the DAP server's stackTrace response.
 * 
 * This is what populates the "Frames" panel in IntelliJ's debug tool window.
 */
public class BoxLangExecutionStack extends XExecutionStack {
    private static final Logger LOG = Logger.getInstance(BoxLangExecutionStack.class);

    private final BoxLangDebugProcess debugProcess;
    private final int threadId;
    private final List<BoxLangStackFrame> frames;

    /**
     * Creates an execution stack from DAP stack frames.
     * 
     * @param debugProcess the debug process
     * @param threadId the thread ID this stack belongs to
     * @param threadName display name for this thread
     * @param dapFrames the stack frames from the DAP stackTrace response
     */
    public BoxLangExecutionStack(@NotNull BoxLangDebugProcess debugProcess,
                                  int threadId,
                                  @NotNull String threadName,
                                  @NotNull StackFrame[] dapFrames) {
        super(threadName);
        this.debugProcess = debugProcess;
        this.threadId = threadId;
        this.frames = new ArrayList<>(dapFrames.length);

        for (StackFrame dapFrame : dapFrames) {
            frames.add(new BoxLangStackFrame(debugProcess, dapFrame));
        }

        LOG.debug("Created execution stack for thread " + threadId + " (" + threadName + ") with " + frames.size() + " frames");
    }

    /**
     * Returns the thread ID for this execution stack.
     */
    public int getThreadId() {
        return threadId;
    }

    @Override
    public @Nullable XStackFrame getTopFrame() {
        if (frames.isEmpty()) {
            return null;
        }
        return frames.get(0);
    }

    @Override
    public void computeStackFrames(int firstFrameIndex, @NotNull XStackFrameContainer container) {
        if (firstFrameIndex >= frames.size()) {
            container.addStackFrames(List.of(), true);
            return;
        }

        List<BoxLangStackFrame> subList = frames.subList(firstFrameIndex, frames.size());
        container.addStackFrames(new ArrayList<>(subList), true);
    }
}
