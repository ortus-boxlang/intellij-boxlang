package com.ortussolutions.intellijboxlang.project;

import com.intellij.ide.util.projectWizard.SettingsStep;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.platform.ProjectGeneratorPeer;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import java.awt.BorderLayout;
import java.util.List;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * UI panel for the BoxLang New Project wizard step.
 */
public final class BoxLangProjectGeneratorPeer implements ProjectGeneratorPeer<BoxLangProjectSettings> {

	private final JPanel								panel;
	private final ComboBox<GitHubTemplate>				templateTypeCombo;
	private final JBTextField							boxLangVersionField;
	private final JBLabel								templateDescriptionLabel;
	private final DefaultComboBoxModel<GitHubTemplate>	comboModel;

	public BoxLangProjectGeneratorPeer() {
		comboModel					= new DefaultComboBoxModel<>();
		templateTypeCombo			= new ComboBox<>( comboModel );
		boxLangVersionField			= new JBTextField();
		templateDescriptionLabel	= new JBLabel();

		// Load initial templates from cache
		loadTemplates( GitHubTemplateService.getInstance().getCachedTemplates() );
		updateTemplateDescription();

		// Update description when selection changes
		templateTypeCombo.addActionListener( e -> updateTemplateDescription() );

		// Build the panel
		JPanel descriptionPanel = new JPanel( new BorderLayout() );
		descriptionPanel.add( templateDescriptionLabel, BorderLayout.WEST );

		panel = FormBuilder.createFormBuilder()
		    .addLabeledComponent( "Project Type:", templateTypeCombo )
		    .addComponentToRightColumn( descriptionPanel )
		    .addLabeledComponent( "BoxLang Version:", boxLangVersionField )
		    .addTooltip( "Leave empty to use the latest version" )
		    .getPanel();

		// Fetch templates from GitHub in background
		fetchTemplatesInBackground();
	}

	private void loadTemplates( List<GitHubTemplate> templates ) {
		GitHubTemplate currentSelection = ( GitHubTemplate ) comboModel.getSelectedItem();
		comboModel.removeAllElements();
		for ( GitHubTemplate template : templates ) {
			comboModel.addElement( template );
		}
		// Restore selection if possible
		if ( currentSelection != null ) {
			for ( int i = 0; i < comboModel.getSize(); i++ ) {
				if ( comboModel.getElementAt( i ).getName().equals( currentSelection.getName() ) ) {
					comboModel.setSelectedItem( comboModel.getElementAt( i ) );
					break;
				}
			}
		}
		// If nothing selected, select first item
		if ( comboModel.getSelectedItem() == null && comboModel.getSize() > 0 ) {
			comboModel.setSelectedItem( comboModel.getElementAt( 0 ) );
		}
	}

	private void fetchTemplatesInBackground() {
		ApplicationManager.getApplication().executeOnPooledThread( () -> {
			List<GitHubTemplate> templates = GitHubTemplateService.getInstance().fetchTemplatesSync();
			SwingUtilities.invokeLater( () -> loadTemplates( templates ) );
		} );
	}

	private void updateTemplateDescription() {
		GitHubTemplate selected = ( GitHubTemplate ) templateTypeCombo.getSelectedItem();
		if ( selected != null ) {
			templateDescriptionLabel.setText( selected.getDescription() );
		}
	}

	@Override
	public @NotNull JComponent getComponent() {
		return panel;
	}

	@Override
	public void buildUI( @NotNull SettingsStep settingsStep ) {
		settingsStep.addSettingsField( "Project Type:", templateTypeCombo );
		settingsStep.addSettingsField( "BoxLang Version:", boxLangVersionField );
	}

	@Override
	public @NotNull BoxLangProjectSettings getSettings() {
		BoxLangProjectSettings settings = new BoxLangProjectSettings();
		settings.setSelectedTemplate( ( GitHubTemplate ) templateTypeCombo.getSelectedItem() );
		settings.setBoxLangVersion( boxLangVersionField.getText().trim() );
		return settings;
	}

	@Override
	public @Nullable ValidationInfo validate() {
		// No required validation - version can be empty (will use latest)
		return null;
	}

	@Override
	public boolean isBackgroundJobRunning() {
		return false;
	}
}
