package com.ortussolutions.intellijboxlang.testbox;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.components.BaseState;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Factory for creating TestBox run configurations.
 */
public class TestBoxConfigurationFactory extends ConfigurationFactory {

	public TestBoxConfigurationFactory( @NotNull ConfigurationType type ) {
		super( type );
	}

	@Override
	public @NotNull @NonNls String getId() {
		return TestBoxConfigurationType.ID;
	}

	@Override
	public @NotNull RunConfiguration createTemplateConfiguration( @NotNull Project project ) {
		return new TestBoxRunConfiguration( project, this, "TestBox" );
	}

	@Override
	public @Nullable Class<? extends BaseState> getOptionsClass() {
		return TestBoxRunConfigurationOptions.class;
	}
}
