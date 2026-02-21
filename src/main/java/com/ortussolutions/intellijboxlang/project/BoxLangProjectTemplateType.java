package com.ortussolutions.intellijboxlang.project;

/**
 * Defines the available BoxLang project template types.
 */
public enum BoxLangProjectTemplateType {

	MINISERVER( "MiniServer Web Application", "A BoxLang web application using MiniServer" ),
	COMMANDBOX( "CommandBox Web Application", "A BoxLang web application using CommandBox with server.json" ),
	COMMAND_LINE( "Command Line Application", "A BoxLang command line application" ),
	MODULE( "Module", "A reusable BoxLang module" );

	private final String	displayName;
	private final String	description;

	BoxLangProjectTemplateType( String displayName, String description ) {
		this.displayName	= displayName;
		this.description	= description;
	}

	public String getDisplayName() {
		return displayName;
	}

	public String getDescription() {
		return description;
	}

	@Override
	public String toString() {
		return displayName;
	}
}
