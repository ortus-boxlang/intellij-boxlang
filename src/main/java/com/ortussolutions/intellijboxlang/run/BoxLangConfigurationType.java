package com.ortussolutions.intellijboxlang.run;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.ortussolutions.intellijboxlang.BoxLangIcons;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Defines the BoxLang run configuration type that appears in the Run/Debug Configurations dialog.
 */
public class BoxLangConfigurationType implements ConfigurationType {

	public static final String ID = "BoxLangRunConfiguration";

	@Override
	public @NotNull @Nls( capitalization = Nls.Capitalization.Title ) String getDisplayName() {
		return "BoxLang";
	}

	@Override
	public @Nls( capitalization = Nls.Capitalization.Sentence ) String getConfigurationTypeDescription() {
		return "Run a BoxLang script";
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
		return new ConfigurationFactory[] { new BoxLangConfigurationFactory( this ) };
	}
}
