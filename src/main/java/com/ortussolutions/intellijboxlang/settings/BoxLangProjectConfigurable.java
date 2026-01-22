package com.ortussolutions.intellijboxlang.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.FormBuilder;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

public final class BoxLangProjectConfigurable implements Configurable {
    private final Project project;
    private BoxLangSettingsForm form;
    private JBCheckBox useProjectSettingsCheckBox;
    private JBLabel settingsScopeLabel;

    public BoxLangProjectConfigurable(Project project) {
        this.project = project;
    }

    @Override
    public @Nls(capitalization = Nls.Capitalization.Title) String getDisplayName() {
        return "BoxLang";
    }

    @Override
    public @Nullable JComponent createComponent() {
        form = new BoxLangSettingsForm();
        useProjectSettingsCheckBox = new JBCheckBox("Override global settings for this project");
        settingsScopeLabel = new JBLabel();
        useProjectSettingsCheckBox.addActionListener(event -> updateFormState());

        JPanel panel = FormBuilder.createFormBuilder()
            .addComponent(useProjectSettingsCheckBox)
            .addComponent(settingsScopeLabel)
            .addComponent(form.getPanel())
            .getPanel();

        return panel;
    }

    @Override
    public boolean isModified() {
        if (form == null || useProjectSettingsCheckBox == null) {
            return false;
        }
        BoxLangProjectSettingsState state = BoxLangProjectSettings.getInstance(project).getSettings();
        BoxLangSettingsState defaults = BoxLangApplicationSettings.getInstance().getSettings();
        if (useProjectSettingsCheckBox.isSelected() != state.useProjectSettings) {
            return true;
        }
        if (!useProjectSettingsCheckBox.isSelected()) {
            return form.isModified(defaults);
        }
        return form.isModified(mergeWithDefaults(state, defaults));
    }

    @Override
    public void apply() {
        BoxLangProjectSettingsState state = BoxLangProjectSettings.getInstance(project).getSettings();
        state.useProjectSettings = useProjectSettingsCheckBox.isSelected();
        if (state.useProjectSettings) {
            form.apply(state);
        } else {
            clearOverrides(state);
            form.apply(BoxLangApplicationSettings.getInstance().getSettings());
        }
    }

    @Override
    public void reset() {
        if (form == null) {
            return;
        }

        BoxLangProjectSettingsState state = BoxLangProjectSettings.getInstance(project).getSettings();
        BoxLangSettingsState defaults = BoxLangApplicationSettings.getInstance().getSettings();
        useProjectSettingsCheckBox.setSelected(state.useProjectSettings);
        form.reset(state.useProjectSettings ? mergeWithDefaults(state, defaults) : defaults);
        updateFormState();
    }

    @Override
    public void disposeUIResources() {
        form = null;
        useProjectSettingsCheckBox = null;
        settingsScopeLabel = null;
    }

    private void clearOverrides(BoxLangProjectSettingsState state) {
        state.boxLangVersion = null;
        state.boxLangJarPath = null;
        state.boxLangHome = null;
        state.javaHome = null;
        state.lspVersion = null;
        state.lspBoxLangVersion = null;
        state.lspBoxLangHome = null;
        state.lspModules = null;
        state.lspJvmArgs = null;
        state.lspMaxHeapSize = BoxLangApplicationSettings.getInstance().getSettings().lspMaxHeapSize;
        state.useBvmrc = BoxLangApplicationSettings.getInstance().getSettings().useBvmrc;
        state.promptForDownloads = BoxLangApplicationSettings.getInstance().getSettings().promptForDownloads;
    }

    private void updateFormState() {
        if (form == null || useProjectSettingsCheckBox == null) {
            return;
        }
        if (useProjectSettingsCheckBox.isSelected()) {
            BoxLangProjectSettingsState state = BoxLangProjectSettings.getInstance(project).getSettings();
            BoxLangSettingsState defaults = BoxLangApplicationSettings.getInstance().getSettings();
            form.reset(mergeWithDefaults(state, defaults));
            if (settingsScopeLabel != null) {
                settingsScopeLabel.setText("Editing project overrides. Uncheck to edit global defaults.");
            }
        } else {
            form.reset(BoxLangApplicationSettings.getInstance().getSettings());
            if (settingsScopeLabel != null) {
                settingsScopeLabel.setText("Editing global defaults for all projects.");
            }
        }
    }

    private BoxLangSettingsState mergeWithDefaults(BoxLangProjectSettingsState state, BoxLangSettingsState defaults) {
        BoxLangSettingsState merged = new BoxLangSettingsState();
        merged.boxLangVersion = state.boxLangVersion != null ? state.boxLangVersion : defaults.boxLangVersion;
        merged.boxLangJarPath = state.boxLangJarPath != null ? state.boxLangJarPath : defaults.boxLangJarPath;
        merged.boxLangHome = state.boxLangHome != null ? state.boxLangHome : defaults.boxLangHome;
        merged.javaHome = state.javaHome != null ? state.javaHome : defaults.javaHome;
        merged.lspVersion = state.lspVersion != null ? state.lspVersion : defaults.lspVersion;
        merged.lspBoxLangVersion = state.lspBoxLangVersion != null ? state.lspBoxLangVersion : defaults.lspBoxLangVersion;
        merged.lspBoxLangHome = state.lspBoxLangHome != null ? state.lspBoxLangHome : defaults.lspBoxLangHome;
        merged.lspModules = state.lspModules != null ? state.lspModules : defaults.lspModules;
        merged.lspJvmArgs = state.lspJvmArgs != null ? state.lspJvmArgs : defaults.lspJvmArgs;
        merged.lspMaxHeapSize = state.lspMaxHeapSize != 0 ? state.lspMaxHeapSize : defaults.lspMaxHeapSize;
        merged.useBvmrc = state.useBvmrc;
        merged.promptForDownloads = state.promptForDownloads;
        return merged;
    }
}
