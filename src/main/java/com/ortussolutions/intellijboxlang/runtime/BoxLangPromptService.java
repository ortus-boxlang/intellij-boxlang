package com.ortussolutions.intellijboxlang.runtime;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;

public final class BoxLangPromptService {

	private static final String	NOTIFICATION_GROUP_ID	= "BoxLang";
	private static final long	TIMEOUT_SECONDS			= 60;

	private BoxLangPromptService() {
	}

	public static boolean confirmDownload( Project project, String title, String message ) {
		CompletableFuture<Boolean> result = new CompletableFuture<>();

		ApplicationManager.getApplication().invokeLater( () -> {
			Notification notification = NotificationGroupManager.getInstance()
			    .getNotificationGroup( NOTIFICATION_GROUP_ID )
			    .createNotification( title, message, NotificationType.INFORMATION );

			notification.addAction( new NotificationAction( "Download" ) {

				@Override
				public void actionPerformed( @NotNull AnActionEvent e, @NotNull Notification notification ) {
					notification.expire();
					result.complete( true );
				}
			} );

			notification.addAction( new NotificationAction( "Not Now" ) {

				@Override
				public void actionPerformed( @NotNull AnActionEvent e, @NotNull Notification notification ) {
					notification.expire();
					result.complete( false );
				}
			} );

			// If notification is closed without action, treat as declined
			notification.whenExpired( () -> {
				if ( !result.isDone() ) {
					result.complete( false );
				}
			} );

			notification.notify( project );
		} );

		try {
			return result.get( TIMEOUT_SECONDS, TimeUnit.SECONDS );
		} catch ( Exception e ) {
			return false;
		}
	}
}
