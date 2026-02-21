package com.ortussolutions.intellijboxlang.settings;

import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import java.util.Objects;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

public final class BoxLangSettingsForm {

	private final JPanel		panel;
	private final JBTextField	boxLangVersionField			= new JBTextField();
	private final JBTextField	boxLangJarPathField			= new JBTextField();
	private final JBTextField	boxLangHomeField			= new JBTextField();
	private final JBTextField	javaHomeField				= new JBTextField();
	private final JBTextField	lspVersionField				= new JBTextField();
	private final JBTextField	lspBoxLangVersionField		= new JBTextField();
	private final JBTextField	lspBoxLangHomeField			= new JBTextField();
	private final JBTextField	lspModulesField				= new JBTextField();
	private final JBTextField	lspJvmArgsField				= new JBTextField();
	private final JSpinner		lspMaxHeapSizeSpinner		= new JSpinner( new SpinnerNumberModel( 512, 64, 8192, 64 ) );
	private final JBTextField	debuggerJarPathField		= new JBTextField();
	private final JBCheckBox	useBvmrcCheckBox			= new JBCheckBox( "Use .bvmrc for BoxLang version" );
	private final JBCheckBox	promptForDownloadsCheckBox	= new JBCheckBox( "Prompt before downloading BoxLang/LSP" );

	public BoxLangSettingsForm() {
		panel = FormBuilder.createFormBuilder()
		    .addLabeledComponent( "BoxLang Version", boxLangVersionField )
		    .addLabeledComponent( "BoxLang Jar Path", boxLangJarPathField )
		    .addLabeledComponent( "BoxLang Home", boxLangHomeField )
		    .addLabeledComponent( "Java Home", javaHomeField )
		    .addSeparator()
		    .addLabeledComponent( "LSP Version", lspVersionField )
		    .addLabeledComponent( "LSP BoxLang Version", lspBoxLangVersionField )
		    .addLabeledComponent( "LSP BoxLang Home", lspBoxLangHomeField )
		    .addLabeledComponent( "LSP Modules", lspModulesField )
		    .addLabeledComponent( "LSP JVM Args", lspJvmArgsField )
		    .addLabeledComponent( "LSP Max Heap (MB)", lspMaxHeapSizeSpinner )
		    .addSeparator()
		    .addLabeledComponent( "Debugger Jar Path", debuggerJarPathField )
		    .addSeparator()
		    .addComponent( useBvmrcCheckBox )
		    .addComponent( promptForDownloadsCheckBox )
		    .getPanel();
	}

	public JComponent getPanel() {
		return panel;
	}

	public void setEnabled( boolean enabled ) {
		boxLangVersionField.setEnabled( enabled );
		boxLangJarPathField.setEnabled( enabled );
		boxLangHomeField.setEnabled( enabled );
		javaHomeField.setEnabled( enabled );
		lspVersionField.setEnabled( enabled );
		lspBoxLangVersionField.setEnabled( enabled );
		lspBoxLangHomeField.setEnabled( enabled );
		lspModulesField.setEnabled( enabled );
		lspJvmArgsField.setEnabled( enabled );
		lspMaxHeapSizeSpinner.setEnabled( enabled );
		debuggerJarPathField.setEnabled( enabled );
		useBvmrcCheckBox.setEnabled( enabled );
		promptForDownloadsCheckBox.setEnabled( enabled );
	}

	public void reset( BoxLangSettingsState state ) {
		boxLangVersionField.setText( nullToEmpty( state.boxLangVersion ) );
		boxLangJarPathField.setText( nullToEmpty( state.boxLangJarPath ) );
		boxLangHomeField.setText( nullToEmpty( state.boxLangHome ) );
		javaHomeField.setText( nullToEmpty( state.javaHome ) );
		lspVersionField.setText( nullToEmpty( state.lspVersion ) );
		lspBoxLangVersionField.setText( nullToEmpty( state.lspBoxLangVersion ) );
		lspBoxLangHomeField.setText( nullToEmpty( state.lspBoxLangHome ) );
		lspModulesField.setText( nullToEmpty( state.lspModules ) );
		lspJvmArgsField.setText( nullToEmpty( state.lspJvmArgs ) );
		lspMaxHeapSizeSpinner.setValue( state.lspMaxHeapSize );
		debuggerJarPathField.setText( nullToEmpty( state.debuggerJarPath ) );
		useBvmrcCheckBox.setSelected( state.useBvmrc );
		promptForDownloadsCheckBox.setSelected( state.promptForDownloads );
	}

	public void apply( BoxLangSettingsState state ) {
		state.boxLangVersion		= emptyToNull( boxLangVersionField.getText() );
		state.boxLangJarPath		= emptyToNull( boxLangJarPathField.getText() );
		state.boxLangHome			= emptyToNull( boxLangHomeField.getText() );
		state.javaHome				= emptyToNull( javaHomeField.getText() );
		state.lspVersion			= emptyToNull( lspVersionField.getText() );
		state.lspBoxLangVersion		= emptyToNull( lspBoxLangVersionField.getText() );
		state.lspBoxLangHome		= emptyToNull( lspBoxLangHomeField.getText() );
		state.lspModules			= emptyToNull( lspModulesField.getText() );
		state.lspJvmArgs			= emptyToNull( lspJvmArgsField.getText() );
		state.lspMaxHeapSize		= ( ( Number ) lspMaxHeapSizeSpinner.getValue() ).intValue();
		state.debuggerJarPath		= emptyToNull( debuggerJarPathField.getText() );
		state.useBvmrc				= useBvmrcCheckBox.isSelected();
		state.promptForDownloads	= promptForDownloadsCheckBox.isSelected();
	}

	public boolean isModified( BoxLangSettingsState state ) {
		return !Objects.equals( state.boxLangVersion, emptyToNull( boxLangVersionField.getText() ) )
		    || !Objects.equals( state.boxLangJarPath, emptyToNull( boxLangJarPathField.getText() ) )
		    || !Objects.equals( state.boxLangHome, emptyToNull( boxLangHomeField.getText() ) )
		    || !Objects.equals( state.javaHome, emptyToNull( javaHomeField.getText() ) )
		    || !Objects.equals( state.lspVersion, emptyToNull( lspVersionField.getText() ) )
		    || !Objects.equals( state.lspBoxLangVersion, emptyToNull( lspBoxLangVersionField.getText() ) )
		    || !Objects.equals( state.lspBoxLangHome, emptyToNull( lspBoxLangHomeField.getText() ) )
		    || !Objects.equals( state.lspModules, emptyToNull( lspModulesField.getText() ) )
		    || !Objects.equals( state.lspJvmArgs, emptyToNull( lspJvmArgsField.getText() ) )
		    || state.lspMaxHeapSize != ( ( Number ) lspMaxHeapSizeSpinner.getValue() ).intValue()
		    || !Objects.equals( state.debuggerJarPath, emptyToNull( debuggerJarPathField.getText() ) )
		    || state.useBvmrc != useBvmrcCheckBox.isSelected()
		    || state.promptForDownloads != promptForDownloadsCheckBox.isSelected();
	}

	private String emptyToNull( String value ) {
		if ( value == null ) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private String nullToEmpty( String value ) {
		return value == null ? "" : value;
	}
}
