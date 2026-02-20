package com.ortussolutions.intellijboxlang.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Run configuration for attaching the BoxLang debugger to a running process.
 * Unlike BoxLangRunConfiguration which launches a new process, this configuration
 * connects to an already-running BoxLang process (e.g., MiniServer) via JDWP.
 */
public class BoxLangAttachRunConfiguration extends RunConfigurationBase<BoxLangAttachRunConfigurationOptions> {

    protected BoxLangAttachRunConfiguration(@NotNull Project project,
                                             @NotNull ConfigurationFactory factory,
                                             @Nullable String name) {
        super(project, factory, name);
    }

    @Override
    protected @NotNull BoxLangAttachRunConfigurationOptions getOptions() {
        return (BoxLangAttachRunConfigurationOptions) super.getOptions();
    }

    public String getHost() {
        return getOptions().getHost();
    }

    public void setHost(String host) {
        getOptions().setHost(host);
    }

    public int getJdwpPort() {
        return getOptions().getJdwpPort();
    }

    public void setJdwpPort(int port) {
        getOptions().setJdwpPort(port);
    }

    public String getLocalRoot() {
        return getOptions().getLocalRoot();
    }

    public void setLocalRoot(String localRoot) {
        getOptions().setLocalRoot(localRoot);
    }

    public String getRemoteRoot() {
        return getOptions().getRemoteRoot();
    }

    public void setRemoteRoot(String remoteRoot) {
        getOptions().setRemoteRoot(remoteRoot);
    }

    public String getBoxLangHome() {
        return getOptions().getBoxLangHome();
    }

    public void setBoxLangHome(String home) {
        getOptions().setBoxLangHome(home);
    }

    @Override
    public @NotNull SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
        return new BoxLangAttachRunConfigurationEditor(getProject());
    }

    @Override
    public void checkConfiguration() throws RuntimeConfigurationException {
        String host = getHost();
        if (host == null || host.isBlank()) {
            throw new RuntimeConfigurationError("Host must not be empty");
        }

        int port = getJdwpPort();
        if (port < 1 || port > 65535) {
            throw new RuntimeConfigurationError("JDWP port must be between 1 and 65535");
        }
    }

    @Override
    public @Nullable RunProfileState getState(@NotNull Executor executor,
                                               @NotNull ExecutionEnvironment environment) throws ExecutionException {
        // Return a minimal RunProfileState. The actual work is done by BoxLangAttachDebugRunner,
        // but GenericProgramRunner requires a non-null state to proceed.
        return (executor1, runner) -> null;
    }
}
