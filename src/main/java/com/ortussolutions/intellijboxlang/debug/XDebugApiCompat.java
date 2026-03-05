package com.ortussolutions.intellijboxlang.debug;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugProcessStarter;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Compatibility wrapper around XDebugger APIs that are deprecated in newer IDEs.
 * Uses reflection so we can run on 2024.2.6 while avoiding direct references.
 */
public final class XDebugApiCompat {

	private XDebugApiCompat() {
	}

	public static @NotNull XDebugSession startSession(
	    @NotNull Project project,
	    @NotNull ExecutionEnvironment environment,
	    @NotNull String sessionName,
	    @NotNull XDebugProcessStarter starter ) throws ExecutionException {
		XDebuggerManager manager = XDebuggerManager.getInstance( project );

		try {
			Method	modern	= XDebuggerManager.class.getMethod(
			    "startSessionAndShowTab",
			    String.class,
			    XDebugProcessStarter.class,
			    ExecutionEnvironment.class
			);
			Object	result	= modern.invoke( manager, sessionName, starter, environment );
			return ( XDebugSession ) result;
		} catch ( NoSuchMethodException ignored ) {
			return startSessionLegacy( manager, environment, starter );
		} catch ( InvocationTargetException e ) {
			throw unwrapExecutionException( "Failed to start debug session", e );
		} catch ( IllegalAccessException e ) {
			throw new ExecutionException( "Failed to start debug session", e );
		}
	}

	private static @NotNull XDebugSession startSessionLegacy(
	    @NotNull XDebuggerManager manager,
	    @NotNull ExecutionEnvironment environment,
	    @NotNull XDebugProcessStarter starter ) throws ExecutionException {
		try {
			Method	legacy	= XDebuggerManager.class.getMethod(
			    "startSession",
			    ExecutionEnvironment.class,
			    XDebugProcessStarter.class
			);
			Object	result	= legacy.invoke( manager, environment, starter );
			return ( XDebugSession ) result;
		} catch ( InvocationTargetException e ) {
			throw unwrapExecutionException( "Failed to start debug session", e );
		} catch ( ReflectiveOperationException e ) {
			throw new ExecutionException( "Failed to start debug session", e );
		}
	}

	public static @Nullable RunContentDescriptor getRunContentDescriptor( @NotNull XDebugSession session ) throws ExecutionException {
		try {
			Method method = XDebugSession.class.getMethod( "getRunContentDescriptor" );
			return ( RunContentDescriptor ) method.invoke( session );
		} catch ( NoSuchMethodException ignored ) {
			return null;
		} catch ( InvocationTargetException e ) {
			throw unwrapExecutionException( "Failed to obtain debug run content descriptor", e );
		} catch ( IllegalAccessException e ) {
			throw new ExecutionException( "Failed to obtain debug run content descriptor", e );
		}
	}

	private static ExecutionException unwrapExecutionException( String message, InvocationTargetException invocationTargetException ) {
		Throwable cause = invocationTargetException.getCause();
		if ( cause instanceof ExecutionException executionException ) {
			return executionException;
		}
		return new ExecutionException( message, cause != null ? cause : invocationTargetException );
	}
}
