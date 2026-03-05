package com.ortussolutions.intellijboxlang.testbox;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.icons.AllIcons;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Defines the TestBox run configuration type that appears in the Run/Debug Configurations dialog.
 */
public class TestBoxConfigurationType implements ConfigurationType {

	public static final String ID = "TestBoxRunConfiguration";

	@Override
	public @NotNull @Nls( capitalization = Nls.Capitalization.Title ) String getDisplayName() {
		return "TestBox";
	}

	@Override
	public @Nls( capitalization = Nls.Capitalization.Sentence ) String getConfigurationTypeDescription() {
		return "Run TestBox tests";
	}

	@Override
	public Icon getIcon() {
		return AllIcons.RunConfigurations.TestState.Run;
	}

	@Override
	public @NotNull @NonNls String getId() {
		return ID;
	}

	@Override
	public ConfigurationFactory[] getConfigurationFactories() {
		return new ConfigurationFactory[] { new TestBoxConfigurationFactory( this ) };
	}
}
