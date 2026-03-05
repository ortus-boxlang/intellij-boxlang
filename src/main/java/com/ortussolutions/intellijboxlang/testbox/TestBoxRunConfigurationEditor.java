package com.ortussolutions.intellijboxlang.testbox;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.RawCommandLineEditor;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * Editor UI for the TestBox run configuration settings panel.
 */
public class TestBoxRunConfigurationEditor extends SettingsEditor<TestBoxRunConfiguration> {

	private static final String				SCOPE_BUNDLE	= "BUNDLE";
	private static final String				SCOPE_DIRECTORY	= "DIRECTORY";
	private static final String				SCOPE_ALL		= "ALL";

	private final JPanel					panel;
	private final ComboBox<String>			testScopeCombo;
	private final JBTextField				bundlePathField;
	private final JBLabel					bundlePathLabel;
	private final JBTextField				directoryField;
	private final JBLabel					directoryLabel;
	private final JBTextField				filterSpecsField;
	private final JBTextField				filterSuitesField;
	private final JBTextField				labelsField;
	private final JBTextField				excludesField;
	private final JBCheckBox				verboseCheckbox;
	private final JBCheckBox				eagerFailureCheckbox;
	private final TextFieldWithBrowseButton	workingDirectoryField;
	private final TextFieldWithBrowseButton	boxLangHomeField;
	private final RawCommandLineEditor		jvmArgsField;
	private final RawCommandLineEditor		environmentVariablesField;

	public TestBoxRunConfigurationEditor( Project project ) {
		testScopeCombo = new ComboBox<>( new String[] { SCOPE_BUNDLE, SCOPE_DIRECTORY, SCOPE_ALL } );
		testScopeCombo.addActionListener( e -> updateFieldVisibility() );

		bundlePathLabel	= new JBLabel( "Bundle path:" );
		bundlePathField	= new JBTextField();
		bundlePathField.getEmptyText().setText( "e.g., tests.specs.MySpec" );

		directoryLabel	= new JBLabel( "Directory:" );
		directoryField	= new JBTextField();
		directoryField.getEmptyText().setText( "e.g., tests.specs" );

		filterSpecsField = new JBTextField();
		filterSpecsField.getEmptyText().setText( "Comma-separated spec names" );

		filterSuitesField = new JBTextField();
		filterSuitesField.getEmptyText().setText( "Comma-separated suite names" );

		labelsField = new JBTextField();
		labelsField.getEmptyText().setText( "Comma-separated labels to include" );

		excludesField = new JBTextField();
		excludesField.getEmptyText().setText( "Comma-separated labels to exclude" );

		verboseCheckbox			= new JBCheckBox( "Verbose output (stream progress)" );
		eagerFailureCheckbox	= new JBCheckBox( "Fail fast (stop on first failure)" );

		workingDirectoryField	= new TextFieldWithBrowseButton();
		FileChooserDescriptor workingDirDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
		    .withTitle( "Select Working Directory" )
		    .withDescription( "Select the working directory for test execution" );
		workingDirectoryField.addBrowseFolderListener( new TextBrowseFolderListener( workingDirDescriptor, project ) );

		boxLangHomeField = new TextFieldWithBrowseButton();
		FileChooserDescriptor boxLangHomeDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
		    .withTitle( "Select BoxLang Home" )
		    .withDescription( "Select the BoxLang home directory (leave empty to use default)" );
		boxLangHomeField.addBrowseFolderListener( new TextBrowseFolderListener( boxLangHomeDescriptor, project ) );

		jvmArgsField				= new RawCommandLineEditor();
		environmentVariablesField	= new RawCommandLineEditor();

		panel						= FormBuilder.createFormBuilder()
		    .addLabeledComponent( new JBLabel( "Test scope:" ), testScopeCombo, 1, false )
		    .addLabeledComponent( bundlePathLabel, bundlePathField, 1, false )
		    .addLabeledComponent( directoryLabel, directoryField, 1, false )
		    .addSeparator( 4 )
		    .addLabeledComponent( new JBLabel( "Filter specs:" ), filterSpecsField, 1, false )
		    .addLabeledComponent( new JBLabel( "Filter suites:" ), filterSuitesField, 1, false )
		    .addLabeledComponent( new JBLabel( "Labels:" ), labelsField, 1, false )
		    .addLabeledComponent( new JBLabel( "Excludes:" ), excludesField, 1, false )
		    .addSeparator( 4 )
		    .addComponent( verboseCheckbox )
		    .addComponent( eagerFailureCheckbox )
		    .addSeparator( 4 )
		    .addLabeledComponent( new JBLabel( "Working directory:" ), workingDirectoryField, 1, false )
		    .addLabeledComponent( new JBLabel( "BoxLang home:" ), boxLangHomeField, 1, false )
		    .addLabeledComponent( new JBLabel( "JVM arguments:" ), jvmArgsField, 1, false )
		    .addLabeledComponent( new JBLabel( "Environment variables:" ), environmentVariablesField, 1, false )
		    .addComponentFillVertically( new JPanel(), 0 )
		    .getPanel();
	}

	private void updateFieldVisibility() {
		String scope = ( String ) testScopeCombo.getSelectedItem();
		bundlePathLabel.setVisible( SCOPE_BUNDLE.equals( scope ) );
		bundlePathField.setVisible( SCOPE_BUNDLE.equals( scope ) );
		directoryLabel.setVisible( SCOPE_DIRECTORY.equals( scope ) );
		directoryField.setVisible( SCOPE_DIRECTORY.equals( scope ) );
	}

	@Override
	protected void resetEditorFrom( @NotNull TestBoxRunConfiguration configuration ) {
		testScopeCombo.setSelectedItem( configuration.getTestScope() != null ? configuration.getTestScope() : SCOPE_BUNDLE );
		bundlePathField.setText( configuration.getBundlePath() != null ? configuration.getBundlePath() : "" );
		directoryField.setText( configuration.getDirectory() != null ? configuration.getDirectory() : "" );
		filterSpecsField.setText( configuration.getFilterSpecs() != null ? configuration.getFilterSpecs() : "" );
		filterSuitesField.setText( configuration.getFilterSuites() != null ? configuration.getFilterSuites() : "" );
		labelsField.setText( configuration.getLabels() != null ? configuration.getLabels() : "" );
		excludesField.setText( configuration.getExcludes() != null ? configuration.getExcludes() : "" );
		verboseCheckbox.setSelected( configuration.isVerbose() );
		eagerFailureCheckbox.setSelected( configuration.isEagerFailure() );
		workingDirectoryField.setText( configuration.getWorkingDirectory() != null ? configuration.getWorkingDirectory() : "" );
		boxLangHomeField.setText( configuration.getBoxLangHome() != null ? configuration.getBoxLangHome() : "" );
		jvmArgsField.setText( configuration.getJvmArgs() != null ? configuration.getJvmArgs() : "" );
		environmentVariablesField.setText( configuration.getEnvironmentVariables() != null ? configuration.getEnvironmentVariables() : "" );
		updateFieldVisibility();
	}

	@Override
	protected void applyEditorTo( @NotNull TestBoxRunConfiguration configuration ) {
		configuration.setTestScope( ( String ) testScopeCombo.getSelectedItem() );
		configuration.setBundlePath( bundlePathField.getText() );
		configuration.setDirectory( directoryField.getText() );
		configuration.setFilterSpecs( filterSpecsField.getText() );
		configuration.setFilterSuites( filterSuitesField.getText() );
		configuration.setLabels( labelsField.getText() );
		configuration.setExcludes( excludesField.getText() );
		configuration.setVerbose( verboseCheckbox.isSelected() );
		configuration.setEagerFailure( eagerFailureCheckbox.isSelected() );
		configuration.setWorkingDirectory( workingDirectoryField.getText() );
		configuration.setBoxLangHome( boxLangHomeField.getText() );
		configuration.setJvmArgs( jvmArgsField.getText() );
		configuration.setEnvironmentVariables( environmentVariablesField.getText() );
	}

	@Override
	protected @NotNull JComponent createEditor() {
		return panel;
	}
}
