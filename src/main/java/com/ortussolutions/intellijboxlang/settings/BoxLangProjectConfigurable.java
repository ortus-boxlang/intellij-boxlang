package com.ortussolutions.intellijboxlang.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.FormBuilder;
import com.ortussolutions.intellijboxlang.runtime.ForgeBoxDebuggerInstaller;
import com.ortussolutions.intellijboxlang.runtime.ForgeBoxDebuggerResolver;
import com.ortussolutions.intellijboxlang.runtime.ForgeBoxDebuggerDescriptor;
import com.ortussolutions.intellijboxlang.runtime.ForgeBoxLspInstaller;
import com.ortussolutions.intellijboxlang.runtime.ForgeBoxLspResolver;
import com.ortussolutions.intellijboxlang.runtime.ForgeBoxLspDescriptor;
import com.ortussolutions.intellijboxlang.runtime.BoxLangLspHomeResolver;
import java.io.IOException;
import java.nio.file.Path;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class BoxLangProjectConfigurable implements Configurable {

	private final Project		project;
	private BoxLangSettingsForm	form;
	private JBCheckBox			useProjectSettingsCheckBox;
	private JBLabel				settingsScopeLabel;

	public BoxLangProjectConfigurable( Project project ) {
		this.project = project;
	}

	@Override
	public @Nls( capitalization = Nls.Capitalization.Title ) String getDisplayName() {
		return "BoxLang";
	}

	@Override
	public @Nullable JComponent createComponent() {
		form						= new BoxLangSettingsForm();
		useProjectSettingsCheckBox	= new JBCheckBox( "Override global settings for this project" );
		settingsScopeLabel			= new JBLabel();
		useProjectSettingsCheckBox.addActionListener( event -> updateFormState() );

		// Set up download listener
		form.setDownloadListener( new BoxLangSettingsForm.DownloadListener() {

			@Override
			public void onDownloadLsp() {
				downloadLsp();
			}

			@Override
			public void onDownloadDebugger() {
				downloadDebugger();
			}
		} );

		JPanel panel = FormBuilder.createFormBuilder()
		    .addComponent( useProjectSettingsCheckBox )
		    .addComponent( settingsScopeLabel )
		    .addComponent( form.getPanel() )
		    .getPanel();

		return panel;
	}

	@Override
	public boolean isModified() {
		if ( form == null || useProjectSettingsCheckBox == null ) {
			return false;
		}
		BoxLangProjectSettingsState	state		= BoxLangProjectSettings.getInstance( project ).getSettings();
		BoxLangSettingsState		defaults	= BoxLangApplicationSettings.getInstance().getSettings();
		if ( useProjectSettingsCheckBox.isSelected() != state.useProjectSettings ) {
			return true;
		}
		if ( !useProjectSettingsCheckBox.isSelected() ) {
			return form.isModified( defaults );
		}
		return form.isModified( mergeWithDefaults( state, defaults ) );
	}

	@Override
	public void apply() {
		BoxLangProjectSettingsState state = BoxLangProjectSettings.getInstance( project ).getSettings();
		state.useProjectSettings = useProjectSettingsCheckBox.isSelected();
		if ( state.useProjectSettings ) {
			form.apply( state );
		} else {
			clearOverrides( state, BoxLangApplicationSettings.getInstance().getSettings() );
			form.apply( BoxLangApplicationSettings.getInstance().getSettings() );
		}
	}

	@Override
	public void reset() {
		if ( form == null ) {
			return;
		}

		BoxLangProjectSettingsState	state		= BoxLangProjectSettings.getInstance( project ).getSettings();
		BoxLangSettingsState		defaults	= BoxLangApplicationSettings.getInstance().getSettings();
		useProjectSettingsCheckBox.setSelected( state.useProjectSettings );
		form.reset( state.useProjectSettings ? mergeWithDefaults( state, defaults ) : defaults );
		form.updateModuleStatus( project );
		updateFormState();
	}

	@Override
	public void disposeUIResources() {
		if ( form != null ) {
			form.setDownloadListener( null );
		}
		form						= null;
		useProjectSettingsCheckBox	= null;
		settingsScopeLabel			= null;
	}

	static void clearOverrides( BoxLangProjectSettingsState state, BoxLangSettingsState defaults ) {
		state.boxLangVersion		= null;
		state.boxLangJarPath		= null;
		state.boxLangHome			= null;
		state.javaHome				= null;
		state.lspVersion			= null;
		state.lspBoxLangVersion		= null;
		state.lspBoxLangHome		= null;
		state.lspModules			= null;
		state.lspJvmArgs			= null;
		state.debuggerVersion		= null;
		state.debuggerJarPath		= null;
		state.lspMaxHeapSize		= defaults.lspMaxHeapSize;
		state.useBvmrc				= defaults.useBvmrc;
		state.promptForDownloads	= defaults.promptForDownloads;
	}

	private void updateFormState() {
		if ( form == null || useProjectSettingsCheckBox == null ) {
			return;
		}
		if ( useProjectSettingsCheckBox.isSelected() ) {
			BoxLangProjectSettingsState	state		= BoxLangProjectSettings.getInstance( project ).getSettings();
			BoxLangSettingsState		defaults	= BoxLangApplicationSettings.getInstance().getSettings();
			form.reset( mergeWithDefaults( state, defaults ) );
			if ( settingsScopeLabel != null ) {
				settingsScopeLabel.setText( "Editing project overrides. Uncheck to edit global defaults." );
			}
		} else {
			form.reset( BoxLangApplicationSettings.getInstance().getSettings() );
			if ( settingsScopeLabel != null ) {
				settingsScopeLabel.setText( "Editing global defaults for all projects." );
			}
		}
		form.updateModuleStatus( project );
	}

	private void downloadLsp() {
		BoxLangResolvedSettings	settings	= BoxLangSettingsResolver.resolve( project );
		String					version		= settings.lspVersion;

		ProgressManager.getInstance().run( new Task.Backgroundable( project, "Downloading BoxLang LSP", true ) {

			@Override
			public void run( @NotNull ProgressIndicator indicator ) {
				try {
					ForgeBoxLspDescriptor	descriptor	= ForgeBoxLspResolver.resolve( version );
					Path					targetDir	= BoxLangStoragePaths.getLspCacheRoot()
					    .resolve( descriptor.version )
					    .resolve( "bx-lsp" );
					ForgeBoxLspInstaller.install( descriptor.version, targetDir, indicator );

					// Refresh UI on EDT
					ApplicationManager.getApplication().invokeLater( () -> {
						if ( form != null ) {
							form.updateModuleStatus( project );
						}
					} );
				} catch ( IOException e ) {
					// Error will be shown in indicator or logged
				}
			}
		} );
	}

	private void downloadDebugger() {
		BoxLangResolvedSettings	settings	= BoxLangSettingsResolver.resolve( project );
		String					version		= settings.debuggerVersion;

		ProgressManager.getInstance().run( new Task.Backgroundable( project, "Downloading BoxLang Debugger", true ) {

			@Override
			public void run( @NotNull ProgressIndicator indicator ) {
				try {
					ForgeBoxDebuggerDescriptor	descriptor	= ForgeBoxDebuggerResolver.resolve( version );
					Path						boxLangHome	= BoxLangLspHomeResolver.resolve( project, settings );
					Path						targetDir	= boxLangHome.resolve( "modules" ).resolve( "bx-debugger" );
					ForgeBoxDebuggerInstaller.install( descriptor.version, targetDir, indicator );

					// Refresh UI on EDT
					ApplicationManager.getApplication().invokeLater( () -> {
						if ( form != null ) {
							form.updateModuleStatus( project );
						}
					} );
				} catch ( IOException e ) {
					// Error will be shown in indicator or logged
				}
			}
		} );
	}

	static BoxLangSettingsState mergeWithDefaults( BoxLangProjectSettingsState state, BoxLangSettingsState defaults ) {
		BoxLangSettingsState merged = new BoxLangSettingsState();
		merged.boxLangVersion		= state.boxLangVersion != null ? state.boxLangVersion : defaults.boxLangVersion;
		merged.boxLangJarPath		= state.boxLangJarPath != null ? state.boxLangJarPath : defaults.boxLangJarPath;
		merged.boxLangHome			= state.boxLangHome != null ? state.boxLangHome : defaults.boxLangHome;
		merged.javaHome				= state.javaHome != null ? state.javaHome : defaults.javaHome;
		merged.lspVersion			= state.lspVersion != null ? state.lspVersion : defaults.lspVersion;
		merged.lspBoxLangVersion	= state.lspBoxLangVersion != null ? state.lspBoxLangVersion : defaults.lspBoxLangVersion;
		merged.lspBoxLangHome		= state.lspBoxLangHome != null ? state.lspBoxLangHome : defaults.lspBoxLangHome;
		merged.lspModules			= state.lspModules != null ? state.lspModules : defaults.lspModules;
		merged.lspJvmArgs			= state.lspJvmArgs != null ? state.lspJvmArgs : defaults.lspJvmArgs;
		merged.debuggerVersion		= state.debuggerVersion != null ? state.debuggerVersion : defaults.debuggerVersion;
		merged.debuggerJarPath		= state.debuggerJarPath != null ? state.debuggerJarPath : defaults.debuggerJarPath;
		merged.lspMaxHeapSize		= state.lspMaxHeapSize != 0 ? state.lspMaxHeapSize : defaults.lspMaxHeapSize;
		merged.useBvmrc				= state.useBvmrc;
		merged.promptForDownloads	= state.promptForDownloads;
		return merged;
	}
}
