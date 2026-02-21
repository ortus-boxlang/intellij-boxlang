package com.ortussolutions.intellijboxlang.run;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Editor UI for the BoxLang attach run configuration.
 * Shows fields for Host, JDWP Port, Local Root, Remote Root, and BoxLang Home.
 */
public class BoxLangAttachRunConfigurationEditor extends SettingsEditor<BoxLangAttachRunConfiguration> {

	private final JPanel					panel;
	private final JBTextField				hostField;
	private final JBTextField				jdwpPortField;
	private final TextFieldWithBrowseButton	localRootField;
	private final TextFieldWithBrowseButton	remoteRootField;
	private final TextFieldWithBrowseButton	boxLangHomeField;

	public BoxLangAttachRunConfigurationEditor( Project project ) {
		hostField		= new JBTextField( "localhost" );

		jdwpPortField	= new JBTextField( "5005" );

		localRootField	= new TextFieldWithBrowseButton();
		FileChooserDescriptor localRootDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
		    .withTitle( "Select Local Root" )
		    .withDescription( "Select the local directory containing BoxLang source files" );
		localRootField.addBrowseFolderListener( new TextBrowseFolderListener( localRootDescriptor, project ) );

		remoteRootField = new TextFieldWithBrowseButton();
		FileChooserDescriptor remoteRootDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
		    .withTitle( "Select Remote Root" )
		    .withDescription( "Select the remote directory where BoxLang files are served from" );
		remoteRootField.addBrowseFolderListener( new TextBrowseFolderListener( remoteRootDescriptor, project ) );

		boxLangHomeField = new TextFieldWithBrowseButton();
		FileChooserDescriptor boxLangHomeDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
		    .withTitle( "Select BoxLang Home" )
		    .withDescription( "Select the BoxLang home directory (leave empty to use default)" );
		boxLangHomeField.addBrowseFolderListener( new TextBrowseFolderListener( boxLangHomeDescriptor, project ) );

		panel = FormBuilder.createFormBuilder()
		    .addLabeledComponent( new JBLabel( "Host:" ), hostField, 1, false )
		    .addLabeledComponent( new JBLabel( "JDWP port:" ), jdwpPortField, 1, false )
		    .addLabeledComponent( new JBLabel( "Local root:" ), localRootField, 1, false )
		    .addLabeledComponent( new JBLabel( "Remote root:" ), remoteRootField, 1, false )
		    .addLabeledComponent( new JBLabel( "BoxLang home:" ), boxLangHomeField, 1, false )
		    .addComponentFillVertically( new JPanel(), 0 )
		    .getPanel();
	}

	@Override
	protected void resetEditorFrom( @NotNull BoxLangAttachRunConfiguration configuration ) {
		hostField.setText( configuration.getHost() != null ? configuration.getHost() : "localhost" );
		jdwpPortField.setText( String.valueOf( configuration.getJdwpPort() ) );
		localRootField.setText( configuration.getLocalRoot() != null ? configuration.getLocalRoot() : "" );
		remoteRootField.setText( configuration.getRemoteRoot() != null ? configuration.getRemoteRoot() : "" );
		boxLangHomeField.setText( configuration.getBoxLangHome() != null ? configuration.getBoxLangHome() : "" );
	}

	@Override
	protected void applyEditorTo( @NotNull BoxLangAttachRunConfiguration configuration ) {
		configuration.setHost( hostField.getText() );
		try {
			configuration.setJdwpPort( Integer.parseInt( jdwpPortField.getText().trim() ) );
		} catch ( NumberFormatException e ) {
			// Leave the current value; validation will catch the error
		}
		configuration.setLocalRoot( localRootField.getText() );
		configuration.setRemoteRoot( remoteRootField.getText() );
		configuration.setBoxLangHome( boxLangHomeField.getText() );
	}

	@Override
	protected @NotNull JComponent createEditor() {
		return panel;
	}
}
