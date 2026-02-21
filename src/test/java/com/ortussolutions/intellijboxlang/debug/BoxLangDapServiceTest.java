package com.ortussolutions.intellijboxlang.debug;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.eclipse.lsp4j.debug.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tests for BoxLang DAP service.
 */
public class BoxLangDapServiceTest extends BasePlatformTestCase {

	public void testServiceCanBeInstantiated() {
		BoxLangDapService service = new BoxLangDapService( getProject() );
		assertNotNull( "DAP service should be created", service );
		assertFalse( "New service should not be connected", service.isConnected() );
		assertNull( "New service should not have a server", service.getServer() );
		assertNull( "New service should not have capabilities", service.getServerCapabilities() );
	}

	public void testServiceCanAddAndRemoveEventListeners() {
		BoxLangDapService					service			= new BoxLangDapService( getProject() );

		AtomicBoolean						listenerCalled	= new AtomicBoolean( false );
		BoxLangDapService.DapEventListener	listener		= new BoxLangDapService.DapEventListener() {

																@Override
																public void onStopped( StoppedEventArguments args ) {
																	listenerCalled.set( true );
																}
															};

		service.addEventListener( listener );

		// Simulate a stopped event
		StoppedEventArguments args = new StoppedEventArguments();
		args.setReason( "breakpoint" );
		args.setThreadId( 1 );
		service.handleStopped( args );

		assertTrue( "Listener should have been called", listenerCalled.get() );

		// Remove listener and verify it's not called again
		listenerCalled.set( false );
		service.removeEventListener( listener );
		service.handleStopped( args );

		assertFalse( "Listener should not be called after removal", listenerCalled.get() );
	}

	public void testEventListenerReceivesCorrectEventData() {
		BoxLangDapService						service			= new BoxLangDapService( getProject() );

		AtomicReference<StoppedEventArguments>	receivedArgs	= new AtomicReference<>();
		service.addEventListener( new BoxLangDapService.DapEventListener() {

			@Override
			public void onStopped( StoppedEventArguments args ) {
				receivedArgs.set( args );
			}
		} );

		StoppedEventArguments args = new StoppedEventArguments();
		args.setReason( "breakpoint" );
		args.setThreadId( 42 );
		args.setAllThreadsStopped( true );
		service.handleStopped( args );

		assertNotNull( "Should have received args", receivedArgs.get() );
		assertEquals( "breakpoint", receivedArgs.get().getReason() );
		assertEquals( Integer.valueOf( 42 ), receivedArgs.get().getThreadId() );
		assertTrue( receivedArgs.get().getAllThreadsStopped() );
	}

	public void testOutputEventHandling() {
		BoxLangDapService						service			= new BoxLangDapService( getProject() );

		AtomicReference<OutputEventArguments>	receivedArgs	= new AtomicReference<>();
		service.addEventListener( new BoxLangDapService.DapEventListener() {

			@Override
			public void onOutput( OutputEventArguments args ) {
				receivedArgs.set( args );
			}
		} );

		OutputEventArguments args = new OutputEventArguments();
		args.setOutput( "Hello, World!\n" );
		args.setCategory( "stdout" );
		service.handleOutput( args );

		assertNotNull( "Should have received output args", receivedArgs.get() );
		assertEquals( "Hello, World!\n", receivedArgs.get().getOutput() );
		assertEquals( "stdout", receivedArgs.get().getCategory() );
	}

	public void testTerminatedEventHandling() {
		BoxLangDapService	service				= new BoxLangDapService( getProject() );

		AtomicBoolean		terminatedCalled	= new AtomicBoolean( false );
		service.addEventListener( new BoxLangDapService.DapEventListener() {

			@Override
			public void onTerminated( TerminatedEventArguments args ) {
				terminatedCalled.set( true );
			}
		} );

		TerminatedEventArguments args = new TerminatedEventArguments();
		service.handleTerminated( args );

		assertTrue( "Terminated listener should have been called", terminatedCalled.get() );
	}

	public void testDispose() {
		BoxLangDapService	service			= new BoxLangDapService( getProject() );

		// Add a listener
		AtomicBoolean		listenerCalled	= new AtomicBoolean( false );
		service.addEventListener( new BoxLangDapService.DapEventListener() {

			@Override
			public void onStopped( StoppedEventArguments args ) {
				listenerCalled.set( true );
			}
		} );

		// Dispose the service
		service.dispose();

		// Service should not be connected after dispose
		assertFalse( "Service should not be connected after dispose", service.isConnected() );

		// Listeners should be cleared - calling handleStopped should not throw
		// but also should not call the listener
		listenerCalled.set( false );
		service.handleStopped( new StoppedEventArguments() );
		assertFalse( "Listener should not be called after dispose", listenerCalled.get() );
	}

	public void testMultipleEventListeners() {
		BoxLangDapService	service			= new BoxLangDapService( getProject() );

		AtomicBoolean		listener1Called	= new AtomicBoolean( false );
		AtomicBoolean		listener2Called	= new AtomicBoolean( false );

		service.addEventListener( new BoxLangDapService.DapEventListener() {

			@Override
			public void onStopped( StoppedEventArguments args ) {
				listener1Called.set( true );
			}
		} );

		service.addEventListener( new BoxLangDapService.DapEventListener() {

			@Override
			public void onStopped( StoppedEventArguments args ) {
				listener2Called.set( true );
			}
		} );

		service.handleStopped( new StoppedEventArguments() );

		assertTrue( "Listener 1 should have been called", listener1Called.get() );
		assertTrue( "Listener 2 should have been called", listener2Called.get() );
	}

	public void testNotConnectedOperationsReturnFailedFuture() {
		BoxLangDapService service = new BoxLangDapService( getProject() );

		// All these operations should return failed futures when not connected
		assertTrue( service.launch( "/path/to/script.bx", null, null, false ).isCompletedExceptionally() );
		assertTrue( service.attach( "localhost", 5005, null, null ).isCompletedExceptionally() );
		assertTrue( service.configurationDone().isCompletedExceptionally() );
		assertTrue( service.setBreakpoints( "/path/to/script.bx", java.util.List.of() ).isCompletedExceptionally() );
		assertTrue( service.threads().isCompletedExceptionally() );
		assertTrue( service.stackTrace( 1 ).isCompletedExceptionally() );
		assertTrue( service.scopes( 1 ).isCompletedExceptionally() );
		assertTrue( service.variables( 1 ).isCompletedExceptionally() );
		assertTrue( service.continueExecution( 1 ).isCompletedExceptionally() );
		assertTrue( service.stepOver( 1 ).isCompletedExceptionally() );
		assertTrue( service.stepInto( 1 ).isCompletedExceptionally() );
		assertTrue( service.stepOut( 1 ).isCompletedExceptionally() );
		assertTrue( service.pause( 1 ).isCompletedExceptionally() );
		assertTrue( service.evaluate( "expression", 1, "watch" ).isCompletedExceptionally() );
	}

	public void testPrepareProgramPathForLaunchUsesWhitespaceSafeAlias() throws Exception {
		Path	dirWithSpaces	= Files.createTempDirectory( "boxlang dap test " );
		Path	scriptPath		= dirWithSpaces.resolve( "test.bxs" );
		Files.writeString( scriptPath, "println(\"hello\")\n" );

		String prepared = BoxLangDapService.prepareProgramPathForLaunch( scriptPath.toString() );
		assertFalse( "Prepared path should avoid whitespace when aliasing succeeds", prepared.matches( ".*\\s+.*" ) );
		assertFalse( "Prepared path should differ from original when original has whitespace", scriptPath.toString().equals( prepared ) );
		assertTrue( "Prepared alias path should exist", Files.exists( Path.of( prepared ) ) );
	}

	public void testPrepareProgramPathForLaunchExpandsTildeAndStripsWrappingQuotes() {
		String	userHome	= System.getProperty( "user.home" );
		String	prepared	= BoxLangDapService.prepareProgramPathForLaunch( "\"~/IdeaProjects/boxlang-test.bxs\"" );
		assertEquals( userHome + "/IdeaProjects/boxlang-test.bxs", prepared );
	}

	public void testPrepareProgramPathForLaunchLeavesSimplePathUnchanged() {
		String prepared = BoxLangDapService.prepareProgramPathForLaunch( "/tmp/boxlang/test.bxs" );
		assertEquals( "/tmp/boxlang/test.bxs", prepared );
	}
}
