package com.ortussolutions.intellijboxlang.debug;

import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.XBreakpointHandler;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.frame.XSuspendContext;
import org.eclipse.lsp4j.debug.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * The main debug process for BoxLang debugging.
 * Manages the connection to the DAP server and coordinates debugging operations.
 */
public class BoxLangDebugProcess extends XDebugProcess implements BoxLangDapService.DapEventListener {

	private static final Logger						LOG							= Logger.getInstance( BoxLangDebugProcess.class );
	// This process is created per debug session, so attempts reset naturally each session.
	private static final int						MAX_CONFIGURATION_ATTEMPTS	= 2;

	private final BoxLangDapService					dapService;
	private final BoxLangBreakpointHandler			breakpointHandler;
	private final BoxLangDebuggerEditorsProvider	editorsProvider;
	private final BoxLangDapProcessHandler			processHandler;

	// Launch parameters - stored so we can send launch request at the right time
	private final String							scriptPath;
	private final String							workingDirectory;
	private final List<String>						programArgs;

	// Attach mode parameters
	private final boolean							attachMode;
	private final String							attachHost;
	private final int								attachPort;
	private final String							localRoot;
	private final String							remoteRoot;

	// Track the current thread ID for stepping operations
	private volatile int							activeThreadId				= -1;

	// Track the top frame ID of the current suspension, used for REPL evaluation
	private volatile int							activeFrameId				= -1;

	// Track whether we've received the 'initialized' event from DAP
	private volatile boolean						dapInitialized				= false;

	// Track whether IntelliJ has finished registering breakpoints (sessionInitialized called)
	private volatile boolean						sessionReady				= false;

	// Track whether we've already completed configuration (prevent double calls)
	private volatile boolean						configurationCompleted		= false;
	private volatile boolean						configurationInProgress		= false;
	private volatile int							configurationAttempts		= 0;

	// Track run-to-cursor state: the file path where we set the temporary breakpoint
	private volatile String							runToCursorFilePath			= null;

	/**
	 * Creates a debug process in launch mode (runs a BoxLang script).
	 */
	public BoxLangDebugProcess( @NotNull XDebugSession session,
	    @NotNull BoxLangDapService dapService,
	    @NotNull String scriptPath,
	    @Nullable String workingDirectory,
	    @Nullable List<String> programArgs ) {
		this( session, dapService, scriptPath, workingDirectory, programArgs,
		    false, null, 0, null, null );
	}

	/**
	 * Creates a debug process in attach mode (connects to an already-running process).
	 */
	public static BoxLangDebugProcess createAttachProcess( @NotNull XDebugSession session,
	    @NotNull BoxLangDapService dapService,
	    @Nullable String attachHost,
	    int attachPort,
	    @Nullable String localRoot,
	    @Nullable String remoteRoot ) {
		return new BoxLangDebugProcess( session, dapService, null, null, null,
		    true, attachHost, attachPort, localRoot, remoteRoot );
	}

	private BoxLangDebugProcess( @NotNull XDebugSession session,
	    @NotNull BoxLangDapService dapService,
	    @Nullable String scriptPath,
	    @Nullable String workingDirectory,
	    @Nullable List<String> programArgs,
	    boolean attachMode,
	    @Nullable String attachHost,
	    int attachPort,
	    @Nullable String localRoot,
	    @Nullable String remoteRoot ) {
		super( session );
		this.dapService			= dapService;
		this.scriptPath			= scriptPath;
		this.workingDirectory	= workingDirectory;
		this.programArgs		= programArgs;
		this.attachMode			= attachMode;
		this.attachHost			= attachHost;
		this.attachPort			= attachPort;
		this.localRoot			= localRoot;
		this.remoteRoot			= remoteRoot;
		this.breakpointHandler	= new BoxLangBreakpointHandler( this );
		this.editorsProvider	= new BoxLangDebuggerEditorsProvider();

		// Create a custom ProcessHandler that stays alive until DAP terminated/exited events.
		// This is the key fix: previously we used the ProcessHandler from profileState.execute()
		// which would terminate when the normal (non-debugged) BoxLang process finished,
		// causing IntelliJ to tear down the debug session prematurely.
		this.processHandler		= new BoxLangDapProcessHandler();
		this.processHandler.startNotified();

		// Register for DAP events
		dapService.addEventListener( this );

		// Check if the 'initialized' event was already received before we registered
		// This can happen because dapService.start() sends initialize and may receive
		// the initialized event before BoxLangDebugProcess is created
		dapInitialized = dapService.isConfigurationReady();
	}

	@Override
	public @NotNull XDebuggerEditorsProvider getEditorsProvider() {
		return editorsProvider;
	}

	@Override
	public XBreakpointHandler<?>[] getBreakpointHandlers() {
		return new XBreakpointHandler[] { breakpointHandler };
	}

	@Override
	public @Nullable ExecutionConsole createConsole() {
		// Create a debug console with REPL support for expression evaluation
		BoxLangDebugConsole debugConsole = new BoxLangDebugConsole(
		    getSession().getProject(), this );
		debugConsole.attachToProcess( processHandler );
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
		sessionReady = true;
		if ( dapInitialized ) {
			completeConfiguration();
		}
	}

	/**
	 * Complete the DAP configuration by syncing breakpoints, launching/attaching, and sending configurationDone.
	 * This should only be called when BOTH the DAP server is initialized AND IntelliJ has
	 * finished registering breakpoints.
	 *
	 * The correct DAP message order is:
	 * 1. setBreakpoints (for all files)
	 * 2. launch or attach
	 * 3. configurationDone
	 */
	private synchronized void completeConfiguration() {
		if ( configurationCompleted || configurationInProgress ) {
			return;
		}
		if ( configurationAttempts >= MAX_CONFIGURATION_ATTEMPTS ) {
			LOG.error( "Exceeded maximum DAP configuration attempts" );
			return;
		}
		configurationInProgress = true;
		configurationAttempts++;

		breakpointHandler.syncAllBreakpoints()
		    .thenCompose( v -> {
			    if ( attachMode ) {
				    return dapService.attach( attachHost, attachPort, localRoot, remoteRoot );
			    } else {
				    return dapService.launch( scriptPath, workingDirectory, programArgs, false );
			    }
		    } )
		    .thenCompose( v -> dapService.configurationDone() )
		    .thenRun( () -> {
			    synchronized ( this ) {
				    configurationInProgress = false;
				    configurationCompleted = true;
			    }
			    breakpointHandler.markConfigurationComplete();
		    } )
		    .exceptionally( ex -> {
			    boolean shouldRetry;
			    synchronized ( this ) {
				    configurationInProgress = false;
				    shouldRetry			= !configurationCompleted && configurationAttempts < MAX_CONFIGURATION_ATTEMPTS
				        && sessionReady && dapInitialized;
			    }
			    if ( shouldRetry ) {
				    LOG.warn( "DAP configuration attempt failed, retrying", ex );
				    ApplicationManager.getApplication().invokeLater( this::completeConfiguration );
			    } else {
				    LOG.error( "Failed to complete DAP configuration", ex );
			    }
			    return null;
		    } );
	}

	// Stepping operations

	@Override
	public void startStepOver( @Nullable XSuspendContext context ) {
		performThreadAction( context, dapService::stepOver, "Step over" );
	}

	@Override
	public void startStepInto( @Nullable XSuspendContext context ) {
		performThreadAction( context, dapService::stepInto, "Step into" );
	}

	@Override
	public void startStepOut( @Nullable XSuspendContext context ) {
		performThreadAction( context, dapService::stepOut, "Step out" );
	}

	@Override
	public void resume( @Nullable XSuspendContext context ) {
		performThreadAction( context, dapService::continueExecution, "Resume" );
	}

	@Override
	public void startPausing() {
		performThreadAction( activeThreadId, dapService::pause, "Pause" );
	}

	@Override
	public void stop() {
		breakpointHandler.clear();
		dapService.removeEventListener( this );
		// In attach mode, don't terminate the debuggee — let the target process keep running.
		// In launch mode, the default dispose() behavior terminates the debuggee.
		if ( attachMode ) {
			try {
				dapService.disconnect( false ).get( 5, java.util.concurrent.TimeUnit.SECONDS );
			} catch ( Exception e ) {
				LOG.debug( "Error during attach mode disconnect", e );
			}
		}
		dapService.dispose();
	}

	@Override
	public void runToPosition( @NotNull XSourcePosition position, @Nullable XSuspendContext context ) {
		VirtualFile				file			= position.getFile();
		String					filePath		= file.getPath();
		int						dapLine			= position.getLine() + 1; // Convert 0-based to 1-based

		List<SourceBreakpoint>	allBreakpoints	= new ArrayList<>();
		allBreakpoints.addAll( breakpointHandler.collectDapBreakpointsForFile( filePath ) );

		SourceBreakpoint tempBreakpoint = new SourceBreakpoint();
		tempBreakpoint.setLine( dapLine );
		allBreakpoints.add( tempBreakpoint );

		runToCursorFilePath = filePath;

		// Use the same path aliasing as launch() so the DAP server path is consistent.
		String dapPath = BoxLangDapService.prepareProgramPathForLaunch( filePath );
		dapService.setBreakpoints( dapPath, allBreakpoints )
		    .thenCompose( response -> {
			    int threadId = getThreadId( context );
			    if ( threadId != -1 ) {
				    return dapService.continueExecution( threadId ).thenApply( r -> null );
			    }
			    return CompletableFuture.completedFuture( null );
		    } )
		    .exceptionally( ex -> {
			    LOG.warn( "Run to cursor failed", ex );
			    runToCursorFilePath = null;
			    return null;
		    } );
	}

	// DAP Event Handlers

	@Override
	public void onInitialized() {
		dapInitialized = true;
		if ( sessionReady ) {
			completeConfiguration();
		}
	}

	@Override
	public void onStopped( StoppedEventArguments args ) {
		if ( runToCursorFilePath != null ) {
			String rtcFilePath = runToCursorFilePath;
			runToCursorFilePath = null;
			List<SourceBreakpoint>	originalBreakpoints	= breakpointHandler.collectDapBreakpointsForFile( rtcFilePath );
			// Use the same path aliasing as launch() so the DAP server path is consistent.
			String					dapPath				= BoxLangDapService.prepareProgramPathForLaunch( rtcFilePath );
			dapService.setBreakpoints( dapPath, originalBreakpoints )
			    .exceptionally( ex -> {
				    LOG.warn( "Failed to restore breakpoints after run-to-cursor for " + rtcFilePath, ex );
				    return null;
			    } );
		}

		fetchPausedThreadFrames( args )
		    .thenAccept( threadFrames -> {
			    activeThreadId = threadFrames.threadId();
			    activeFrameId = resolveActiveFrameId( threadFrames.frames() );
			    BoxLangSuspendContext suspendContext = new BoxLangSuspendContext(
			        this, args, "Thread " + threadFrames.threadId(), threadFrames.frames() );
			    notifyPositionReached( suspendContext );
		    } )
		    .exceptionally( ex -> {
			    int fallbackThreadId = args.getThreadId() != null ? args.getThreadId() : 1;
			    activeThreadId = fallbackThreadId;
			    activeFrameId = -1;
			    BoxLangSuspendContext suspendContext = new BoxLangSuspendContext(
			        this, args, "Thread " + fallbackThreadId, new org.eclipse.lsp4j.debug.StackFrame[ 0 ] );
			    notifyPositionReached( suspendContext );
			    return null;
		    } );
	}

	@Override
	public void onExited( ExitedEventArguments args ) {
		processHandler.onDapExited( args.getExitCode() );
	}

	@Override
	public void onTerminated( TerminatedEventArguments args ) {
		processHandler.onDapTerminated();
		getSession().stop();
	}

	@Override
	public void onOutput( OutputEventArguments args ) {
		processHandler.onDapOutput( args.getOutput(), args.getCategory() );
	}

	// Helper methods

	/**
	 * Returns the frame ID of the top frame in the current suspension.
	 * Used by the debug console for REPL evaluation context.
	 */
	public int getActiveFrameId() {
		return activeFrameId;
	}

	public @Nullable String mapRemotePathToLocal( @Nullable String sourcePath ) {
		if ( sourcePath == null || sourcePath.isBlank() ) {
			return sourcePath;
		}

		String normalizedSourcePath = FileUtil.toSystemIndependentName( sourcePath );
		if ( localRoot == null || localRoot.isBlank() || remoteRoot == null || remoteRoot.isBlank() ) {
			return normalizedSourcePath;
		}

		String	normalizedLocalRoot		= FileUtil.toSystemIndependentName( localRoot );
		String	normalizedRemoteRoot	= FileUtil.toSystemIndependentName( remoteRoot );
		if ( !isPathWithinRoot( normalizedSourcePath, normalizedRemoteRoot ) ) {
			return normalizedSourcePath;
		}

		String suffix = normalizedSourcePath.substring( normalizedRemoteRoot.length() );
		if ( !suffix.isEmpty() && !suffix.startsWith( "/" ) ) {
			suffix = "/" + suffix;
		}
		return normalizedLocalRoot + suffix;
	}

	private boolean isPathWithinRoot( @NotNull String path, @NotNull String root ) {
		if ( SystemInfo.isWindows ) {
			String	lowerPath	= path.toLowerCase();
			String	lowerRoot	= root.toLowerCase();
			return isPathWithinRootCaseSensitive( lowerPath, lowerRoot );
		}
		return isPathWithinRootCaseSensitive( path, root );
	}

	private boolean isPathWithinRootCaseSensitive( @NotNull String path, @NotNull String root ) {
		if ( path.equals( root ) ) {
			return true;
		}
		if ( !path.startsWith( root ) ) {
			return false;
		}
		if ( root.endsWith( "/" ) ) {
			return true;
		}
		return path.length() > root.length() && path.charAt( root.length() ) == '/';
	}

	private int getThreadId( @Nullable XSuspendContext context ) {
		if ( context instanceof BoxLangSuspendContext ) {
			return ( ( BoxLangSuspendContext ) context ).getThreadId();
		}
		return activeThreadId;
	}

	private void notifyPositionReached( @NotNull BoxLangSuspendContext suspendContext ) {
		ApplicationManager.getApplication().invokeLater( () -> getSession().positionReached( suspendContext ) );
	}

	private int resolveActiveFrameId( @NotNull org.eclipse.lsp4j.debug.StackFrame[] frames ) {
		if ( frames.length == 0 ) {
			return -1;
		}

		for ( org.eclipse.lsp4j.debug.StackFrame frame : frames ) {
			Source source = frame.getSource();
			if ( source != null && source.getPath() != null && !source.getPath().isBlank() ) {
				return frame.getId();
			}
		}

		return frames[ 0 ].getId();
	}

	private CompletableFuture<ThreadFrames> fetchPausedThreadFrames( @NotNull StoppedEventArguments args ) {
		Integer stoppedThreadId = args.getThreadId();
		if ( stoppedThreadId != null && stoppedThreadId > 0 ) {
			return dapService.stackTrace( stoppedThreadId )
			    .handle( ( response, error ) -> {
				    if ( error != null ) {
					    LOG.warn( "Failed to fetch stack trace for stopped thread " + stoppedThreadId, error );
					    return new ThreadFrames( stoppedThreadId, new org.eclipse.lsp4j.debug.StackFrame[ 0 ] );
				    }
				    return new ThreadFrames( stoppedThreadId, stackFrames( response ) );
			    } )
			    .thenCompose( threadFrames -> {
				    if ( threadFrames.frames().length > 0 || !Boolean.TRUE.equals( args.getAllThreadsStopped() ) ) {
					    return CompletableFuture.completedFuture( threadFrames );
				    }
				    return fetchFirstNonEmptyThreadFrames( stoppedThreadId );
			    } );
		}

		return fetchFirstNonEmptyThreadFrames( 1 );
	}

	private CompletableFuture<ThreadFrames> fetchFirstNonEmptyThreadFrames( int fallbackThreadId ) {
		return dapService.threads()
		    .handle( ( threadsResponse, error ) -> {
			    if ( error != null || threadsResponse == null || threadsResponse.getThreads() == null ) {
				    if ( error != null ) {
					    LOG.warn( "Failed to fetch thread list while resolving paused frame", error );
				    }
				    return List.<Integer>of();
			    }

			    List<Integer> threadIds = new ArrayList<>();
			    for ( org.eclipse.lsp4j.debug.Thread thread : threadsResponse.getThreads() ) {
				    if ( thread.getId() > 0 ) {
					    threadIds.add( thread.getId() );
				    }
			    }
			    return threadIds;
		    } )
		    .thenCompose( threadIds -> tryThreadStackTraces( threadIds, 0, fallbackThreadId ) );
	}

	private CompletableFuture<ThreadFrames> tryThreadStackTraces( @NotNull List<Integer> threadIds,
	    int index,
	    int fallbackThreadId ) {
		if ( index >= threadIds.size() ) {
			return CompletableFuture.completedFuture(
			    new ThreadFrames( fallbackThreadId, new org.eclipse.lsp4j.debug.StackFrame[ 0 ] ) );
		}

		int threadId = threadIds.get( index );
		return dapService.stackTrace( threadId )
		    .handle( ( response, error ) -> {
			    if ( error != null ) {
				    return new org.eclipse.lsp4j.debug.StackFrame[ 0 ];
			    }
			    return stackFrames( response );
		    } )
		    .thenCompose( frames -> {
			    if ( frames.length > 0 ) {
				    return CompletableFuture.completedFuture( new ThreadFrames( threadId, frames ) );
			    }
			    return tryThreadStackTraces( threadIds, index + 1, fallbackThreadId );
		    } );
	}

	private org.eclipse.lsp4j.debug.StackFrame[] stackFrames( @Nullable StackTraceResponse response ) {
		if ( response == null || response.getStackFrames() == null ) {
			return new org.eclipse.lsp4j.debug.StackFrame[ 0 ];
		}
		return response.getStackFrames();
	}

	private void performThreadAction( int threadId,
	    @NotNull Function<Integer, CompletableFuture<?>> action,
	    @NotNull String actionName ) {
		if ( threadId == -1 ) {
			return;
		}
		action.apply( threadId )
		    .exceptionally( ex -> {
			    LOG.warn( actionName + " failed", ex );
			    return null;
		    } );
	}

	private void performThreadAction( @Nullable XSuspendContext context,
	    @NotNull Function<Integer, CompletableFuture<?>> action,
	    @NotNull String actionName ) {
		performThreadAction( getThreadId( context ), action, actionName );
	}

	private record ThreadFrames( int threadId, org.eclipse.lsp4j.debug.StackFrame[] frames ) {
	}
}
