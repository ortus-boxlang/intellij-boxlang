package com.ortussolutions.intellijboxlang.structure;

import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public final class BoxLangStructureViewBuilder extends TreeBasedStructureViewBuilder {
    private final PsiFile psiFile;

    public BoxLangStructureViewBuilder(PsiFile psiFile) {
        this.psiFile = psiFile;
    }

    @Override
    public @NotNull StructureViewModel createStructureViewModel(Editor editor) {
        return new BoxLangStructureViewModel(psiFile);
    }
}
