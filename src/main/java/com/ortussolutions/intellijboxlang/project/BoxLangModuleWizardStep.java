package com.ortussolutions.intellijboxlang.project;

import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import java.util.List;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.jetbrains.annotations.NotNull;

/**
 * Wizard step for configuring BoxLang project settings.
 */
public final class BoxLangModuleWizardStep extends ModuleWizardStep {

	private final BoxLangModuleBuilder					builder;
	private final JPanel								panel;
	private final ComboBox<GitHubTemplate>				templateTypeCombo;
	private final JBTextField							boxLangVersionField;
	private final JBLabel								templateDescriptionLabel;
	private final DefaultComboBoxModel<GitHubTemplate>	comboModel;

	public BoxLangModuleWizardStep( @NotNull BoxLangModuleBuilder builder ) {
		this.builder				= builder;

		comboModel					= new DefaultComboBoxModel<>();
		templateTypeCombo			= new ComboBox<>( comboModel );
		boxLangVersionField			= new JBTextField();
		templateDescriptionLabel	= new JBLabel();

		// Load initial templates from cache
		loadTemplates( GitHubTemplateService.getInstance().getCachedTemplates() );

		// Set default values from builder
		boxLangVersionField.setText( builder.getBoxLangVersion() );
		updateTemplateDescription();

		// Update description when selection changes
		templateTypeCombo.addActionListener( e -> updateTemplateDescription() );

		// Build the panel
		panel = FormBuilder.createFormBuilder()
		    .addLabeledComponent( "Project Type:", templateTypeCombo )
		    .addComponentToRightColumn( templateDescriptionLabel )
		    .addLabeledComponent( "BoxLang Version:", boxLangVersionField )
		    .addTooltip( "Leave empty to use the latest version" )
		    .addComponentFillVertically( new JPanel(), 0 )
		    .getPanel();

		panel.setBorder( JBUI.Borders.empty( 10 ) );

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
			templateDescriptionLabel.setText( "<html><i>" + selected.getDescription() + "</i></html>" );
		}
	}

	@Override
	public JComponent getComponent() {
		return panel;
	}

	@Override
	public void updateDataModel() {
		GitHubTemplate selected = ( GitHubTemplate ) templateTypeCombo.getSelectedItem();
		builder.setSelectedTemplate( selected );
		builder.setBoxLangVersion( boxLangVersionField.getText().trim() );
	}

	@Override
	public boolean validate() {
		// No required validation - version can be empty (will use latest)
		return true;
	}
}
