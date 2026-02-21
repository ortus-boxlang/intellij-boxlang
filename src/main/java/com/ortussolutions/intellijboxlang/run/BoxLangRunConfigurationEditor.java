package com.ortussolutions.intellijboxlang.run;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.RawCommandLineEditor;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.FormBuilder;
import com.ortussolutions.intellijboxlang.file.BoxLangFileUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Editor UI for the BoxLang run configuration.
 */
public class BoxLangRunConfigurationEditor extends SettingsEditor<BoxLangRunConfiguration> {

	private final JPanel					panel;
	private final JBCheckBox				useCurrentFileCheckbox;
	private final TextFieldWithBrowseButton	scriptPathField;
	private final JBLabel					scriptPathLabel;
	private final TextFieldWithBrowseButton	workingDirectoryField;
	private final RawCommandLineEditor		programArgumentsField;
	private final RawCommandLineEditor		environmentVariablesField;
	private final TextFieldWithBrowseButton	boxLangHomeField;
	private final RawCommandLineEditor		jvmArgsField;

	public BoxLangRunConfigurationEditor( Project project ) {
		useCurrentFileCheckbox = new JBCheckBox( "Use current file" );
		useCurrentFileCheckbox.addActionListener( e -> updateScriptPathVisibility() );

		scriptPathLabel	= new JBLabel( "Script path:" );
		scriptPathField	= new TextFieldWithBrowseButton();
		FileChooserDescriptor scriptDescriptor = FileChooserDescriptorFactory.createSingleFileDescriptor()
		    .withFileFilter( BoxLangFileUtil::isBoxLangFile )
		    .withTitle( "Select BoxLang Script" )
		    .withDescription( "Select the BoxLang script file to run" );
		scriptPathField.addBrowseFolderListener( new TextBrowseFolderListener( scriptDescriptor, project ) );

		workingDirectoryField = new TextFieldWithBrowseButton();
		FileChooserDescriptor workingDirDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
		    .withTitle( "Select Working Directory" )
		    .withDescription( "Select the working directory for the script" );
		workingDirectoryField.addBrowseFolderListener( new TextBrowseFolderListener( workingDirDescriptor, project ) );

		programArgumentsField = new RawCommandLineEditor();
		programArgumentsField.setDialogCaption( "Program Arguments" );

		environmentVariablesField = new RawCommandLineEditor();
		environmentVariablesField.setDialogCaption( "Environment Variables" );

		boxLangHomeField = new TextFieldWithBrowseButton();
		FileChooserDescriptor boxLangHomeDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
		    .withTitle( "Select BoxLang Home" )
		    .withDescription( "Select the BoxLang home directory (leave empty to use default)" );
		boxLangHomeField.addBrowseFolderListener( new TextBrowseFolderListener( boxLangHomeDescriptor, project ) );

		jvmArgsField = new RawCommandLineEditor();
		jvmArgsField.setDialogCaption( "JVM Arguments" );

		panel = FormBuilder.createFormBuilder()
		    .addComponent( useCurrentFileCheckbox )
		    .addLabeledComponent( scriptPathLabel, scriptPathField, 1, false )
		    .addLabeledComponent( new JBLabel( "Working directory:" ), workingDirectoryField, 1, false )
		    .addLabeledComponent( new JBLabel( "Program arguments:" ), programArgumentsField, 1, false )
		    .addLabeledComponent( new JBLabel( "Environment variables:" ), environmentVariablesField, 1, false )
		    .addLabeledComponent( new JBLabel( "BoxLang home:" ), boxLangHomeField, 1, false )
		    .addLabeledComponent( new JBLabel( "JVM arguments:" ), jvmArgsField, 1, false )
		    .addComponentFillVertically( new JPanel(), 0 )
		    .getPanel();
	}

	private void updateScriptPathVisibility() {
		boolean useCurrentFile = useCurrentFileCheckbox.isSelected();
		scriptPathLabel.setVisible( !useCurrentFile );
		scriptPathField.setVisible( !useCurrentFile );
	}

	@Override
	protected void resetEditorFrom( @NotNull BoxLangRunConfiguration configuration ) {
		useCurrentFileCheckbox.setSelected( configuration.isUseCurrentFile() );
		scriptPathField.setText( configuration.getScriptPath() != null ? configuration.getScriptPath() : "" );
		workingDirectoryField.setText( configuration.getWorkingDirectory() != null ? configuration.getWorkingDirectory() : "" );
		programArgumentsField.setText( configuration.getProgramArguments() != null ? configuration.getProgramArguments() : "" );
		environmentVariablesField.setText( configuration.getEnvironmentVariables() != null ? configuration.getEnvironmentVariables() : "" );
		boxLangHomeField.setText( configuration.getBoxLangHome() != null ? configuration.getBoxLangHome() : "" );
		jvmArgsField.setText( configuration.getJvmArgs() != null ? configuration.getJvmArgs() : "" );
		updateScriptPathVisibility();
	}

	@Override
	protected void applyEditorTo( @NotNull BoxLangRunConfiguration configuration ) {
		configuration.setUseCurrentFile( useCurrentFileCheckbox.isSelected() );
		configuration.setScriptPath( scriptPathField.getText() );
		configuration.setWorkingDirectory( workingDirectoryField.getText() );
		configuration.setProgramArguments( programArgumentsField.getText() );
		configuration.setEnvironmentVariables( environmentVariablesField.getText() );
		configuration.setBoxLangHome( boxLangHomeField.getText() );
		configuration.setJvmArgs( jvmArgsField.getText() );
	}

	@Override
	protected @NotNull JComponent createEditor() {
		return panel;
	}
}
