package com.ortussolutions.intellijboxlang.runtime;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BoxLangPromptService {

	private BoxLangPromptService() {
	}

	public static boolean confirmDownload( Project project, String title, String message ) {
		if ( ApplicationManager.getApplication().isDispatchThread() ) {
			int choice = Messages.showYesNoDialog( project, message, title, Messages.getQuestionIcon() );
			return choice == Messages.YES;
		}
		AtomicBoolean result = new AtomicBoolean( false );
		ApplicationManager.getApplication().invokeAndWait( () -> {
			int choice = Messages.showYesNoDialog( project, message, title, Messages.getQuestionIcon() );
			result.set( choice == Messages.YES );
		} );
		return result.get();
	}
}
