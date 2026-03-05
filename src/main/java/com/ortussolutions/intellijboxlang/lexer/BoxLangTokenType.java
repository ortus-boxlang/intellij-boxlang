package com.ortussolutions.intellijboxlang.lexer;

import com.intellij.psi.tree.IElementType;
import com.ortussolutions.intellijboxlang.BoxLangLanguage;

public class BoxLangTokenType extends IElementType {

	public BoxLangTokenType( String debugName ) {
		super( debugName, BoxLangLanguage.INSTANCE );
	}
}
