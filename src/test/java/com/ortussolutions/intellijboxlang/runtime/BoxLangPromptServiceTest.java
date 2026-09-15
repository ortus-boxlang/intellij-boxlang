package com.ortussolutions.intellijboxlang.runtime;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import java.util.concurrent.CompletableFuture;

public class BoxLangPromptServiceTest extends BasePlatformTestCase {

	public void testDownloadAcceptsBeforeNotificationExpires() {
		CompletableFuture<Boolean>	result			= new CompletableFuture<>();
		Notification				notification	= createConfirmation( result );
		performAction( notification, "Download" );
		assertEquals( Boolean.TRUE, result.getNow( null ) );
		assertTrue( notification.isExpired() );
	}

	public void testNotNowDeclinesAndExpiresNotification() {
		CompletableFuture<Boolean>	result			= new CompletableFuture<>();
		Notification				notification	= createConfirmation( result );
		performAction( notification, "Not Now" );
		assertEquals( Boolean.FALSE, result.getNow( null ) );
		assertTrue( notification.isExpired() );
	}

	public void testDismissalDeclinesDownload() {
		CompletableFuture<Boolean>	result			= new CompletableFuture<>();
		Notification				notification	= createConfirmation( result );
		notification.expire();
		assertEquals( Boolean.FALSE, result.getNow( null ) );
	}

	public void testUnansweredPromptRemainsActionable() {
		CompletableFuture<Boolean>	result			= new CompletableFuture<>();
		Notification				notification	= createConfirmation( result );
		assertFalse( result.isDone() );
		assertFalse( notification.isExpired() );
		performAction( notification, "Download" );
		assertEquals( Boolean.TRUE, result.getNow( null ) );
	}

	public void testDismissedNotificationCannotStartDownloadLater() {
		CompletableFuture<Boolean>	result			= new CompletableFuture<>();
		Notification				notification	= createConfirmation( result );
		notification.expire();
		performAction( notification, "Download" );
		assertEquals( Boolean.FALSE, result.getNow( null ) );
	}

	private Notification createConfirmation( CompletableFuture<Boolean> result ) {
		return BoxLangPromptService.createDownloadConfirmation( "Download BoxLang Runtime", "Download now?", result );
	}

	private void performAction( Notification notification, String text ) {
		NotificationAction action = ( NotificationAction ) notification.getActions().stream()
		    .filter( candidate -> text.equals( candidate.getTemplateText() ) ).findFirst().orElseThrow();
		action.actionPerformed( AnActionEvent.createFromAnAction( action, null, "test", DataContext.EMPTY_CONTEXT ), notification );
	}
}
