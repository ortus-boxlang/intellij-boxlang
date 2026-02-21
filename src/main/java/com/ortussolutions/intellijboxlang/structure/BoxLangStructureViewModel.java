package com.ortussolutions.intellijboxlang.structure;

import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.StructureViewModelBase;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class BoxLangStructureViewModel extends StructureViewModelBase
    implements StructureViewModel.ElementInfoProvider {

	public BoxLangStructureViewModel( PsiFile psiFile ) {
		super( psiFile, new BoxLangStructureViewRootElement( psiFile ) );
		withSuitableClasses( PsiFile.class );
	}

	@Override
	public boolean isAlwaysShowsPlus( StructureViewTreeElement element ) {
		return element instanceof BoxLangStructureViewRootElement;
	}

	@Override
	public boolean isAlwaysLeaf( StructureViewTreeElement element ) {
		return element instanceof BoxLangStructureViewSymbolElement symbol && !symbol.hasChildren();
	}
}
