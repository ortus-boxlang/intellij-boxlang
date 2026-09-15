package com.ortussolutions.intellijboxlang.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.ortussolutions.intellijboxlang.lsp.BoxLangLspClientService;
import com.ortussolutions.intellijboxlang.runtime.BoxLangDiagnosticReport;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.datatransfer.StringSelection;
import java.util.function.Consumer;
import javax.swing.*;
import org.jetbrains.annotations.NotNull;

public final class BoxLangToolingStatusAction extends AnAction implements DumbAware {

	@Override
	public @NotNull ActionUpdateThread getActionUpdateThread() {
		return ActionUpdateThread.BGT;
	}

	@Override
	public void update( @NotNull AnActionEvent event ) {
		event.getPresentation().setEnabledAndVisible( event.getProject() != null );
	}

	@Override
	public void actionPerformed( @NotNull AnActionEvent event ) {
		Project project = event.getProject();
		if ( project != null )
			snapshot( project, text -> new ReportDialog( project, text ).show() );
	}

	private static void snapshot( Project project, Consumer<String> ready ) {
		ProgressManager.getInstance().run( new Task.Backgroundable( project, "Reading BoxLang tooling status", false ) {

			private String report;

			@Override
			public void run( @NotNull ProgressIndicator indicator ) {
				report = BoxLangDiagnosticReport.collect( project );
			}

			@Override
			public void onSuccess() {
				if ( !project.isDisposed() )
					ready.accept( report );
			}
		} );
	}

	static final class ReportDialog extends DialogWrapper {

		private final Project		project;
		private final JBTextArea	preview	= new JBTextArea();

		ReportDialog( Project project, String report ) {
			super( project );
			this.project = project;
			setTitle( "BoxLang Tooling Status" );
			preview.setText( report );
			preview.setCaretPosition( 0 );
			setOKButtonText( "Close" );
			init();
		}

		@Override
		protected JComponent createCenterPanel() {
			JPanel panel = new JPanel( new BorderLayout( 0, 8 ) );
			panel.add( new JLabel( "Review and edit the report before copying. Nothing is sent automatically." ), BorderLayout.NORTH );
			JBScrollPane scroll = new JBScrollPane( preview );
			scroll.setPreferredSize( new Dimension( 850, 550 ) );
			panel.add( scroll, BorderLayout.CENTER );
			return panel;
		}

		@Override
		protected Action @NotNull [] createActions() {
			return new Action[] {
			    new AbstractAction( "Copy Report" ) {

				    @Override
				    public void actionPerformed( ActionEvent e ) {
					    CopyPasteManager.getInstance().setContents( new StringSelection( preview.getText() ) );
				    }
			    },
			    new AbstractAction( "Refresh" ) {

				    @Override
				    public void actionPerformed( ActionEvent e ) {
					    snapshot( project, text -> {
						    if ( !isDisposed() ) {
							    preview.setText( text );
							    preview.setCaretPosition( 0 );
						    }
					    } );
				    }
			    },
			    new AbstractAction( "Retry Language Server" ) {

				    @Override
				    public void actionPerformed( ActionEvent e ) {
					    BoxLangLspClientService.getInstance( project ).retryStartup();
				    }
			    },
			    new AbstractAction( "BoxLang Settings" ) {

				    @Override
				    public void actionPerformed( ActionEvent e ) {
					    ShowSettingsUtil.getInstance().showSettingsDialog( project,
					        com.ortussolutions.intellijboxlang.settings.BoxLangApplicationConfigurable.class );
				    }
			    }, getOKAction()
			};
		}
	}
}
