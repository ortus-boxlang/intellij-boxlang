package com.ortussolutions.intellijboxlang.run;

import com.intellij.execution.configurations.RunConfigurationOptions;
import com.intellij.openapi.components.StoredProperty;

/**
 * Stores the options for a BoxLang run configuration.
 * These options are persisted and restored when the IDE restarts.
 */
public class BoxLangRunConfigurationOptions extends RunConfigurationOptions {

    private final StoredProperty<String> scriptPath = string("")
            .provideDelegate(this, "scriptPath");

    private final StoredProperty<String> workingDirectory = string("")
            .provideDelegate(this, "workingDirectory");

    private final StoredProperty<String> programArguments = string("")
            .provideDelegate(this, "programArguments");

    private final StoredProperty<String> environmentVariables = string("")
            .provideDelegate(this, "environmentVariables");

    private final StoredProperty<String> boxLangHome = string("")
            .provideDelegate(this, "boxLangHome");

    private final StoredProperty<String> jvmArgs = string("")
            .provideDelegate(this, "jvmArgs");

    public String getScriptPath() {
        return scriptPath.getValue(this);
    }

    public void setScriptPath(String path) {
        scriptPath.setValue(this, path);
    }

    public String getWorkingDirectory() {
        return workingDirectory.getValue(this);
    }

    public void setWorkingDirectory(String directory) {
        workingDirectory.setValue(this, directory);
    }

    public String getProgramArguments() {
        return programArguments.getValue(this);
    }

    public void setProgramArguments(String arguments) {
        programArguments.setValue(this, arguments);
    }

    public String getEnvironmentVariables() {
        return environmentVariables.getValue(this);
    }

    public void setEnvironmentVariables(String variables) {
        environmentVariables.setValue(this, variables);
    }

    public String getBoxLangHome() {
        return boxLangHome.getValue(this);
    }

    public void setBoxLangHome(String home) {
        boxLangHome.setValue(this, home);
    }

    public String getJvmArgs() {
        return jvmArgs.getValue(this);
    }

    public void setJvmArgs(String args) {
        jvmArgs.setValue(this, args);
    }
}
