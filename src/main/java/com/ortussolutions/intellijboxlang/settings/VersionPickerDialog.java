package com.ortussolutions.intellijboxlang.settings;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.FormBuilder;
import java.awt.event.ItemEvent;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Dialog for selecting a module version to download.
 */
public class VersionPickerDialog extends DialogWrapper {

	private final String			moduleName;
	private final List<String>		versions;
	private final ComboBox<String>	versionComboBox;
	private String					selectedVersion;

	public VersionPickerDialog( @Nullable Project project, @NotNull String moduleName, @NotNull List<String> versions ) {
		super( project );
		this.moduleName	= moduleName;
		this.versions	= versions;

		setTitle( "Download " + moduleName );

		versionComboBox = new ComboBox<>( versions.toArray( new String[ 0 ] ) );
		if ( !versions.isEmpty() ) {
			versionComboBox.setSelectedIndex( 0 ); // Select latest by default
			selectedVersion = versions.get( 0 );
		}

		versionComboBox.addItemListener( e -> {
			if ( e.getStateChange() == ItemEvent.SELECTED ) {
				selectedVersion = ( String ) e.getItem();
			}
		} );

		init();
	}

	@Override
	protected @Nullable JComponent createCenterPanel() {
		JBLabel	infoLabel	= new JBLabel( "Select a version to download:" );

		JPanel	panel		= FormBuilder.createFormBuilder()
		    .addComponent( infoLabel )
		    .addLabeledComponent( "Version:", versionComboBox )
		    .getPanel();

		return panel;
	}

	/**
	 * Returns the selected version, or null if dialog was cancelled.
	 */
	@Nullable
	public String getSelectedVersion() {
		return selectedVersion;
	}

	/**
	 * Shows the dialog and returns the selected version, or null if cancelled.
	 */
	@Nullable
	public static String showAndGetVersion( @Nullable Project project, @NotNull String moduleName, @NotNull List<String> versions ) {
		if ( versions.isEmpty() ) {
			return null;
		}

		VersionPickerDialog dialog = new VersionPickerDialog( project, moduleName, versions );
		if ( dialog.showAndGet() ) {
			return dialog.getSelectedVersion();
		}
		return null;
	}
}
