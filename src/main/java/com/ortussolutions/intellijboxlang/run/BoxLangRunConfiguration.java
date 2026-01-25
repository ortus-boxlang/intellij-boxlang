package com.ortussolutions.intellijboxlang.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Run configuration for executing BoxLang scripts.
 */
public class BoxLangRunConfiguration extends RunConfigurationBase<BoxLangRunConfigurationOptions> {

    protected BoxLangRunConfiguration(@NotNull Project project,
                                       @NotNull ConfigurationFactory factory,
                                       @Nullable String name) {
        super(project, factory, name);
    }

    @Override
    protected @NotNull BoxLangRunConfigurationOptions getOptions() {
        return (BoxLangRunConfigurationOptions) super.getOptions();
    }

    public String getScriptPath() {
        return getOptions().getScriptPath();
    }

    public void setScriptPath(String path) {
        getOptions().setScriptPath(path);
    }

    public String getWorkingDirectory() {
        return getOptions().getWorkingDirectory();
    }

    public void setWorkingDirectory(String directory) {
        getOptions().setWorkingDirectory(directory);
    }

    public String getProgramArguments() {
        return getOptions().getProgramArguments();
    }

    public void setProgramArguments(String arguments) {
        getOptions().setProgramArguments(arguments);
    }

    public String getEnvironmentVariables() {
        return getOptions().getEnvironmentVariables();
    }

    public void setEnvironmentVariables(String variables) {
        getOptions().setEnvironmentVariables(variables);
    }

    public String getBoxLangHome() {
        return getOptions().getBoxLangHome();
    }

    public void setBoxLangHome(String home) {
        getOptions().setBoxLangHome(home);
    }

    public String getJvmArgs() {
        return getOptions().getJvmArgs();
    }

    public void setJvmArgs(String args) {
        getOptions().setJvmArgs(args);
    }

    @Override
    public @NotNull SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
        return new BoxLangRunConfigurationEditor(getProject());
    }

    @Override
    public void checkConfiguration() throws RuntimeConfigurationException {
        String scriptPath = getScriptPath();
        if (scriptPath == null || scriptPath.isBlank()) {
            throw new RuntimeConfigurationError("Script path is not specified");
        }
        java.nio.file.Path path = java.nio.file.Path.of(scriptPath);
        if (!java.nio.file.Files.exists(path)) {
            throw new RuntimeConfigurationError("Script file does not exist: " + scriptPath);
        }
    }

    @Override
    public @Nullable RunProfileState getState(@NotNull Executor executor,
                                               @NotNull ExecutionEnvironment environment) throws ExecutionException {
        return new BoxLangRunProfileState(this, environment);
    }

    @Override
    public void readExternal(@NotNull Element element) throws InvalidDataException {
        super.readExternal(element);
    }

    @Override
    public void writeExternal(@NotNull Element element) {
        super.writeExternal(element);
    }
}
