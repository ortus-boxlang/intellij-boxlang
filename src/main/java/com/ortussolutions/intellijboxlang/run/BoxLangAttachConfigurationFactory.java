package com.ortussolutions.intellijboxlang.run;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.components.BaseState;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Factory for creating BoxLang attach run configurations.
 */
public class BoxLangAttachConfigurationFactory extends ConfigurationFactory {

    public BoxLangAttachConfigurationFactory(@NotNull ConfigurationType type) {
        super(type);
    }

    @Override
    public @NotNull @NonNls String getId() {
        return BoxLangAttachConfigurationType.ID;
    }

    @Override
    public @NotNull RunConfiguration createTemplateConfiguration(@NotNull Project project) {
        return new BoxLangAttachRunConfiguration(project, this, "BoxLang Attach");
    }

    @Override
    public @Nullable Class<? extends BaseState> getOptionsClass() {
        return BoxLangAttachRunConfigurationOptions.class;
    }
}
