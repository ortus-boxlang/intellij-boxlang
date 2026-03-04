package com.ortussolutions.intellijboxlang.settings;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import com.ortussolutions.intellijboxlang.runtime.InstalledModuleStatus;
import com.ortussolutions.intellijboxlang.runtime.ModuleStatusResolver;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import org.jetbrains.annotations.Nullable;

public final class BoxLangSettingsForm {

	private static final Logger LOG = Logger.getInstance( BoxLangSettingsForm.class );

	public interface DownloadListener {

		void onDownloadLsp();

		void onDownloadDebugger();
	}

	private final JPanel			panel;
	private final JBTextField		boxLangVersionField			= new JBTextField();
	private final JBTextField		boxLangJarPathField			= new JBTextField();
	private final JBTextField		boxLangHomeField			= new JBTextField();
	private final JBTextField		javaHomeField				= new JBTextField();
	private final JBTextField		lspBoxLangVersionField		= new JBTextField();
	private final JBTextField		lspBoxLangHomeField			= new JBTextField();
	private final JBTextField		lspModulesField				= new JBTextField();
	private final JBTextField		lspJvmArgsField				= new JBTextField();
	private final JSpinner			lspMaxHeapSizeSpinner		= new JSpinner( new SpinnerNumberModel( 512, 64, 8192, 64 ) );
	private final JBCheckBox		useBvmrcCheckBox			= new JBCheckBox( "Use .bvmrc for BoxLang version" );
	private final JBCheckBox		promptForDownloadsCheckBox	= new JBCheckBox( "Prompt before downloading BoxLang/LSP" );

	// Status labels and links
	private final JBLabel			lspStatusLabel				= new JBLabel();
	private final HyperlinkLabel	lspPathLink					= new HyperlinkLabel();
	private final HyperlinkLabel	lspDownloadLink				= new HyperlinkLabel( "Download" );
	private final JBLabel			debuggerStatusLabel			= new JBLabel();
	private final HyperlinkLabel	debuggerPathLink			= new HyperlinkLabel();
	private final HyperlinkLabel	debuggerDownloadLink		= new HyperlinkLabel( "Download" );
	private final JPanel			lspStatusPanel;
	private final JPanel			debuggerStatusPanel;

	// Store paths for hyperlink actions
	private Path					lspInstalledPath;
	private Path					debuggerInstalledPath;

	private DownloadListener		downloadListener;

	public BoxLangSettingsForm() {
		// Create status panels with label, path link, and download link
		lspStatusPanel		= createStatusPanel( lspStatusLabel, lspPathLink, lspDownloadLink );
		debuggerStatusPanel	= createStatusPanel( debuggerStatusLabel, debuggerPathLink, debuggerDownloadLink );

		// Set up path link actions (they will use stored paths)
		lspPathLink.addHyperlinkListener( e -> {
			if ( lspInstalledPath != null ) {
				openInFileBrowser( lspInstalledPath );
			}
		} );
		debuggerPathLink.addHyperlinkListener( e -> {
			if ( debuggerInstalledPath != null ) {
				openInFileBrowser( debuggerInstalledPath );
			}
		} );

		// Set up download link actions
		lspDownloadLink.addHyperlinkListener( e -> {
			if ( downloadListener != null ) {
				downloadListener.onDownloadLsp();
			}
		} );
		debuggerDownloadLink.addHyperlinkListener( e -> {
			if ( downloadListener != null ) {
				downloadListener.onDownloadDebugger();
			}
		} );

		panel = FormBuilder.createFormBuilder()
		    .addLabeledComponent( "BoxLang Version", boxLangVersionField )
		    .addLabeledComponent( "BoxLang Jar Path", boxLangJarPathField )
		    .addLabeledComponent( "BoxLang Home", boxLangHomeField )
		    .addLabeledComponent( "Java Home", javaHomeField )
		    .addSeparator()
		    .addLabeledComponent( "LSP", lspStatusPanel )
		    .addLabeledComponent( "LSP BoxLang Version", lspBoxLangVersionField )
		    .addLabeledComponent( "LSP BoxLang Home", lspBoxLangHomeField )
		    .addLabeledComponent( "LSP Modules", lspModulesField )
		    .addLabeledComponent( "LSP JVM Args", lspJvmArgsField )
		    .addLabeledComponent( "LSP Max Heap (MB)", lspMaxHeapSizeSpinner )
		    .addSeparator()
		    .addLabeledComponent( "Debugger", debuggerStatusPanel )
		    .addSeparator()
		    .addComponent( useBvmrcCheckBox )
		    .addComponent( promptForDownloadsCheckBox )
		    .getPanel();
	}

	private JPanel createStatusPanel( JBLabel statusLabel, HyperlinkLabel pathLink, HyperlinkLabel downloadLink ) {
		JPanel panel = new JPanel( new FlowLayout( FlowLayout.LEFT, 0, 0 ) );
		panel.add( statusLabel );
		panel.add( pathLink );
		pathLink.setBorder( JBUI.Borders.emptyLeft( 5 ) );
		panel.add( downloadLink );
		downloadLink.setBorder( JBUI.Borders.emptyLeft( 15 ) );
		return panel;
	}

	public void setDownloadListener( @Nullable DownloadListener listener ) {
		this.downloadListener = listener;
	}

	public JComponent getPanel() {
		return panel;
	}

	public void setEnabled( boolean enabled ) {
		boxLangVersionField.setEnabled( enabled );
		boxLangJarPathField.setEnabled( enabled );
		boxLangHomeField.setEnabled( enabled );
		javaHomeField.setEnabled( enabled );
		lspBoxLangVersionField.setEnabled( enabled );
		lspBoxLangHomeField.setEnabled( enabled );
		lspModulesField.setEnabled( enabled );
		lspJvmArgsField.setEnabled( enabled );
		lspMaxHeapSizeSpinner.setEnabled( enabled );
		useBvmrcCheckBox.setEnabled( enabled );
		promptForDownloadsCheckBox.setEnabled( enabled );
	}

	public void reset( BoxLangSettingsState state ) {
		boxLangVersionField.setText( nullToEmpty( state.boxLangVersion ) );
		boxLangJarPathField.setText( nullToEmpty( state.boxLangJarPath ) );
		boxLangHomeField.setText( nullToEmpty( state.boxLangHome ) );
		javaHomeField.setText( nullToEmpty( state.javaHome ) );
		lspBoxLangVersionField.setText( nullToEmpty( state.lspBoxLangVersion ) );
		lspBoxLangHomeField.setText( nullToEmpty( state.lspBoxLangHome ) );
		lspModulesField.setText( nullToEmpty( state.lspModules ) );
		lspJvmArgsField.setText( nullToEmpty( state.lspJvmArgs ) );
		lspMaxHeapSizeSpinner.setValue( state.lspMaxHeapSize );
		useBvmrcCheckBox.setSelected( state.useBvmrc );
		promptForDownloadsCheckBox.setSelected( state.promptForDownloads );
	}

	/**
	 * Updates the module status display based on currently installed modules.
	 * Call this after reset() to show current installation status.
	 */
	public void updateModuleStatus( @Nullable Project project ) {
		// Update LSP status
		InstalledModuleStatus lspStatus = ModuleStatusResolver.resolveLspStatus( project );
		if ( lspStatus.installed ) {
			String versionText = "v" + ( lspStatus.version != null ? lspStatus.version : "unknown" );
			lspStatusLabel.setText( versionText );
			if ( lspStatus.path != null ) {
				setupPathLink( lspPathLink, lspStatus.path, true );
				lspPathLink.setVisible( true );
			} else {
				lspPathLink.setVisible( false );
			}
			lspDownloadLink.setVisible( false );
		} else {
			lspStatusLabel.setText( "Not installed" );
			lspPathLink.setVisible( false );
			lspDownloadLink.setVisible( true );
		}

		// Update Debugger status
		InstalledModuleStatus debuggerStatus = ModuleStatusResolver.resolveDebuggerStatus( project );
		if ( debuggerStatus.installed ) {
			String versionText = "v" + ( debuggerStatus.version != null ? debuggerStatus.version : "unknown" );
			debuggerStatusLabel.setText( versionText );
			if ( debuggerStatus.path != null ) {
				setupPathLink( debuggerPathLink, debuggerStatus.path, false );
				debuggerPathLink.setVisible( true );
			} else {
				debuggerPathLink.setVisible( false );
			}
			debuggerDownloadLink.setVisible( false );
		} else {
			debuggerStatusLabel.setText( "Not installed" );
			debuggerPathLink.setVisible( false );
			debuggerDownloadLink.setVisible( true );
		}
	}

	private void setupPathLink( HyperlinkLabel link, String path, boolean isLsp ) {
		// Show shortened path but open full path
		Path	fullPath		= Path.of( path );
		Path	parentDir		= fullPath.getParent();
		String	displayPath		= parentDir != null ? parentDir.toString() : path;
		String	shortenedPath	= shortenPath( displayPath, 50 );

		link.setHyperlinkText( "at ", shortenedPath, "" );
		link.setToolTipText( displayPath );

		// Store path for the hyperlink action
		if ( isLsp ) {
			lspInstalledPath = parentDir != null ? parentDir : fullPath;
		} else {
			debuggerInstalledPath = parentDir != null ? parentDir : fullPath;
		}
	}

	private String shortenPath( String path, int maxLength ) {
		if ( path.length() <= maxLength ) {
			return path;
		}
		// Show beginning and end with ... in middle
		int halfLength = ( maxLength - 3 ) / 2;
		return path.substring( 0, halfLength ) + "..." + path.substring( path.length() - halfLength );
	}

	private void openInFileBrowser( Path path ) {
		try {
			File file = path.toFile();
			if ( !file.exists() ) {
				file = file.getParentFile();
			}
			if ( file != null && file.exists() ) {
				Desktop.getDesktop().open( file );
			}
		} catch ( IOException e ) {
			LOG.warn( "Failed to open file browser for: " + path, e );
		}
	}

	public void apply( BoxLangSettingsState state ) {
		state.boxLangVersion		= emptyToNull( boxLangVersionField.getText() );
		state.boxLangJarPath		= emptyToNull( boxLangJarPathField.getText() );
		state.boxLangHome			= emptyToNull( boxLangHomeField.getText() );
		state.javaHome				= emptyToNull( javaHomeField.getText() );
		state.lspBoxLangVersion		= emptyToNull( lspBoxLangVersionField.getText() );
		state.lspBoxLangHome		= emptyToNull( lspBoxLangHomeField.getText() );
		state.lspModules			= emptyToNull( lspModulesField.getText() );
		state.lspJvmArgs			= emptyToNull( lspJvmArgsField.getText() );
		state.lspMaxHeapSize		= ( ( Number ) lspMaxHeapSizeSpinner.getValue() ).intValue();
		state.useBvmrc				= useBvmrcCheckBox.isSelected();
		state.promptForDownloads	= promptForDownloadsCheckBox.isSelected();
	}

	public boolean isModified( BoxLangSettingsState state ) {
		return !Objects.equals( state.boxLangVersion, emptyToNull( boxLangVersionField.getText() ) )
		    || !Objects.equals( state.boxLangJarPath, emptyToNull( boxLangJarPathField.getText() ) )
		    || !Objects.equals( state.boxLangHome, emptyToNull( boxLangHomeField.getText() ) )
		    || !Objects.equals( state.javaHome, emptyToNull( javaHomeField.getText() ) )
		    || !Objects.equals( state.lspBoxLangVersion, emptyToNull( lspBoxLangVersionField.getText() ) )
		    || !Objects.equals( state.lspBoxLangHome, emptyToNull( lspBoxLangHomeField.getText() ) )
		    || !Objects.equals( state.lspModules, emptyToNull( lspModulesField.getText() ) )
		    || !Objects.equals( state.lspJvmArgs, emptyToNull( lspJvmArgsField.getText() ) )
		    || state.lspMaxHeapSize != ( ( Number ) lspMaxHeapSizeSpinner.getValue() ).intValue()
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
