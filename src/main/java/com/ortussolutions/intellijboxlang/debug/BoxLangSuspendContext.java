package com.ortussolutions.intellijboxlang.debug;

import com.intellij.xdebugger.frame.XExecutionStack;
import com.intellij.xdebugger.frame.XSuspendContext;
import org.eclipse.lsp4j.debug.StackFrame;
import org.eclipse.lsp4j.debug.StoppedEventArguments;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents the suspended state of a debug session.
 * Contains information about why execution stopped and the current execution stacks.
 *
 * When execution stops (breakpoint hit, step completed, etc.), this context is created
 * with the stack frames fetched from the DAP server. IntelliJ uses this to populate
 * the Frames panel, highlight the current line, and enable stepping controls.
 */
public class BoxLangSuspendContext extends XSuspendContext {

	private final int					threadId;
	private final BoxLangExecutionStack	executionStack;

	/**
	 * Creates a suspend context with pre-fetched stack frames.
	 *
	 * @param debugProcess the debug process
	 * @param stoppedEvent the DAP stopped event arguments
	 * @param threadName   display name for the stopped thread
	 * @param stackFrames  the stack frames from the DAP stackTrace response
	 */
	public BoxLangSuspendContext( @NotNull BoxLangDebugProcess debugProcess,
	    @NotNull StoppedEventArguments stoppedEvent,
	    @NotNull String threadName,
	    @NotNull StackFrame[] stackFrames ) {
		this.threadId		= stoppedEvent.getThreadId() != null ? stoppedEvent.getThreadId() : 1;
		this.executionStack	= new BoxLangExecutionStack( debugProcess, threadId, threadName, stackFrames );
	}

	/**
	 * Returns the thread ID that caused the stop.
	 */
	public int getThreadId() {
		return threadId;
	}

	@Override
	public @Nullable XExecutionStack getActiveExecutionStack() {
		return executionStack;
	}

	@Override
	public XExecutionStack @NotNull [] getExecutionStacks() {
		return new XExecutionStack[] { executionStack };
	}
}
