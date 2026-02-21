package com.ortussolutions.intellijboxlang.structure;

import com.intellij.lang.PsiStructureViewFactory;
import com.intellij.psi.PsiFile;
import com.intellij.ide.structureView.StructureViewBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class BoxLangStructureViewFactory implements PsiStructureViewFactory {

	@Override
	public @Nullable StructureViewBuilder getStructureViewBuilder( @NotNull PsiFile psiFile ) {
		return new BoxLangStructureViewBuilder( psiFile );
	}
}
