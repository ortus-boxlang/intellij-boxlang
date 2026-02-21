package com.ortussolutions.intellijboxlang.project;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Holds the settings selected in the New Project wizard for BoxLang projects.
 */
public final class BoxLangProjectSettings {

	private GitHubTemplate	selectedTemplate;
	private String			boxLangVersion	= "";

	public BoxLangProjectSettings() {
		// Default to the first built-in template
		this.selectedTemplate = GitHubTemplate.fromBuiltIn( BoxLangProjectTemplateType.COMMAND_LINE );
	}

	@NotNull
	public GitHubTemplate getSelectedTemplate() {
		return selectedTemplate;
	}

	public void setSelectedTemplate( @Nullable GitHubTemplate selectedTemplate ) {
		if ( selectedTemplate != null ) {
			this.selectedTemplate = selectedTemplate;
		}
	}

	/**
	 * For backward compatibility - returns the template type if it's a built-in template.
	 */
	@Nullable
	public BoxLangProjectTemplateType getTemplateType() {
		if ( selectedTemplate == null || !selectedTemplate.isBuiltIn() ) {
			return null;
		}
		String name = selectedTemplate.getName().toUpperCase().replace( "-", "_" );
		try {
			return BoxLangProjectTemplateType.valueOf( name );
		} catch ( IllegalArgumentException e ) {
			return null;
		}
	}

	/**
	 * For backward compatibility - sets the template from a built-in type.
	 */
	public void setTemplateType( @NotNull BoxLangProjectTemplateType templateType ) {
		this.selectedTemplate = GitHubTemplate.fromBuiltIn( templateType );
	}

	@NotNull
	public String getBoxLangVersion() {
		return boxLangVersion;
	}

	public void setBoxLangVersion( @Nullable String boxLangVersion ) {
		this.boxLangVersion = boxLangVersion != null ? boxLangVersion : "";
	}
}
