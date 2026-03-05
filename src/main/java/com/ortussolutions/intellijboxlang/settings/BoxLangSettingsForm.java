package com.ortussolutions.intellijboxlang.settings;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.ortussolutions.intellijboxlang.runtime.InstalledModuleStatus;
import com.ortussolutions.intellijboxlang.runtime.ModuleStatusResolver;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.HyperlinkEvent;
import org.jetbrains.annotations.Nullable;

public final class BoxLangSettingsForm {

	private static final Logger LOG = Logger.getInstance( BoxLangSettingsForm.class );

	public interface DownloadListener {

		void onDownloadRuntime();

		void onDownloadLsp();

		void onDownloadDebugger();

		default void onChangeRuntime() {
		}

		default void onDeleteRuntime() {
		}

		default void onChangeLsp() {
		}

		default void onDeleteLsp() {
		}

		default void onChangeDebugger() {
		}

		default void onDeleteDebugger() {
		}
	}

	private final JPanel			panel;
	private final JPanel			formPanel;
	private final JBTextField		boxLangJarPathField		= new JBTextField();
	private final JBTextField		boxLangHomeField		= new JBTextField();
	private final JBTextField		javaHomeField			= new JBTextField();
	private final JBTextField		lspBoxLangVersionField	= new JBTextField();
	private final JBTextField		lspBoxLangHomeField		= new JBTextField();
	private final JBTextField		lspModulesField			= new JBTextField();
	private final JBTextField		lspJvmArgsField			= new JBTextField();
	private final JBTextField		lspModulePathField		= new JBTextField();
	private final JSpinner			lspMaxHeapSizeSpinner	= new JSpinner( new SpinnerNumberModel( 512, 64, 8192, 64 ) );
	private final JBTextField		debuggerModulePathField	= new JBTextField();
	private final JBCheckBox		useBvmrcCheckBox		= new JBCheckBox( "Use .bvmrc for BoxLang version" );

	// Runtime status components
	private final JBLabel			runtimeStatusLabel		= new JBLabel();
	private final HyperlinkLabel	runtimePathLink			= new HyperlinkLabel();
	private final HyperlinkLabel	runtimeDownloadLink		= new HyperlinkLabel( "Download" );
	private final HyperlinkLabel	runtimeChangeLink		= new HyperlinkLabel( "Change" );
	private final HyperlinkLabel	runtimeDeleteLink		= new HyperlinkLabel( "Delete" );
	private final JPanel			runtimeStatusPanel;

	// LSP status components
	private final JBLabel			lspStatusLabel			= new JBLabel();
	private final HyperlinkLabel	lspPathLink				= new HyperlinkLabel();
	private final HyperlinkLabel	lspDownloadLink			= new HyperlinkLabel( "Download" );
	private final HyperlinkLabel	lspChangeLink			= new HyperlinkLabel( "Change" );
	private final HyperlinkLabel	lspDeleteLink			= new HyperlinkLabel( "Delete" );
	private final JPanel			lspStatusPanel;

	// Debugger status components
	private final JBLabel			debuggerStatusLabel		= new JBLabel();
	private final HyperlinkLabel	debuggerPathLink		= new HyperlinkLabel();
	private final HyperlinkLabel	debuggerDownloadLink	= new HyperlinkLabel( "Download" );
	private final HyperlinkLabel	debuggerChangeLink		= new HyperlinkLabel( "Change" );
	private final HyperlinkLabel	debuggerDeleteLink		= new HyperlinkLabel( "Delete" );
	private final JPanel			debuggerStatusPanel;

	// Store paths for hyperlink actions
	private Path					runtimeInstalledPath;
	private Path					lspInstalledPath;
	private Path					debuggerInstalledPath;

	private DownloadListener		downloadListener;

	public BoxLangSettingsForm() {
		runtimeStatusPanel	= createStatusPanel( runtimeStatusLabel, runtimePathLink, runtimeDownloadLink, runtimeChangeLink,
		    runtimeDeleteLink );
		lspStatusPanel		= createStatusPanel( lspStatusLabel, lspPathLink, lspDownloadLink, lspChangeLink, lspDeleteLink );
		debuggerStatusPanel	= createStatusPanel( debuggerStatusLabel, debuggerPathLink, debuggerDownloadLink,
		    debuggerChangeLink, debuggerDeleteLink );

		lspModulesField.getEmptyText().setText( "e.g. bx-esapi, bx-pdf" );

		// Path link actions
		runtimePathLink.addHyperlinkListener( e -> {
			if ( e.getEventType() == HyperlinkEvent.EventType.ACTIVATED && runtimeInstalledPath != null ) {
				openInFileBrowser( runtimeInstalledPath );
			}
		} );
		lspPathLink.addHyperlinkListener( e -> {
			if ( e.getEventType() == HyperlinkEvent.EventType.ACTIVATED && lspInstalledPath != null ) {
				openInFileBrowser( lspInstalledPath );
			}
		} );
		debuggerPathLink.addHyperlinkListener( e -> {
			if ( e.getEventType() == HyperlinkEvent.EventType.ACTIVATED && debuggerInstalledPath != null ) {
				openInFileBrowser( debuggerInstalledPath );
			}
		} );

		// Download (not-installed state)
		runtimeDownloadLink.addHyperlinkListener( e -> {
			if ( e.getEventType() == HyperlinkEvent.EventType.ACTIVATED && downloadListener != null ) {
				downloadListener.onDownloadRuntime();
			}
		} );
		lspDownloadLink.addHyperlinkListener( e -> {
			if ( e.getEventType() == HyperlinkEvent.EventType.ACTIVATED && downloadListener != null ) {
				downloadListener.onDownloadLsp();
			}
		} );
		debuggerDownloadLink.addHyperlinkListener( e -> {
			if ( e.getEventType() == HyperlinkEvent.EventType.ACTIVATED && downloadListener != null ) {
				downloadListener.onDownloadDebugger();
			}
		} );

		// Change / Update (installed state)
		runtimeChangeLink.addHyperlinkListener( e -> {
			if ( e.getEventType() == HyperlinkEvent.EventType.ACTIVATED && downloadListener != null ) {
				downloadListener.onChangeRuntime();
			}
		} );
		lspChangeLink.addHyperlinkListener( e -> {
			if ( e.getEventType() == HyperlinkEvent.EventType.ACTIVATED && downloadListener != null ) {
				downloadListener.onChangeLsp();
			}
		} );
		debuggerChangeLink.addHyperlinkListener( e -> {
			if ( e.getEventType() == HyperlinkEvent.EventType.ACTIVATED && downloadListener != null ) {
				downloadListener.onChangeDebugger();
			}
		} );

		// Delete (installed state)
		runtimeDeleteLink.addHyperlinkListener( e -> {
			if ( e.getEventType() == HyperlinkEvent.EventType.ACTIVATED && downloadListener != null ) {
				downloadListener.onDeleteRuntime();
			}
		} );
		lspDeleteLink.addHyperlinkListener( e -> {
			if ( e.getEventType() == HyperlinkEvent.EventType.ACTIVATED && downloadListener != null ) {
				downloadListener.onDeleteLsp();
			}
		} );
		debuggerDeleteLink.addHyperlinkListener( e -> {
			if ( e.getEventType() == HyperlinkEvent.EventType.ACTIVATED && downloadListener != null ) {
				downloadListener.onDeleteDebugger();
			}
		} );

		formPanel	= FormBuilder.createFormBuilder()
		    .addLabeledComponent( "BoxLang Runtime", runtimeStatusPanel )
		    .addLabeledComponent( "BoxLang Jar Path Override", boxLangJarPathField )
		    .addLabeledComponent( "BoxLang Home", boxLangHomeField )
		    .addLabeledComponent( "Java Home", javaHomeField )
		    .addSeparator()
		    .addLabeledComponent( "LSP", lspStatusPanel )
		    .addLabeledComponent( "LSP Module Path Override", lspModulePathField )
		    .addLabeledComponent( "LSP BoxLang Version", lspBoxLangVersionField )
		    .addLabeledComponent( "LSP BoxLang Home", lspBoxLangHomeField )
		    .addLabeledComponent( "LSP Modules (experimental)", lspModulesField ).addLabeledComponent( "LSP JVM Args", lspJvmArgsField )
		    .addLabeledComponent( "LSP Max Heap (MB)", lspMaxHeapSizeSpinner )
		    .addSeparator()
		    .addLabeledComponent( "Debugger", debuggerStatusPanel )
		    .addLabeledComponent( "Debugger Module Path Override", debuggerModulePathField )
		    .addSeparator()
		    .addComponent( useBvmrcCheckBox )
		    .getPanel();
		panel		= new JPanel( new BorderLayout() );
		panel.add( formPanel, BorderLayout.NORTH );
	}

	private JPanel createStatusPanel( JBLabel statusLabel, HyperlinkLabel pathLink, HyperlinkLabel downloadLink,
	    HyperlinkLabel changeLink, HyperlinkLabel deleteLink ) {
		JPanel panel = new JPanel( new FlowLayout( FlowLayout.LEFT, 0, 0 ) );
		panel.add( statusLabel );
		panel.add( Box.createRigidArea( new Dimension( 8, 0 ) ) );
		panel.add( pathLink );
		panel.add( Box.createRigidArea( new Dimension( 8, 0 ) ) );
		panel.add( downloadLink );
		panel.add( Box.createRigidArea( new Dimension( 8, 0 ) ) );
		panel.add( changeLink );
		panel.add( Box.createRigidArea( new Dimension( 8, 0 ) ) );
		panel.add( deleteLink );
		return panel;
	}

	public void setDownloadListener( @Nullable DownloadListener listener ) {
		this.downloadListener = listener;
	}

	public JComponent getPanel() {
		return panel;
	}

	public void setEnabled( boolean enabled ) {
		boxLangJarPathField.setEnabled( enabled );
		boxLangHomeField.setEnabled( enabled );
		javaHomeField.setEnabled( enabled );
		lspBoxLangVersionField.setEnabled( enabled );
		lspBoxLangHomeField.setEnabled( enabled );
		lspModulesField.setEnabled( enabled );
		lspJvmArgsField.setEnabled( enabled );
		lspModulePathField.setEnabled( enabled );
		lspMaxHeapSizeSpinner.setEnabled( enabled );
		debuggerModulePathField.setEnabled( enabled );
		useBvmrcCheckBox.setEnabled( enabled );
	}

	public void reset( BoxLangSettingsState state ) {
		boxLangJarPathField.setText( nullToEmpty( state.boxLangJarPath ) );
		boxLangHomeField.setText( nullToEmpty( state.boxLangHome ) );
		javaHomeField.setText( nullToEmpty( state.javaHome ) );
		lspBoxLangVersionField.setText( nullToEmpty( state.lspBoxLangVersion ) );
		lspBoxLangHomeField.setText( nullToEmpty( state.lspBoxLangHome ) );
		lspModulesField.setText( nullToEmpty( state.lspModules ) );
		lspJvmArgsField.setText( nullToEmpty( state.lspJvmArgs ) );
		lspModulePathField.setText( nullToEmpty( state.lspModulePath ) );
		lspMaxHeapSizeSpinner.setValue( state.lspMaxHeapSize );
		debuggerModulePathField.setText( nullToEmpty( state.debuggerModulePath ) );
		useBvmrcCheckBox.setSelected( state.useBvmrc );
	}

	/**
	 * Updates the module status display based on currently installed modules.
	 * Call this after reset() to show current installation status.
	 */
	public void updateModuleStatus( @Nullable Project project ) {
		updateModuleStatus( project, null, null, null );
	}

	/**
	 * Updates the module status display, with optional latest version strings for the outdated
	 * indicator. Pass null for any if the version hasn't been fetched yet.
	 */
	public void updateModuleStatus( @Nullable Project project, @Nullable String runtimeLatestVersion,
	    @Nullable String lspLatestVersion, @Nullable String debuggerLatestVersion ) {
		// Runtime status
		InstalledModuleStatus runtimeStatus = ModuleStatusResolver.resolveRuntimeStatus( runtimeLatestVersion );
		updateStatusPanel(
		    runtimeStatus,
		    runtimeStatusLabel,
		    runtimePathLink,
		    runtimeDownloadLink,
		    runtimeChangeLink,
		    runtimeDeleteLink,
		    StatusKind.RUNTIME );

		// LSP status
		InstalledModuleStatus lspStatus = ModuleStatusResolver.resolveLspStatus( project, lspLatestVersion );
		updateStatusPanel(
		    lspStatus,
		    lspStatusLabel,
		    lspPathLink,
		    lspDownloadLink,
		    lspChangeLink,
		    lspDeleteLink,
		    StatusKind.LSP );

		// Debugger status
		InstalledModuleStatus debuggerStatus = ModuleStatusResolver.resolveDebuggerStatus( project, debuggerLatestVersion );
		updateStatusPanel(
		    debuggerStatus,
		    debuggerStatusLabel,
		    debuggerPathLink,
		    debuggerDownloadLink,
		    debuggerChangeLink,
		    debuggerDeleteLink,
		    StatusKind.DEBUGGER );
	}

	private enum StatusKind {
		RUNTIME, LSP, DEBUGGER
	}

	private void updateStatusPanel( InstalledModuleStatus status, JBLabel statusLabel, HyperlinkLabel pathLink,
	    HyperlinkLabel downloadLink, HyperlinkLabel changeLink, HyperlinkLabel deleteLink, StatusKind kind ) {
		if ( status.installed ) {
			String versionText = "v" + ( status.version != null ? status.version : "unknown" );
			if ( status.isOutdated() ) {
				versionText += "  (latest: " + status.latestVersion + ")";
				changeLink.setHyperlinkText( "Update" );
			} else {
				changeLink.setHyperlinkText( "Change" );
			}
			statusLabel.setText( versionText );

			if ( status.path != null ) {
				setupPathLink( pathLink, status.path, kind );
				pathLink.setVisible( true );
			} else {
				pathLink.setVisible( false );
			}

			downloadLink.setVisible( false );
			changeLink.setVisible( true );
			deleteLink.setVisible( true );
		} else {
			statusLabel.setText( "Not installed" );
			pathLink.setVisible( false );
			downloadLink.setVisible( true );
			changeLink.setVisible( false );
			deleteLink.setVisible( false );
		}
	}

	private void setupPathLink( HyperlinkLabel link, String path, StatusKind kind ) {
		Path	fullPath		= Path.of( path );
		Path	parentDir		= fullPath.getParent();
		String	displayPath		= parentDir != null ? parentDir.toString() : path;
		String	shortenedPath	= shortenPath( displayPath, 50 );

		link.setHyperlinkText( "at ", shortenedPath, "" );
		link.setToolTipText( displayPath );

		Path installedPath = parentDir != null ? parentDir : fullPath;
		switch ( kind ) {
			case RUNTIME -> runtimeInstalledPath = installedPath;
			case LSP -> lspInstalledPath = installedPath;
			case DEBUGGER -> debuggerInstalledPath = installedPath;
		}
	}

	private String shortenPath( String path, int maxLength ) {
		if ( path.length() <= maxLength ) {
			return path;
		}
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
		state.boxLangJarPath		= emptyToNull( boxLangJarPathField.getText() );
		state.boxLangHome			= emptyToNull( boxLangHomeField.getText() );
		state.javaHome				= emptyToNull( javaHomeField.getText() );
		state.lspBoxLangVersion		= emptyToNull( lspBoxLangVersionField.getText() );
		state.lspBoxLangHome		= emptyToNull( lspBoxLangHomeField.getText() );
		state.lspModules			= emptyToNull( lspModulesField.getText() );
		state.lspJvmArgs			= emptyToNull( lspJvmArgsField.getText() );
		state.lspModulePath			= emptyToNull( lspModulePathField.getText() );
		state.lspMaxHeapSize		= ( ( Number ) lspMaxHeapSizeSpinner.getValue() ).intValue();
		state.debuggerModulePath	= emptyToNull( debuggerModulePathField.getText() );
		state.useBvmrc				= useBvmrcCheckBox.isSelected();
	}

	public boolean isModified( BoxLangSettingsState state ) {
		return !Objects.equals( state.boxLangJarPath, emptyToNull( boxLangJarPathField.getText() ) )
		    || !Objects.equals( state.boxLangHome, emptyToNull( boxLangHomeField.getText() ) )
		    || !Objects.equals( state.javaHome, emptyToNull( javaHomeField.getText() ) )
		    || !Objects.equals( state.lspBoxLangVersion, emptyToNull( lspBoxLangVersionField.getText() ) )
		    || !Objects.equals( state.lspBoxLangHome, emptyToNull( lspBoxLangHomeField.getText() ) )
		    || !Objects.equals( state.lspModules, emptyToNull( lspModulesField.getText() ) )
		    || !Objects.equals( state.lspJvmArgs, emptyToNull( lspJvmArgsField.getText() ) )
		    || !Objects.equals( state.lspModulePath, emptyToNull( lspModulePathField.getText() ) )
		    || state.lspMaxHeapSize != ( ( Number ) lspMaxHeapSizeSpinner.getValue() ).intValue()
		    || !Objects.equals( state.debuggerModulePath, emptyToNull( debuggerModulePathField.getText() ) )
		    || state.useBvmrc != useBvmrcCheckBox.isSelected();
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
