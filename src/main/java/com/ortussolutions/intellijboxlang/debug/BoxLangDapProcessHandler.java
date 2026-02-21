package com.ortussolutions.intellijboxlang.debug;

import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputType;
import org.jetbrains.annotations.Nullable;

import java.io.OutputStream;

/**
 * A custom ProcessHandler that tracks the lifecycle of the bx-debugger DAP server.
 * <p>
 * Unlike a normal ProcessHandler that wraps an OS process and terminates when that process exits,
 * this handler stays alive as long as the DAP debug session is active. It terminates only when:
 * <ul>
 * <li>The DAP server sends a "terminated" or "exited" event</li>
 * <li>The user explicitly stops the debug session</li>
 * </ul>
 * <p>
 * This solves the root cause of premature debug session termination: previously, the debug session
 * was tied to a normal BoxLang run process (via profileState.execute()) which would finish quickly
 * for simple scripts, causing IntelliJ to tear down the entire debug session before breakpoints
 * could be hit. Now, the debug session lifecycle is controlled by DAP protocol events.
 * <p>
 * This handler also receives DAP output events and forwards them to IntelliJ's console.
 */
public class BoxLangDapProcessHandler extends ProcessHandler {

	private volatile boolean destroyed = false;

	public BoxLangDapProcessHandler() {
	}

	/**
	 * Called when the debug session starts. Signals that the process is "started".
	 */
	public void startNotified() {
		super.startNotify();
	}

	@Override
	protected void destroyProcessImpl() {
		destroyed = true;
		notifyProcessTerminated( 0 );
	}

	@Override
	protected void detachProcessImpl() {
		destroyed = true;
		notifyProcessDetached();
	}

	@Override
	public boolean detachIsDefault() {
		return false;
	}

	@Override
	public @Nullable OutputStream getProcessInput() {
		// No direct process input - all communication goes through DAP protocol
		return null;
	}

	/**
	 * Called when the DAP server sends a "terminated" event.
	 * This signals that the debugged program has finished and the debug session should end.
	 */
	public void onDapTerminated() {
		if ( destroyed ) {
			return;
		}
		destroyed = true;
		notifyProcessTerminated( 0 );
	}

	/**
	 * Called when the DAP server sends an "exited" event with an exit code.
	 * We store the exit code but don't terminate yet - wait for the "terminated" event.
	 */
	public void onDapExited( int exitCode ) {
		if ( !destroyed ) {
			destroyed = true;
			notifyProcessTerminated( exitCode );
		}
	}

	/**
	 * Forwards DAP output to IntelliJ's console.
	 *
	 * @param text     The output text
	 * @param category The DAP output category ("stdout", "stderr", "console", etc.)
	 */
	public void onDapOutput( String text, String category ) {
		if ( destroyed || text == null ) {
			return;
		}

		ProcessOutputType outputType = switch ( category ) {
			case "stderr" -> ProcessOutputType.STDERR;
			case "console", "important" -> ProcessOutputType.SYSTEM;
			default -> ProcessOutputType.STDOUT;
		};
		notifyTextAvailable( text, outputType );
	}
}
