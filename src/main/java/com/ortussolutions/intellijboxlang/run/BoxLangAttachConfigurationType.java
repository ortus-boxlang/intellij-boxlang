package com.ortussolutions.intellijboxlang.run;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.ortussolutions.intellijboxlang.BoxLangIcons;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Defines the BoxLang attach run configuration type that appears in the Run/Debug Configurations dialog.
 * This allows users to attach the debugger to an already-running BoxLang process (e.g., MiniServer).
 */
public class BoxLangAttachConfigurationType implements ConfigurationType {

    public static final String ID = "BoxLangAttachRunConfiguration";

    @Override
    public @NotNull @Nls(capitalization = Nls.Capitalization.Title) String getDisplayName() {
        return "BoxLang Attach";
    }

    @Override
    public @Nls(capitalization = Nls.Capitalization.Sentence) String getConfigurationTypeDescription() {
        return "Attach to a running BoxLang process for debugging";
    }

    @Override
    public Icon getIcon() {
        return BoxLangIcons.FILE;
    }

    @Override
    public @NotNull @NonNls String getId() {
        return ID;
    }

    @Override
    public ConfigurationFactory[] getConfigurationFactories() {
        return new ConfigurationFactory[]{new BoxLangAttachConfigurationFactory(this)};
    }
}
