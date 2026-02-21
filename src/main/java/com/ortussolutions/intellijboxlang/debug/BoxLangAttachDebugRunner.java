package com.ortussolutions.intellijboxlang.debug;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.GenericProgramRunner;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugProcessStarter;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.ortussolutions.intellijboxlang.run.BoxLangAttachRunConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Program runner that handles debug-attach execution for BoxLang.
 * Starts the bx-debugger DAP server and sends a DAP "attach" request
 * to connect to an already-running BoxLang process via JDWP.
 */
public class BoxLangAttachDebugRunner extends GenericProgramRunner<RunnerSettings> {

	private static final String RUNNER_ID = "BoxLangAttachDebugRunner";

	@Override
	public @NotNull String getRunnerId() {
		return RUNNER_ID;
	}

	@Override
	public boolean canRun( @NotNull String executorId, @NotNull RunProfile profile ) {
		return DefaultDebugExecutor.EXECUTOR_ID.equals( executorId )
		    && profile instanceof BoxLangAttachRunConfiguration;
	}

	@Override
	protected @Nullable RunContentDescriptor doExecute( @NotNull RunProfileState state,
	    @NotNull ExecutionEnvironment environment ) throws ExecutionException {
		BoxLangAttachRunConfiguration	configuration	= ( BoxLangAttachRunConfiguration ) environment.getRunProfile();
		Project							project			= environment.getProject();

		String							host			= configuration.getHost();
		int								jdwpPort		= configuration.getJdwpPort();
		String							localRoot		= configuration.getLocalRoot();
		String							remoteRoot		= configuration.getRemoteRoot();

		try {
			XDebugSession debugSession = XDebuggerManager.getInstance( project ).startSession(
			    environment,
			    new XDebugProcessStarter() {

				    @Override
				    public @NotNull XDebugProcess start( @NotNull XDebugSession session ) throws ExecutionException {
					    return createAttachDebugProcess( session, project, host, jdwpPort, localRoot, remoteRoot );
				    }
			    }
			);

			return debugSession.getRunContentDescriptor();
		} catch ( Exception e ) {
			throw new ExecutionException( "Failed to start attach debug session: " + e.getMessage(), e );
		}
	}

	private BoxLangDebugProcess createAttachDebugProcess( @NotNull XDebugSession session,
	    @NotNull Project project,
	    @Nullable String host,
	    int jdwpPort,
	    @Nullable String localRoot,
	    @Nullable String remoteRoot ) throws ExecutionException {
		BoxLangDapService dapService = new BoxLangDapService( project );

		try {
			dapService.start();
			return BoxLangDebugProcess.createAttachProcess( session, dapService, host, jdwpPort, localRoot, remoteRoot );

		} catch ( Exception e ) {
			dapService.dispose();
			throw new ExecutionException( "Failed to start DAP server for attach: " + e.getMessage(), e );
		}
	}
}
