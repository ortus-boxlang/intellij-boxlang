package com.ortussolutions.intellijboxlang.testbox;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessListener;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.charset.StandardCharsets;

/**
 * On project startup, checks if the project looks like it should have TestBox
 * (e.g., has test spec files) but TestBox is not installed.
 * If so, shows a notification offering to install it via CommandBox.
 */
public class TestBoxInstallNotifier implements ProjectActivity {

	@Override
	public @Nullable Object execute( @NotNull Project project, @NotNull Continuation<? super Unit> continuation ) {
		// Only check if TestBox is not already installed
		if ( TestBoxUtil.isTestBoxInstalled( project ) ) {
			return Unit.INSTANCE;
		}

		// Check if this project looks like it might need TestBox
		// (has a tests/ directory with BoxLang/CFML spec files)
		String basePath = project.getBasePath();
		if ( basePath == null ) {
			return Unit.INSTANCE;
		}

		File testsDir = new File( basePath, "tests" );
		if ( !testsDir.isDirectory() ) {
			return Unit.INSTANCE;
		}

		// Only show the notification if the tests/ directory contains BoxLang/CFML files.
		// This prevents false positives for non-BoxLang projects (Java, Python, etc.)
		// that also have a tests/ directory.
		if ( !containsBoxLangFiles( testsDir ) ) {
			return Unit.INSTANCE;
		}

		// Project has BoxLang test files but no testbox/ — offer to install
		showInstallNotification( project );
		return Unit.INSTANCE;
	}

	/**
	 * Recursively checks if a directory contains any BoxLang (.bx) or CFML (.cfc, .cfm) files.
	 * Limits recursion depth to avoid scanning excessively deep directory trees.
	 */
	private boolean containsBoxLangFiles( @NotNull File directory ) {
		return containsBoxLangFiles( directory, 0, 5 );
	}

	private boolean containsBoxLangFiles( @NotNull File directory, int depth, int maxDepth ) {
		if ( depth > maxDepth ) {
			return false;
		}

		File[] files = directory.listFiles();
		if ( files == null ) {
			return false;
		}

		for ( File file : files ) {
			if ( file.isFile() ) {
				String name = file.getName().toLowerCase( java.util.Locale.ROOT );
				if ( name.endsWith( ".bx" ) || name.endsWith( ".cfc" ) || name.endsWith( ".cfm" ) ) {
					return true;
				}
			} else if ( file.isDirectory() && !file.getName().startsWith( "." ) ) {
				if ( containsBoxLangFiles( file, depth + 1, maxDepth ) ) {
					return true;
				}
			}
		}

		return false;
	}

	private void showInstallNotification( @NotNull Project project ) {
		NotificationGroupManager.getInstance()
		    .getNotificationGroup( "BoxLang" )
		    .createNotification(
		        "TestBox Not Found",
		        "Your project has a tests/ directory but TestBox is not installed. "
		            + "Install it with CommandBox to enable test running.",
		        NotificationType.INFORMATION
		    )
		    .addAction( new com.intellij.notification.NotificationAction( "Install TestBox" ) {

			    @Override
			    public void actionPerformed(
			        @NotNull com.intellij.openapi.actionSystem.AnActionEvent e,
			        @NotNull com.intellij.notification.Notification notification ) {
				    installTestBox( project );
				    notification.expire();
			    }
		    } )
		    .notify( project );
	}

	private void installTestBox( @NotNull Project project ) {
		String basePath = project.getBasePath();
		if ( basePath == null ) {
			return;
		}

		try {
			GeneralCommandLine commandLine = new GeneralCommandLine( "box", "install", "testbox", "--saveDev" );
			commandLine.withWorkDirectory( basePath );
			commandLine.withCharset( StandardCharsets.UTF_8 );

			OSProcessHandler handler = new OSProcessHandler( commandLine );
			handler.addProcessListener( new ProcessListener() {

				@Override
				public void processTerminated( @NotNull ProcessEvent event ) {
					int exitCode = event.getExitCode();
					if ( exitCode == 0 ) {
						NotificationGroupManager.getInstance()
						    .getNotificationGroup( "BoxLang" )
						    .createNotification(
						        "TestBox Installed",
						        "TestBox has been installed successfully. You can now run tests from the gutter icons.",
						        NotificationType.INFORMATION
						    )
						    .notify( project );
					} else {
						NotificationGroupManager.getInstance()
						    .getNotificationGroup( "BoxLang" )
						    .createNotification(
						        "TestBox Installation Failed",
						        "Failed to install TestBox (exit code: " + exitCode + "). "
						            + "Please install manually with: box install testbox --saveDev",
						        NotificationType.ERROR
						    )
						    .notify( project );
					}
				}
			} );

			handler.startNotify();
		} catch ( Exception e ) {
			NotificationGroupManager.getInstance()
			    .getNotificationGroup( "BoxLang" )
			    .createNotification(
			        "TestBox Installation Failed",
			        "Could not run CommandBox. Please ensure 'box' is in your PATH, "
			            + "or install TestBox manually with: box install testbox --saveDev",
			        NotificationType.ERROR
			    )
			    .notify( project );
		}
	}
}
