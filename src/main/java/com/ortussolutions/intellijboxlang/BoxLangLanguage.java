package com.ortussolutions.intellijboxlang;

import com.intellij.lang.Language;

public final class BoxLangLanguage extends Language {

	public static final BoxLangLanguage INSTANCE = new BoxLangLanguage();

	private BoxLangLanguage() {
		super( "BoxLang" );
	}
}
