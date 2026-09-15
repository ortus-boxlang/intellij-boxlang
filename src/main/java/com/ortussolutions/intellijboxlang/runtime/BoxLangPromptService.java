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
import org.jetbrains.annotations.NotNull;

public final class BoxLangPromptService {

	private static final String NOTIFICATION_GROUP_ID = "BoxLang";

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
									BoxLangSetupTasks.download( project, moduleName,
									    indicator -> downloadAction.download( selected, indicator ),
									    () -> {
										    if ( project != null && !project.isDisposed() )
											    com.ortussolutions.intellijboxlang.lsp.BoxLangLspClientService.getInstance( project ).retryStartup();
									    } );
								}
							} );
						} catch ( IOException ex ) {
							BoxLangSetupTasks.reportFailure( project, "Fetching " + moduleName + " versions", ex,
							    () -> promptAndDownload( project, title, message, moduleName, versionFetcher, downloadAction ) );
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

	public static void promptDownload( Project project, String title, String message, Runnable download ) {
		ApplicationManager.getApplication().invokeLater( () -> {
			if ( project != null && project.isDisposed() )
				return;
			CompletableFuture<Boolean>	result			= new CompletableFuture<>();
			Notification				notification	= createDownloadConfirmation( title, message, result );
			result.thenAccept( accepted -> {
				if ( accepted )
					download.run();
			} );
			notification.notify( project );
		} );
	}

	static Notification createDownloadConfirmation( String title, String message, CompletableFuture<Boolean> result ) {
		Notification notification = NotificationGroupManager.getInstance()
		    .getNotificationGroup( NOTIFICATION_GROUP_ID )
		    .createNotification( title, message, NotificationType.INFORMATION );

		notification.addAction( new NotificationAction( "Download" ) {

			@Override
			public void actionPerformed( @NotNull AnActionEvent e, @NotNull Notification notification ) {
				// Expiration invokes the dismissal callback synchronously. Record the
				// user's choice first so Download is not interpreted as a decline.
				result.complete( true );
				notification.expire();
			}
		} );

		notification.addAction( new NotificationAction( "Not Now" ) {

			@Override
			public void actionPerformed( @NotNull AnActionEvent e, @NotNull Notification notification ) {
				result.complete( false );
				notification.expire();
			}
		} );

		// If notification is closed without action, treat as declined
		notification.whenExpired( () -> {
			if ( !result.isDone() ) {
				result.complete( false );
			}
		} );
		result.whenComplete( ( accepted, failure ) -> notification.expire() );
		return notification;
	}

}
