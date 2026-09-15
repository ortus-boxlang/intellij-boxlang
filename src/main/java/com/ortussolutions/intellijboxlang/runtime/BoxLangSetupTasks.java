package com.ortussolutions.intellijboxlang.runtime;

import com.intellij.notification.*;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;

/** Runs user-approved setup with progress, visible failures, and an explicit retry. */
public final class BoxLangSetupTasks {

	@FunctionalInterface
	public interface Operation {

		void run( ProgressIndicator indicator ) throws IOException;
	}

	private static final Logger LOG = Logger.getInstance( BoxLangSetupTasks.class );

	private BoxLangSetupTasks() {
	}

	public static void download( Project project, String name, Operation operation, Runnable success ) {
		if ( project != null && project.isDisposed() )
			return;
		var status = BoxLangToolingStatus.get( project );
		if ( !status.beginDownload( name ) )
			return;
		ApplicationManager.getApplication().invokeLater( () -> {
			if ( project != null && project.isDisposed() )
				return;
			ProgressManager.getInstance().run( new Task.Backgroundable( project, "Downloading " + name, true ) {

				@Override
				public void run( @NotNull ProgressIndicator indicator ) {
					try {
						operation.run( indicator );
					} catch ( IOException e ) {
						throw new java.io.UncheckedIOException( e );
					}
				}

				@Override
				public void onSuccess() {
					status.record( name, BoxLangToolingStatus.Phase.INSTALLED, "Installation completed." );
					notice( name + " installed", "Installation completed. Check BoxLang Tooling Status for language-server readiness.",
					    NotificationType.INFORMATION ).notify( project );
					success.run();
				}

				@Override
				public void onCancel() {
					status.record( name, BoxLangToolingStatus.Phase.CANCELLED, "Download cancelled. Choose Retry to try again." );
					failureNotification( name, "Download cancelled. Choose Retry to try again.",
					    () -> download( project, name, operation, success ) ).notify( project );
				}

				@Override
				public void onThrowable( @NotNull Throwable error ) {
					LOG.warn( "Unable to install " + name, error );
					String detail = failureDetail( error );
					status.record( name, BoxLangToolingStatus.Phase.FAILED, detail );
					failureNotification( name, detail, () -> download( project, name, operation, success ) ).notify( project );
				}
			} );
		} );
	}

	public static void reportFailure( Project project, String name, Throwable error, Runnable retry ) {
		String detail = failureDetail( error );
		LOG.warn( name, error );
		BoxLangToolingStatus.get( project ).record( name, BoxLangToolingStatus.Phase.FAILED, detail );
		ApplicationManager.getApplication().invokeLater( () -> {
			if ( project == null || !project.isDisposed() )
				failureNotification( name, detail, retry ).notify( project );
		} );
	}

	static String failureDetail( Throwable error ) {
		if ( error instanceof java.io.UncheckedIOException )
			error = error.getCause();
		String reason = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
		return reason.substring( 0, Math.min( reason.length(), 500 ) )
		    + " Check the connection, available disk space, and BoxLang settings, then choose Retry.";
	}

	static Notification failureNotification( String name, String detail, Runnable retry ) {
		Notification notification = notice( name + " needs attention", detail, NotificationType.ERROR );
		notification.addAction( new NotificationAction( "Retry" ) {

			@Override
			public void actionPerformed( @NotNull AnActionEvent event, @NotNull Notification source ) {
				source.expire();
				retry.run();
			}
		} );
		return notification;
	}

	private static Notification notice( String title, String message, NotificationType type ) {
		return NotificationGroupManager.getInstance().getNotificationGroup( "BoxLang" )
		    .createNotification( title, StringUtil.escapeXmlEntities( message ), type );
	}
}
