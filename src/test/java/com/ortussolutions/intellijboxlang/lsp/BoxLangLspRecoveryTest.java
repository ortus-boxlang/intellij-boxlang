package com.ortussolutions.intellijboxlang.lsp;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.ortussolutions.intellijboxlang.runtime.BoxLangToolingStatus;
import com.ortussolutions.intellijboxlang.settings.BoxLangProjectSettings;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class BoxLangLspRecoveryTest extends BasePlatformTestCase {

	public void testStartupFailureIsVisibleAndRetriesOnlyOnExplicitAction() {
		var		settings	= BoxLangProjectSettings.getInstance( getProject() ).getSettings();
		String	previous	= settings.lspModulePath;
		settings.lspModulePath = getProject().getBasePath() + "/nonexistent-lsp-module";
		List<Notification> notices = new CopyOnWriteArrayList<>();
		getProject().getMessageBus().connect( getTestRootDisposable() ).subscribe( Notifications.TOPIC, new Notifications() {

			@Override
			public void notify( Notification notification ) {
				if ( notification.getTitle().equals( "BoxLang language server needs attention" ) )
					notices.add( notification );
			}
		} );
		try {
			var service = BoxLangLspClientService.getInstance( getProject() );
			service.ensureStartedAsync();
			PlatformTestUtil.waitWithEventsDispatching( "startup error", () -> notices.size() == 1, 10 );
			assertTrue( notices.getFirst().getContent().contains( "module path" ) );
			assertEquals( BoxLangToolingStatus.Phase.FAILED,
			    BoxLangToolingStatus.get( getProject() ).entries().get( "BoxLang language server" ).phase() );
			for ( int i = 0; i < 10; i++ )
				service.ensureStartedAsync();
			com.intellij.util.ui.UIUtil.dispatchAllInvocationEvents();
			assertEquals( 1, notices.size() );
			var	failed	= notices.getFirst();
			var	retry	= ( NotificationAction ) failed.getActions().getFirst();
			retry.actionPerformed( AnActionEvent.createFromAnAction( retry, null, "test", DataContext.EMPTY_CONTEXT ), failed );
			PlatformTestUtil.waitWithEventsDispatching( "explicit retry", () -> notices.size() == 2, 10 );
			assertTrue( failed.isExpired() );
		} finally {
			settings.lspModulePath = previous;
			notices.forEach( Notification::expire );
		}
	}
}
