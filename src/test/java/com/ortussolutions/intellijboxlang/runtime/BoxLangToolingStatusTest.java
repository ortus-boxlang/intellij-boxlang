package com.ortussolutions.intellijboxlang.runtime;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import java.util.concurrent.atomic.AtomicInteger;

public class BoxLangToolingStatusTest extends BasePlatformTestCase {

	public void testBackgroundDownloadFailureRetryAndSuccess() {
		java.util.List<Notification> notifications = new java.util.concurrent.CopyOnWriteArrayList<>();
		getProject().getMessageBus().connect( getTestRootDisposable() ).subscribe( com.intellij.notification.Notifications.TOPIC,
		    new com.intellij.notification.Notifications() {

			    @Override
			    public void notify( Notification notification ) {
				    notifications.add( notification );
			    }
		    } );
		AtomicInteger				attempts	= new AtomicInteger();
		AtomicInteger				successes	= new AtomicInteger();
		String						name		= "Retry test runtime";
		BoxLangSetupTasks.Operation	operation	= indicator -> {
													if ( attempts.incrementAndGet() == 1 )
														throw new java.io.IOException( "HTTP 503 simulated server failure" );
												};
		BoxLangSetupTasks.download( getProject(), name, operation, successes::incrementAndGet );
		// A second editor request while setup is queued must not launch another download.
		BoxLangSetupTasks.download( getProject(), name, operation, successes::incrementAndGet );
		com.intellij.testFramework.PlatformTestUtil.waitWithEventsDispatching( "download failure notification",
		    () -> notifications.stream().anyMatch( n -> n.getTitle().equals( name + " needs attention" ) ), 10 );
		assertEquals( 1, attempts.get() );
		assertEquals( 0, successes.get() );
		assertEquals( BoxLangToolingStatus.Phase.FAILED, BoxLangToolingStatus.get( getProject() ).entries().get( name ).phase() );
		Notification		failed	= notifications.stream().filter( n -> n.getTitle().equals( name + " needs attention" ) ).findFirst().orElseThrow();
		NotificationAction	retry	= ( NotificationAction ) failed.getActions().getFirst();
		retry.actionPerformed( AnActionEvent.createFromAnAction( retry, null, "test", DataContext.EMPTY_CONTEXT ), failed );
		com.intellij.testFramework.PlatformTestUtil.waitWithEventsDispatching( "successful retry", () -> successes.get() == 1, 10 );
		assertEquals( 2, attempts.get() );
		assertEquals( BoxLangToolingStatus.Phase.INSTALLED, BoxLangToolingStatus.get( getProject() ).entries().get( name ).phase() );
		notifications.forEach( Notification::expire );
	}

	public void testCancelledBackgroundDownloadDoesNotReportSuccess() {
		AtomicInteger	successes	= new AtomicInteger();
		String			name		= "Cancelled runtime";
		BoxLangSetupTasks.download( getProject(), name, indicator -> {
			indicator.cancel();
			indicator.checkCanceled();
		}, successes::incrementAndGet );
		com.intellij.testFramework.PlatformTestUtil.waitWithEventsDispatching( "cancellation", () -> {
			var entry = BoxLangToolingStatus.get( getProject() ).entries().get( name );
			return entry != null && entry.phase() == BoxLangToolingStatus.Phase.CANCELLED;
		}, 10 );
		assertEquals( 0, successes.get() );
	}

	public void testConcurrentDownloadSuppressionAllowsExplicitRetryAfterFailure() {
		var status = new BoxLangToolingStatus();
		assertTrue( status.beginDownload( "Runtime" ) );
		assertFalse( status.beginDownload( "Runtime" ) );
		status.record( "Runtime", BoxLangToolingStatus.Phase.FAILED, "HTTP 503" );
		assertTrue( status.beginDownload( "Runtime" ) );
		status.record( "Runtime", BoxLangToolingStatus.Phase.INSTALLED, "Installed" );
		assertEquals( BoxLangToolingStatus.Phase.INSTALLED, status.entries().get( "Runtime" ).phase() );
		assertFalse( status.entries().containsKey( "BoxLang language server" ) );
	}

	public void testFailureNotificationIncludesReasonAndExplicitRetry() {
		AtomicInteger	retried	= new AtomicInteger();
		Notification	notice	= BoxLangSetupTasks.failureNotification( "Runtime",
		    BoxLangSetupTasks.failureDetail( new java.io.IOException( "HTTP 503" ) ), retried::incrementAndGet );
		assertTrue( notice.getContent().contains( "HTTP 503" ) );
		assertTrue( notice.getContent().contains( "BoxLang settings" ) );
		assertEquals( 0, retried.get() );
		NotificationAction retry = ( NotificationAction ) notice.getActions().getFirst();
		assertEquals( "Retry", retry.getTemplateText() );
		retry.actionPerformed( AnActionEvent.createFromAnAction( retry, null, "test", DataContext.EMPTY_CONTEXT ), notice );
		assertEquals( 1, retried.get() );
		assertTrue( notice.isExpired() );
	}

	public void testStatusHistoryIsBoundedAndDeduplicated() {
		var status = new BoxLangToolingStatus();
		for ( int i = 0; i < 10; i++ )
			status.record( "Runtime", BoxLangToolingStatus.Phase.WAITING, "Download now" );
		assertEquals( 1, status.events().size() );
		for ( int i = 0; i < 150; i++ )
			status.event( "x".repeat( 3000 ) );
		assertEquals( 100, status.events().size() );
		assertTrue( status.events().getFirst().length() < 2100 );
	}

	public void testReportRedactsPathsAndCommonCredentials() {
		String output = BoxLangDiagnosticReport.redact(
		    "/Users/person/work/project/a.cfm /Users/person/.boxlang https://user:password@example.com?token=abc password=foo Authorization: Bearer secret123",
		    "/Users/person", "/Users/person/work/project" );
		assertTrue( output.contains( "$PROJECT/a.cfm" ) );
		assertTrue( output.contains( "$HOME/.boxlang" ) );
		assertFalse( output.contains( "/Users/person" ) );
		assertFalse( output.contains( "password@example" ) );
		assertFalse( output.contains( "token=abc" ) );
		assertFalse( output.contains( "password=foo" ) );
		assertFalse( output.contains( "secret123" ) );
	}

	public void testReportContainsVersionsConfigurationAndSessionErrors() {
		BoxLangToolingStatus.get( getProject() ).record( "BoxLang language server", BoxLangToolingStatus.Phase.FAILED, "Connection refused" );
		String report = BoxLangDiagnosticReport.collect( getProject() );
		assertTrue( report.contains( "Plugin:" ) );
		assertTrue( report.contains( "IDE:" ) );
		assertTrue( report.contains( "IDE Java:" ) );
		assertTrue( report.contains( "LSP module override:" ) );
		assertTrue( report.contains( "Runtime cache:" ) );
		assertTrue( report.contains( "Connection refused" ) );
		assertTrue( report.contains( "presence does not prove readiness" ) );
		assertNotNull( ActionManager.getInstance().getAction( "BoxLang.ToolingStatus" ) );
	}
}
