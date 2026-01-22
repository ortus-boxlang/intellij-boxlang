package com.ortussolutions.intellijboxlang.parser;

import com.intellij.psi.tree.IFileElementType;
import com.ortussolutions.intellijboxlang.BoxLangLanguage;

public final class BoxLangFileElementType extends IFileElementType {
    public static final BoxLangFileElementType INSTANCE = new BoxLangFileElementType();

    private BoxLangFileElementType() {
        super(BoxLangLanguage.INSTANCE);
    }
}
