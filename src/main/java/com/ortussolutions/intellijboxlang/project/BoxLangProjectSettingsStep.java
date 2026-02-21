package com.ortussolutions.intellijboxlang.project;

import com.intellij.ide.util.projectWizard.AbstractNewProjectStep;
import com.intellij.ide.util.projectWizard.ProjectSettingsStepBase;
import com.intellij.platform.DirectoryProjectGenerator;
import org.jetbrains.annotations.NotNull;

/**
 * Settings step for the BoxLang New Project wizard.
 */
public final class BoxLangProjectSettingsStep extends ProjectSettingsStepBase<BoxLangProjectSettings> {

	public BoxLangProjectSettingsStep(
	    DirectoryProjectGenerator<BoxLangProjectSettings> projectGenerator,
	    AbstractNewProjectStep.AbstractCallback<BoxLangProjectSettings> callback ) {
		super( projectGenerator, callback );
	}
}
