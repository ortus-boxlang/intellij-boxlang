package com.ortussolutions.intellijboxlang.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.ortussolutions.intellijboxlang.runtime.BoxLangVersionCatalog;
import com.ortussolutions.intellijboxlang.runtime.ForgeBoxDebuggerInstaller;
import com.ortussolutions.intellijboxlang.runtime.ForgeBoxLspInstaller;
import com.ortussolutions.intellijboxlang.runtime.ForgeBoxVersionFetcher;
import com.ortussolutions.intellijboxlang.runtime.InstalledModuleStatus;
import com.ortussolutions.intellijboxlang.runtime.ModuleStatusResolver;
import com.ortussolutions.intellijboxlang.settings.VersionPickerDialog;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.Box;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.HyperlinkEvent;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

/**
 * Per-project BoxLang settings configurable.
 * Shown under Settings → Languages &amp; Frameworks → BoxLang → Project Overrides.
 * Fields here override the global defaults from {@link BoxLangApplicationConfigurable}
 * for this specific project. Leave a field blank to inherit the global value.
 *
 * <p>
 * LSP and Debugger modules can be downloaded directly from this panel into the
 * project-local {@code <projectDir>/.boxlang/modules/} directory.
 * </p>
 */
public final class BoxLangProjectConfigurable implements Configurable {

	private static final Logger	LOG				= Logger.getInstance( BoxLangProjectConfigurable.class );

	/** Sentinel value shown as the first combo entry meaning "inherit global". */
	private static final String	VERSION_INHERIT	= "(Global default)";

	private final Project		project;

	// Project-level override fields
	private JComboBox<String>	boxLangVersionCombo;
	private JBTextField			boxLangJarPathField;
	private JBTextField			boxLangHomeField;
	private JBTextField			javaHomeField;
	private JBTextField			lspBoxLangVersionField;
	private JBTextField			lspBoxLangHomeField;
	private JBTextField			lspModulesField;
	private JBTextField			lspJvmArgsField;
	private JSpinner			lspMaxHeapSizeSpinner;
	private JComboBox<String>	useBvmrcCombo;

	// LSP global status (read-only label row)
	private JBLabel				lspGlobalLabel;
	private HyperlinkLabel		lspGlobalPathLink;
	private JPanel				lspGlobalPanel;
	private Path				lspGlobalInstalledPath;

	// LSP project status panel components
	private JBLabel				lspStatusLabel;
	private HyperlinkLabel		lspPathLink;
	private HyperlinkLabel		lspDownloadLink;
	private HyperlinkLabel		lspChangeLink;
	private HyperlinkLabel		lspDeleteLink;
	private JPanel				lspStatusPanel;
	private Path				lspInstalledPath;

	// Debugger global status (read-only label row)
	private JBLabel				debuggerGlobalLabel;
	private HyperlinkLabel		debuggerGlobalPathLink;
	private JPanel				debuggerGlobalPanel;
	private Path				debuggerGlobalInstalledPath;

	// Debugger project status panel components
	private JBLabel				debuggerStatusLabel;
	private HyperlinkLabel		debuggerPathLink;
	private HyperlinkLabel		debuggerDownloadLink;
	private HyperlinkLabel		debuggerChangeLink;
	private HyperlinkLabel		debuggerDeleteLink;
	private JPanel				debuggerStatusPanel;
	private Path				debuggerInstalledPath;

	// Hint labels showing the current global value for each field
	private JBLabel				boxLangVersionHint;
	private JBLabel				boxLangJarPathHint;
	private JBLabel				boxLangHomeHint;
	private JBLabel				javaHomeHint;
	private JBLabel				lspBoxLangVersionHint;
	private JBLabel				lspBoxLangHomeHint;
	private JBLabel				lspModulesHint;
	private JBLabel				lspJvmArgsHint;
	private JBLabel				lspMaxHeapSizeHint;
	private JBLabel				useBvmrcHint;

	/** Cached latest versions for outdated indicators, populated asynchronously. */
	private volatile String		cachedLspLatestVersion;
	private volatile String		cachedDebuggerLatestVersion;

	private JPanel				panel;

	public BoxLangProjectConfigurable( Project project ) {
		this.project = project;
	}

	@Override
	public @Nls( capitalization = Nls.Capitalization.Title ) String getDisplayName() {
		return "Project Overrides";
	}

	@Override
	public @Nullable JComponent createComponent() {
		boxLangVersionCombo = new JComboBox<>( new String[] { VERSION_INHERIT } );
		boxLangVersionCombo.setEditable( false );
		boxLangJarPathField		= new JBTextField();
		boxLangHomeField		= new JBTextField();
		javaHomeField			= new JBTextField();
		lspBoxLangVersionField	= new JBTextField();
		lspBoxLangHomeField		= new JBTextField();
		lspModulesField			= new JBTextField();
		lspModulesField.getEmptyText().setText( "e.g. bx-esapi, bx-pdf" );
		lspJvmArgsField			= new JBTextField();
		lspMaxHeapSizeSpinner	= new JSpinner( new SpinnerNumberModel( 0, 0, 8192, 64 ) );
		useBvmrcCombo			= new JComboBox<>( new String[] { "Global default", "Enabled", "Disabled" } );

		// LSP global status (read-only)
		lspGlobalLabel			= new JBLabel();
		lspGlobalPathLink		= new HyperlinkLabel();
		lspGlobalPanel			= createGlobalStatusPanel( lspGlobalLabel, lspGlobalPathLink );

		lspGlobalPathLink.addHyperlinkListener( e -> {
			if ( e.getEventType() == HyperlinkEvent.EventType.ACTIVATED && lspGlobalInstalledPath != null ) {
				openInFileBrowser( lspGlobalInstalledPath );
			}
		} );

		// LSP project status panel
		lspStatusLabel	= new JBLabel();
		lspPathLink		= new HyperlinkLabel();
		lspDownloadLink	= new HyperlinkLabel( "Download" );
		lspChangeLink	= new HyperlinkLabel( "Change" );
		lspDeleteLink	= new HyperlinkLabel( "Delete" );
		lspStatusPanel	= createStatusPanel( lspStatusLabel, lspPathLink, lspDownloadLink, lspChangeLink, lspDeleteLink );

		lspPathLink.addHyperlinkListener( e -> {
			if ( e.getEventType() == HyperlinkEvent.EventType.ACTIVATED && lspInstalledPath != null ) {
				openInFileBrowser( lspInstalledPath );
			}
		} );
		lspDownloadLink.addHyperlinkListener( e -> {
			if ( e.getEventType() == HyperlinkEvent.EventType.ACTIVATED ) {
				pickAndDownloadLsp();
			}
		} );
		lspChangeLink.addHyperlinkListener( e -> {
			if ( e.getEventType() == HyperlinkEvent.EventType.ACTIVATED ) {
				pickAndDownloadLsp();
			}
		} );
		lspDeleteLink.addHyperlinkListener( e -> {
			if ( e.getEventType() == HyperlinkEvent.EventType.ACTIVATED ) {
				deleteLsp();
			}
		} );

		// Debugger global status (read-only)
		debuggerGlobalLabel		= new JBLabel();
		debuggerGlobalPathLink	= new HyperlinkLabel();
		debuggerGlobalPanel		= createGlobalStatusPanel( debuggerGlobalLabel, debuggerGlobalPathLink );

		debuggerGlobalPathLink.addHyperlinkListener( e -> {
			if ( e.getEventType() == HyperlinkEvent.EventType.ACTIVATED && debuggerGlobalInstalledPath != null ) {
				openInFileBrowser( debuggerGlobalInstalledPath );
			}
		} );

		// Debugger project status panel
		debuggerStatusLabel		= new JBLabel();
		debuggerPathLink		= new HyperlinkLabel();
		debuggerDownloadLink	= new HyperlinkLabel( "Download" );
		debuggerChangeLink		= new HyperlinkLabel( "Change" );
		debuggerDeleteLink		= new HyperlinkLabel( "Delete" );
		debuggerStatusPanel		= createStatusPanel( debuggerStatusLabel, debuggerPathLink, debuggerDownloadLink,
		    debuggerChangeLink, debuggerDeleteLink );

		debuggerPathLink.addHyperlinkListener( e -> {
			if ( e.getEventType() == HyperlinkEvent.EventType.ACTIVATED && debuggerInstalledPath != null ) {
				openInFileBrowser( debuggerInstalledPath );
			}
		} );
		debuggerDownloadLink.addHyperlinkListener( e -> {
			if ( e.getEventType() == HyperlinkEvent.EventType.ACTIVATED ) {
				pickAndDownloadDebugger();
			}
		} );
		debuggerChangeLink.addHyperlinkListener( e -> {
			if ( e.getEventType() == HyperlinkEvent.EventType.ACTIVATED ) {
				pickAndDownloadDebugger();
			}
		} );
		debuggerDeleteLink.addHyperlinkListener( e -> {
			if ( e.getEventType() == HyperlinkEvent.EventType.ACTIVATED ) {
				deleteDebugger();
			}
		} );

		// Hint labels
		boxLangVersionHint		= createHintLabel();
		boxLangJarPathHint		= createHintLabel();
		boxLangHomeHint			= createHintLabel();
		javaHomeHint			= createHintLabel();
		lspBoxLangVersionHint	= createHintLabel();
		lspBoxLangHomeHint		= createHintLabel();
		lspModulesHint			= createHintLabel();
		lspJvmArgsHint			= createHintLabel();
		lspMaxHeapSizeHint		= createHintLabel();
		useBvmrcHint			= createHintLabel();

		JPanel formPanel = FormBuilder.createFormBuilder()
		    .addComponent( new JBLabel(
		        "<html><i>Leave a field blank (or set to 0 / \"Global default\") to inherit the global default from Settings \u2192 BoxLang.</i></html>" ) )
		    .addVerticalGap( 8 )
		    .addLabeledComponent( "BoxLang Version", boxLangVersionCombo )
		    .addComponentToRightColumn( boxLangVersionHint )
		    .addLabeledComponent( "BoxLang Jar Path", boxLangJarPathField )
		    .addComponentToRightColumn( boxLangJarPathHint )
		    .addLabeledComponent( "BoxLang Home", boxLangHomeField )
		    .addComponentToRightColumn( boxLangHomeHint )
		    .addLabeledComponent( "Java Home", javaHomeField )
		    .addComponentToRightColumn( javaHomeHint )
		    .addLabeledComponent( "Use .bvmrc", useBvmrcCombo )
		    .addComponentToRightColumn( useBvmrcHint )
		    .addSeparator()
		    .addLabeledComponent( "LSP BoxLang Version", lspBoxLangVersionField )
		    .addComponentToRightColumn( lspBoxLangVersionHint )
		    .addLabeledComponent( "LSP BoxLang Home", lspBoxLangHomeField )
		    .addComponentToRightColumn( lspBoxLangHomeHint )
		    .addLabeledComponent( "LSP Modules", lspModulesField )
		    .addComponentToRightColumn( lspModulesHint )
		    .addLabeledComponent( "LSP JVM Args", lspJvmArgsField )
		    .addComponentToRightColumn( lspJvmArgsHint )
		    .addLabeledComponent( "LSP Max Heap (MB)", lspMaxHeapSizeSpinner )
		    .addComponentToRightColumn( lspMaxHeapSizeHint )
		    .addSeparator()
		    .addLabeledComponent( "LSP (Global)", lspGlobalPanel )
		    .addLabeledComponent( "LSP (Project)", lspStatusPanel )
		    .addSeparator()
		    .addLabeledComponent( "Debugger (Global)", debuggerGlobalPanel )
		    .addLabeledComponent( "Debugger (Project)", debuggerStatusPanel )
		    .getPanel();

		// Anchor the form to the top so there is no extra blank space above it
		panel = new JPanel( new BorderLayout() );
		panel.add( formPanel, BorderLayout.NORTH );

		// Asynchronously populate the version dropdown and fetch latest module versions
		fetchVersionsAsync();
		fetchLatestVersionsAsync();

		return panel;
	}

	@Override
	public boolean isModified() {
		if ( panel == null ) {
			return false;
		}
		BoxLangProjectSettingsState state = BoxLangProjectSettings.getInstance( project ).getSettings();
		return !Objects.equals( state.boxLangVersion, comboToVersion( boxLangVersionCombo ) )
		    || !Objects.equals( state.boxLangJarPath, emptyToNull( boxLangJarPathField.getText() ) )
		    || !Objects.equals( state.boxLangHome, emptyToNull( boxLangHomeField.getText() ) )
		    || !Objects.equals( state.javaHome, emptyToNull( javaHomeField.getText() ) )
		    || !Objects.equals( state.lspBoxLangVersion, emptyToNull( lspBoxLangVersionField.getText() ) )
		    || !Objects.equals( state.lspBoxLangHome, emptyToNull( lspBoxLangHomeField.getText() ) )
		    || !Objects.equals( state.lspModules, emptyToNull( lspModulesField.getText() ) )
		    || !Objects.equals( state.lspJvmArgs, emptyToNull( lspJvmArgsField.getText() ) )
		    || state.lspMaxHeapSize != ( ( Number ) lspMaxHeapSizeSpinner.getValue() ).intValue()
		    || !Objects.equals( state.useBvmrc, comboToBoolean( useBvmrcCombo ) );
	}

	@Override
	public void apply() {
		if ( panel == null ) {
			return;
		}
		BoxLangProjectSettingsState state = BoxLangProjectSettings.getInstance( project ).getSettings();
		state.boxLangVersion	= comboToVersion( boxLangVersionCombo );
		state.boxLangJarPath	= emptyToNull( boxLangJarPathField.getText() );
		state.boxLangHome		= emptyToNull( boxLangHomeField.getText() );
		state.javaHome			= emptyToNull( javaHomeField.getText() );
		state.lspBoxLangVersion	= emptyToNull( lspBoxLangVersionField.getText() );
		state.lspBoxLangHome	= emptyToNull( lspBoxLangHomeField.getText() );
		state.lspModules		= emptyToNull( lspModulesField.getText() );
		state.lspJvmArgs		= emptyToNull( lspJvmArgsField.getText() );
		state.lspMaxHeapSize	= ( ( Number ) lspMaxHeapSizeSpinner.getValue() ).intValue();
		state.useBvmrc			= comboToBoolean( useBvmrcCombo );
	}

	@Override
	public void reset() {
		if ( panel == null ) {
			return;
		}
		BoxLangProjectSettingsState	projectState	= BoxLangProjectSettings.getInstance( project ).getSettings();
		BoxLangSettingsState		globalState		= BoxLangApplicationSettings.getInstance().getSettings();

		versionToCombo( boxLangVersionCombo, projectState.boxLangVersion );
		boxLangJarPathField.setText( nullToEmpty( projectState.boxLangJarPath ) );
		boxLangHomeField.setText( nullToEmpty( projectState.boxLangHome ) );
		javaHomeField.setText( nullToEmpty( projectState.javaHome ) );
		lspBoxLangVersionField.setText( nullToEmpty( projectState.lspBoxLangVersion ) );
		lspBoxLangHomeField.setText( nullToEmpty( projectState.lspBoxLangHome ) );
		lspModulesField.setText( nullToEmpty( projectState.lspModules ) );
		lspJvmArgsField.setText( nullToEmpty( projectState.lspJvmArgs ) );
		lspMaxHeapSizeSpinner.setValue( projectState.lspMaxHeapSize );
		booleanToCombo( useBvmrcCombo, projectState.useBvmrc );

		updateHint( boxLangVersionHint, globalState.boxLangVersion );
		updateHint( boxLangJarPathHint, globalState.boxLangJarPath );
		updateHint( boxLangHomeHint, globalState.boxLangHome );
		updateHint( javaHomeHint, globalState.javaHome );
		updateHint( lspBoxLangVersionHint, globalState.lspBoxLangVersion );
		updateLspBoxLangHomeHint( lspBoxLangHomeHint, globalState.lspBoxLangHome );
		updateHint( lspModulesHint, globalState.lspModules );
		updateHint( lspJvmArgsHint, globalState.lspJvmArgs );
		updateIntHint( lspMaxHeapSizeHint, globalState.lspMaxHeapSize, 512 );
		updateBooleanHint( useBvmrcHint, globalState.useBvmrc );

		// Refresh the module status panels
		updateModuleStatusPanels();
	}

	@Override
	public void disposeUIResources() {
		panel					= null;
		boxLangVersionCombo		= null;
		boxLangJarPathField		= null;
		boxLangHomeField		= null;
		javaHomeField			= null;
		lspBoxLangVersionField	= null;
		lspBoxLangHomeField		= null;
		lspModulesField			= null;
		lspJvmArgsField			= null;
		lspMaxHeapSizeSpinner	= null;
		useBvmrcCombo			= null;
		lspGlobalLabel			= null;
		lspGlobalPathLink		= null;
		lspGlobalPanel			= null;
		lspStatusLabel			= null;
		lspPathLink				= null;
		lspDownloadLink			= null;
		lspChangeLink			= null;
		lspDeleteLink			= null;
		lspStatusPanel			= null;
		debuggerGlobalLabel		= null;
		debuggerGlobalPathLink	= null;
		debuggerGlobalPanel		= null;
		debuggerStatusLabel		= null;
		debuggerPathLink		= null;
		debuggerDownloadLink	= null;
		debuggerChangeLink		= null;
		debuggerDeleteLink		= null;
		debuggerStatusPanel		= null;
		boxLangVersionHint		= null;
		boxLangJarPathHint		= null;
		boxLangHomeHint			= null;
		javaHomeHint			= null;
		lspBoxLangVersionHint	= null;
		lspBoxLangHomeHint		= null;
		lspModulesHint			= null;
		lspJvmArgsHint			= null;
		lspMaxHeapSizeHint		= null;
		useBvmrcHint			= null;
	}

	// -------------------------------------------------------------------------
	// Async version fetch
	// -------------------------------------------------------------------------

	private void fetchVersionsAsync() {
		ProgressManager.getInstance().run( new Task.Backgroundable( project, "Fetching BoxLang Versions", false ) {

			@Override
			public void run( @org.jetbrains.annotations.NotNull ProgressIndicator indicator ) {
				indicator.setIndeterminate( true );
				List<String> versions = null;
				try {
					versions = BoxLangVersionCatalog.fetchVersionNames();
				} catch ( Exception e ) {
					LOG.warn( "Failed to fetch BoxLang version list for project settings", e );
				}

				final List<String> finalVersions = versions;
				ApplicationManager.getApplication().invokeLater( () -> {
					if ( boxLangVersionCombo == null ) {
						return;
					}
					populateVersionCombo( finalVersions );
				} );
			}
		} );
	}

	private void fetchLatestVersionsAsync() {
		ProgressManager.getInstance().run( new Task.Backgroundable( project, "Checking for BoxLang Module Updates", false ) {

			@Override
			public void run( @org.jetbrains.annotations.NotNull ProgressIndicator indicator ) {
				indicator.setIndeterminate( true );
				String	lspLatest		= null;
				String	debuggerLatest	= null;

				try {
					List<String> lspVersions = ForgeBoxVersionFetcher.fetchLspVersions();
					if ( !lspVersions.isEmpty() ) {
						lspLatest = lspVersions.get( 0 );
					}
				} catch ( IOException e ) {
					LOG.warn( "Failed to fetch LSP versions from ForgeBox", e );
				}

				try {
					List<String> debuggerVersions = ForgeBoxVersionFetcher.fetchDebuggerVersions();
					if ( !debuggerVersions.isEmpty() ) {
						debuggerLatest = debuggerVersions.get( 0 );
					}
				} catch ( IOException e ) {
					LOG.warn( "Failed to fetch debugger versions from ForgeBox", e );
				}

				final String	finalLspLatest		= lspLatest;
				final String	finalDebuggerLatest	= debuggerLatest;

				ApplicationManager.getApplication().invokeLater( () -> {
					cachedLspLatestVersion		= finalLspLatest;
					cachedDebuggerLatestVersion	= finalDebuggerLatest;
					updateModuleStatusPanels();
				} );
			}
		} );
	}

	private void populateVersionCombo( @Nullable List<String> catalogVersions ) {
		// Remember currently selected value before rebuilding
		String			current	= comboToVersion( boxLangVersionCombo );

		List<String>	items	= new ArrayList<>();
		items.add( VERSION_INHERIT );

		if ( catalogVersions != null ) {
			for ( String v : catalogVersions ) {
				// Strip "boxlang-" prefix for display
				String display = v.startsWith( "boxlang-" ) ? v.substring( "boxlang-".length() ) : v;
				items.add( display );
			}
		}

		DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>( items.toArray( new String[ 0 ] ) );
		boxLangVersionCombo.setModel( model );

		// Re-select the previously chosen version (or leave at "inherit")
		versionToCombo( boxLangVersionCombo, current );
	}

	// -------------------------------------------------------------------------
	// Module status panels
	// -------------------------------------------------------------------------

	private void updateModuleStatusPanels() {
		if ( lspStatusLabel == null ) {
			return;
		}

		// Global LSP status (read-only baseline)
		InstalledModuleStatus lspGlobalStatus = ModuleStatusResolver.resolveGlobalLspStatus( cachedLspLatestVersion );
		updateGlobalPanel( lspGlobalStatus, lspGlobalLabel, lspGlobalPathLink, path -> lspGlobalInstalledPath = path );

		// Project-local LSP status (interactive)
		InstalledModuleStatus lspProjectStatus = ModuleStatusResolver.resolveProjectLspStatus( project,
		    cachedLspLatestVersion );
		updateStatusPanel( lspProjectStatus, lspStatusLabel, lspPathLink, lspDownloadLink, lspChangeLink, lspDeleteLink,
		    true );

		// Global Debugger status (read-only baseline)
		InstalledModuleStatus debuggerGlobalStatus = ModuleStatusResolver.resolveGlobalDebuggerStatus(
		    cachedDebuggerLatestVersion );
		updateGlobalPanel( debuggerGlobalStatus, debuggerGlobalLabel, debuggerGlobalPathLink,
		    path -> debuggerGlobalInstalledPath = path );

		// Project-local Debugger status (interactive)
		InstalledModuleStatus debuggerProjectStatus = ModuleStatusResolver.resolveProjectDebuggerStatus( project,
		    cachedDebuggerLatestVersion );
		updateStatusPanel( debuggerProjectStatus, debuggerStatusLabel, debuggerPathLink, debuggerDownloadLink,
		    debuggerChangeLink, debuggerDeleteLink, false );
	}

	private void updateStatusPanel( InstalledModuleStatus status, JBLabel statusLabel, HyperlinkLabel pathLink,
	    HyperlinkLabel downloadLink, HyperlinkLabel changeLink, HyperlinkLabel deleteLink, boolean isLsp ) {
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
				Path	modulePath	= Path.of( status.path );
				String	displayPath	= modulePath.toString();
				String	shortened	= shortenPath( displayPath, 50 );
				pathLink.setHyperlinkText( "at " + shortened );
				pathLink.setToolTipText( displayPath );
				if ( isLsp ) {
					lspInstalledPath = modulePath;
				} else {
					debuggerInstalledPath = modulePath;
				}
				pathLink.setVisible( true );
			} else {
				pathLink.setVisible( false );
			}

			downloadLink.setVisible( false );
			changeLink.setVisible( true );
			deleteLink.setVisible( true );
		} else {
			statusLabel.setText( "No project override" );
			pathLink.setVisible( false );
			downloadLink.setVisible( true );
			changeLink.setVisible( false );
			deleteLink.setVisible( false );
		}
	}

	// -------------------------------------------------------------------------
	// LSP Download / Change / Delete (project-local)
	// -------------------------------------------------------------------------

	private void pickAndDownloadLsp() {
		ProgressManager.getInstance().run( new Task.Backgroundable( project, "Fetching LSP Versions", true ) {

			@Override
			public void run( @org.jetbrains.annotations.NotNull ProgressIndicator indicator ) {
				try {
					indicator.setText( "Fetching available versions from ForgeBox..." );
					List<String> versions = ForgeBoxVersionFetcher.fetchLspVersions();

					ApplicationManager.getApplication().invokeLater( () -> {
						String selectedVersion = VersionPickerDialog.showAndGetVersion( project, "BoxLang LSP", versions );
						if ( selectedVersion != null ) {
							downloadLspVersion( selectedVersion );
						}
					} );
				} catch ( IOException e ) {
					LOG.warn( "Failed to fetch LSP versions", e );
				}
			}
		} );
	}

	private void downloadLspVersion( String version ) {
		Path projectHome = BoxLangStoragePaths.getProjectBoxLangHome( project );
		if ( projectHome == null ) {
			Messages.showErrorDialog( project, "Cannot determine project directory.", "Download Error" );
			return;
		}

		ProgressManager.getInstance().run( new Task.Backgroundable( project, "Downloading BoxLang LSP", true ) {

			@Override
			public void run( @org.jetbrains.annotations.NotNull ProgressIndicator indicator ) {
				try {
					Path targetDir = projectHome.resolve( "modules" ).resolve( "bx-lsp" );
					ForgeBoxLspInstaller.install( version, targetDir, indicator );

					ApplicationManager.getApplication().invokeLater( () -> {
						updateModuleStatusPanels();
					} );
				} catch ( IOException e ) {
					LOG.warn( "Failed to download project LSP version " + version, e );
				}
			}
		} );
	}

	private void deleteLsp() {
		InstalledModuleStatus status = ModuleStatusResolver.resolveLspStatus( project );
		if ( !status.installed || status.path == null ) {
			return;
		}

		Path	modulePath	= Path.of( status.path );

		int		confirm		= Messages.showYesNoDialog(
		    project,
		    "Delete the project-local LSP module at:\n" + modulePath + "\n\nThis cannot be undone.",
		    "Delete BoxLang LSP",
		    Messages.getWarningIcon() );

		if ( confirm != Messages.YES ) {
			return;
		}

		ProgressManager.getInstance().run( new Task.Backgroundable( project, "Deleting BoxLang LSP", false ) {

			@Override
			public void run( @org.jetbrains.annotations.NotNull ProgressIndicator indicator ) {
				indicator.setText( "Deleting LSP module files..." );
				indicator.setIndeterminate( true );
				try {
					deleteRecursively( modulePath );
				} catch ( IOException e ) {
					LOG.warn( "Failed to delete LSP module at " + modulePath, e );
				}

				ApplicationManager.getApplication().invokeLater( () -> {
					updateModuleStatusPanels();
				} );
			}
		} );
	}

	// -------------------------------------------------------------------------
	// Debugger Download / Change / Delete (project-local)
	// -------------------------------------------------------------------------

	private void pickAndDownloadDebugger() {
		ProgressManager.getInstance().run( new Task.Backgroundable( project, "Fetching Debugger Versions", true ) {

			@Override
			public void run( @org.jetbrains.annotations.NotNull ProgressIndicator indicator ) {
				try {
					indicator.setText( "Fetching available versions from ForgeBox..." );
					List<String> versions = ForgeBoxVersionFetcher.fetchDebuggerVersions();

					ApplicationManager.getApplication().invokeLater( () -> {
						String selectedVersion = VersionPickerDialog.showAndGetVersion( project, "BoxLang Debugger",
						    versions );
						if ( selectedVersion != null ) {
							downloadDebuggerVersion( selectedVersion );
						}
					} );
				} catch ( IOException e ) {
					LOG.warn( "Failed to fetch debugger versions", e );
				}
			}
		} );
	}

	private void downloadDebuggerVersion( String version ) {
		Path projectHome = BoxLangStoragePaths.getProjectBoxLangHome( project );
		if ( projectHome == null ) {
			Messages.showErrorDialog( project, "Cannot determine project directory.", "Download Error" );
			return;
		}

		ProgressManager.getInstance().run( new Task.Backgroundable( project, "Downloading BoxLang Debugger", true ) {

			@Override
			public void run( @org.jetbrains.annotations.NotNull ProgressIndicator indicator ) {
				try {
					Path targetDir = projectHome.resolve( "modules" ).resolve( "bx-debugger" );
					ForgeBoxDebuggerInstaller.install( version, targetDir, indicator );

					ApplicationManager.getApplication().invokeLater( () -> {
						updateModuleStatusPanels();
					} );
				} catch ( IOException e ) {
					LOG.warn( "Failed to download project debugger version " + version, e );
				}
			}
		} );
	}

	private void deleteDebugger() {
		InstalledModuleStatus status = ModuleStatusResolver.resolveDebuggerStatus( project );
		if ( !status.installed || status.path == null ) {
			return;
		}

		Path	modulePath	= Path.of( status.path );
		String	displayPath	= modulePath.toString();

		int		confirm		= Messages.showYesNoDialog(
		    project,
		    "Delete the project-local Debugger module at:\n" + displayPath + "\n\nThis cannot be undone.",
		    "Delete BoxLang Debugger",
		    Messages.getWarningIcon() );

		if ( confirm != Messages.YES ) {
			return;
		}

		final Path toDelete = modulePath;

		ProgressManager.getInstance().run( new Task.Backgroundable( project, "Deleting BoxLang Debugger", false ) {

			@Override
			public void run( @org.jetbrains.annotations.NotNull ProgressIndicator indicator ) {
				indicator.setText( "Deleting Debugger module files..." );
				indicator.setIndeterminate( true );
				try {
					if ( toDelete != null ) {
						deleteRecursively( toDelete );
					}
				} catch ( IOException e ) {
					LOG.warn( "Failed to delete Debugger module at " + toDelete, e );
				}

				ApplicationManager.getApplication().invokeLater( () -> {
					updateModuleStatusPanels();
				} );
			}
		} );
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	private static JPanel createGlobalStatusPanel( JBLabel statusLabel, HyperlinkLabel pathLink ) {
		JPanel panel = new JPanel( new FlowLayout( FlowLayout.LEFT, 0, 0 ) );
		panel.add( statusLabel );
		panel.add( Box.createRigidArea( new Dimension( 8, 0 ) ) );
		panel.add( pathLink );
		return panel;
	}

	private void updateGlobalPanel( InstalledModuleStatus status, JBLabel statusLabel, HyperlinkLabel pathLink,
	    java.util.function.Consumer<Path> pathSetter ) {
		if ( status.installed ) {
			String versionText = "v" + ( status.version != null ? status.version : "unknown" );
			statusLabel.setText( versionText );

			if ( status.path != null ) {
				Path	modulePath	= Path.of( status.path );
				String	displayPath	= modulePath.toString();
				String	shortened	= shortenPath( displayPath, 50 );
				pathLink.setHyperlinkText( "at " + shortened );
				pathLink.setToolTipText( displayPath );
				pathSetter.accept( modulePath );
				pathLink.setVisible( true );
			} else {
				pathLink.setVisible( false );
			}
		} else {
			statusLabel.setText( "Not installed" );
			pathLink.setVisible( false );
			pathSetter.accept( null );
		}
	}

	private static JPanel createStatusPanel( JBLabel statusLabel, HyperlinkLabel pathLink,
	    HyperlinkLabel downloadLink, HyperlinkLabel changeLink, HyperlinkLabel deleteLink ) {
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

	private static JBLabel createHintLabel() {
		JBLabel label = new JBLabel();
		label.setForeground( UIUtil.getContextHelpForeground() );
		label.setFont( JBUI.Fonts.smallFont() );
		label.setBorder( JBUI.Borders.emptyTop( 1 ) );
		return label;
	}

	private static void updateHint( JBLabel hint, @Nullable String globalValue ) {
		if ( hint == null )
			return;
		if ( globalValue != null && !globalValue.isBlank() ) {
			hint.setText( "Global: " + globalValue );
			hint.setVisible( true );
		} else {
			hint.setText( "" );
			hint.setVisible( false );
		}
	}

	/**
	 * For the LSP BoxLang Home hint, also show the computed default path when no global value is set.
	 * The default LSP home is: &lt;IDE-cache&gt;/boxlang/lsp-home/&lt;project.locationHash&gt;/
	 */
	private void updateLspBoxLangHomeHint( JBLabel hint, @Nullable String globalValue ) {
		if ( hint == null )
			return;
		if ( globalValue != null && !globalValue.isBlank() ) {
			hint.setText( "Global: " + globalValue );
			hint.setVisible( true );
		} else {
			String	locationHash	= project.getLocationHash();
			String	defaultPath		= BoxLangStoragePaths.getCacheRoot()
			    .resolve( "lsp-home" )
			    .resolve( locationHash )
			    .toString();
			hint.setText( "Default: " + defaultPath );
			hint.setVisible( true );
		}
	}

	private static void updateIntHint( JBLabel hint, int globalValue, int defaultValue ) {
		if ( hint == null )
			return;
		hint.setText( "Global: " + globalValue + " MB" );
		hint.setVisible( true );
	}

	private static void updateBooleanHint( JBLabel hint, boolean globalValue ) {
		if ( hint == null )
			return;
		hint.setText( "Global: " + ( globalValue ? "Enabled" : "Disabled" ) );
		hint.setVisible( true );
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

	private static void deleteRecursively( Path path ) throws IOException {
		if ( !Files.exists( path ) ) {
			return;
		}
		Files.walkFileTree( path, new SimpleFileVisitor<Path>() {

			@Override
			public FileVisitResult visitFile( Path file, BasicFileAttributes attrs ) throws IOException {
				Files.delete( file );
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory( Path dir, IOException exc ) throws IOException {
				if ( exc != null ) {
					throw exc;
				}
				Files.delete( dir );
				return FileVisitResult.CONTINUE;
			}
		} );
	}

	// -------------------------------------------------------------------------
	// Version combo helpers
	// -------------------------------------------------------------------------

	/**
	 * Sets the combo box selection to the given version string (stripping "boxlang-" prefix),
	 * or to VERSION_INHERIT if null/blank.
	 */
	private static void versionToCombo( JComboBox<String> combo, @Nullable String version ) {
		if ( version == null || version.isBlank() ) {
			combo.setSelectedIndex( 0 );
			return;
		}
		String display = version.startsWith( "boxlang-" ) ? version.substring( "boxlang-".length() ) : version;
		for ( int i = 0; i < combo.getItemCount(); i++ ) {
			if ( display.equals( combo.getItemAt( i ) ) ) {
				combo.setSelectedIndex( i );
				return;
			}
		}
		// Version not in list yet — add it as a fallback
		( ( DefaultComboBoxModel<String> ) combo.getModel() ).addElement( display );
		combo.setSelectedItem( display );
	}

	/**
	 * Returns the selected version string, or null if "Global default" is selected.
	 */
	@Nullable
	private static String comboToVersion( JComboBox<String> combo ) {
		Object selected = combo.getSelectedItem();
		if ( selected == null || VERSION_INHERIT.equals( selected ) ) {
			return null;
		}
		return selected.toString();
	}

	// -------------------------------------------------------------------------
	// Boolean combo helpers
	// -------------------------------------------------------------------------

	/** null → index 0 ("Global default"), true → 1, false → 2 */
	private static void booleanToCombo( JComboBox<String> combo, @Nullable Boolean value ) {
		if ( value == null ) {
			combo.setSelectedIndex( 0 );
		} else {
			combo.setSelectedIndex( value ? 1 : 2 );
		}
	}

	/** index 0 → null, 1 → true, 2 → false */
	@Nullable
	private static Boolean comboToBoolean( JComboBox<String> combo ) {
		int idx = combo.getSelectedIndex();
		if ( idx == 1 )
			return Boolean.TRUE;
		if ( idx == 2 )
			return Boolean.FALSE;
		return null;
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
