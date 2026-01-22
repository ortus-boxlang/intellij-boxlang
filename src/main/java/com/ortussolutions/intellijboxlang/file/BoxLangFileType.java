package com.ortussolutions.intellijboxlang.file;

import com.intellij.openapi.fileTypes.LanguageFileType;
import com.ortussolutions.intellijboxlang.BoxLangIcons;
import com.ortussolutions.intellijboxlang.BoxLangLanguage;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;

public final class BoxLangFileType extends LanguageFileType {
    public static final BoxLangFileType INSTANCE = new BoxLangFileType();

    private BoxLangFileType() {
        super(BoxLangLanguage.INSTANCE);
    }

    @Override
    @NotNull
    public String getName() {
        return "BoxLang";
    }

    @Override
    @NotNull
    public String getDescription() {
        return "BoxLang source file";
    }

    @Override
    @NotNull
    public String getDefaultExtension() {
        return "bx";
    }

    @Override
    public Icon getIcon() {
        return BoxLangIcons.FILE;
    }
}
