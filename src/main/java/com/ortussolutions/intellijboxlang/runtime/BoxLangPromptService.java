package com.ortussolutions.intellijboxlang.runtime;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.ortussolutions.intellijboxlang.settings.VersionPickerDialog;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class BoxLangPromptService {

	private static final String	NOTIFICATION_GROUP_ID	= "BoxLang";
	private static final long	TIMEOUT_SECONDS			= 60;

	private BoxLangPromptService() {
	}

	/**
	 * Functional interface for fetching available versions.
	 */
	@FunctionalInterface
	public interface VersionFetcher {

		List<String> fetch() throws IOException;
	}

	/**
	 * Functional interface for performing a download given a selected version.
	 */
	@FunctionalInterface
	public interface DownloadAction {

		void download( String version, ProgressIndicator indicator ) throws IOException;
	}

	/**
	 * Shows a balloon notification. If the user clicks "Download", shows a version picker
	 * dialog and then runs the download as a background task with progress.
	 * Returns immediately - does not block.
	 */
	public static void promptAndDownload(
	    Project project,
	    String title,
	    String message,
	    String moduleName,
	    VersionFetcher versionFetcher,
	    DownloadAction downloadAction ) {

		ApplicationManager.getApplication().invokeLater( () -> {
			Notification notification = NotificationGroupManager.getInstance()
			    .getNotificationGroup( NOTIFICATION_GROUP_ID )
			    .createNotification( title, message, NotificationType.INFORMATION );

			notification.addAction( new NotificationAction( "Download" ) {

				@Override
				public void actionPerformed( @NotNull AnActionEvent e, @NotNull Notification notification ) {
					notification.expire();
					// Fetch versions in background, then show picker on EDT
					ApplicationManager.getApplication().executeOnPooledThread( () -> {
						try {
							List<String> versions = versionFetcher.fetch();
							ApplicationManager.getApplication().invokeLater( () -> {
								String selected = VersionPickerDialog.showAndGetVersion( project, moduleName, versions );
								if ( selected != null ) {
									// Run download as a background task with progress
									ProgressManager.getInstance().run( new Task.Backgroundable( project, "Downloading " + moduleName, true ) {

										@Override
										public void run( @NotNull ProgressIndicator indicator ) {
											try {
												downloadAction.download( selected, indicator );
											} catch ( IOException ex ) {
												// Logged by the caller
											}
										}
									} );
								}
							} );
						} catch ( IOException ex ) {
							// Version fetch failed - silently ignore
						}
					} );
				}
			} );

			notification.addAction( new NotificationAction( "Not Now" ) {

				@Override
				public void actionPerformed( @NotNull AnActionEvent e, @NotNull Notification notification ) {
					notification.expire();
				}
			} );

			notification.notify( project );
		} );
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
