package com.ortussolutions.intellijboxlang.run;

import com.intellij.execution.configurations.RunConfigurationOptions;
import com.intellij.openapi.components.StoredProperty;

/**
 * Stores the options for a BoxLang attach run configuration.
 * These options are persisted and restored when the IDE restarts.
 */
public class BoxLangAttachRunConfigurationOptions extends RunConfigurationOptions {

    private final StoredProperty<String> host = string("localhost")
            .provideDelegate(this, "host");

    private final StoredProperty<Integer> jdwpPort = property(5005)
            .provideDelegate(this, "jdwpPort");

    private final StoredProperty<String> localRoot = string("")
            .provideDelegate(this, "localRoot");

    private final StoredProperty<String> remoteRoot = string("")
            .provideDelegate(this, "remoteRoot");

    private final StoredProperty<String> boxLangHome = string("")
            .provideDelegate(this, "boxLangHome");

    public String getHost() {
        return host.getValue(this);
    }

    public void setHost(String value) {
        host.setValue(this, value);
    }

    public int getJdwpPort() {
        return jdwpPort.getValue(this);
    }

    public void setJdwpPort(int value) {
        jdwpPort.setValue(this, value);
    }

    public String getLocalRoot() {
        return localRoot.getValue(this);
    }

    public void setLocalRoot(String value) {
        localRoot.setValue(this, value);
    }

    public String getRemoteRoot() {
        return remoteRoot.getValue(this);
    }

    public void setRemoteRoot(String value) {
        remoteRoot.setValue(this, value);
    }

    public String getBoxLangHome() {
        return boxLangHome.getValue(this);
    }

    public void setBoxLangHome(String value) {
        boxLangHome.setValue(this, value);
    }
}
