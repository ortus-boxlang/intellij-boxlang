package com.ortussolutions.intellijboxlang.structure;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.navigation.ItemPresentation;
import com.ortussolutions.intellijboxlang.BoxLangIcons;
import com.ortussolutions.intellijboxlang.lsp.BoxLangLspClientService;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Icon;
import org.eclipse.lsp4j.DocumentSymbol;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class BoxLangStructureViewRootElement implements StructureViewTreeElement {

	private final PsiFile psiFile;

	public BoxLangStructureViewRootElement( PsiFile psiFile ) {
		this.psiFile = psiFile;
	}

	@Override
	public Object getValue() {
		return psiFile;
	}

	@Override
	public void navigate( boolean requestFocus ) {
		if ( psiFile.isValid() ) {
			psiFile.navigate( requestFocus );
		}
	}

	@Override
	public boolean canNavigate() {
		return psiFile.isValid();
	}

	@Override
	public boolean canNavigateToSource() {
		return true;
	}

	@Override
	public StructureViewTreeElement @NotNull [] getChildren() {
		Project		project		= psiFile.getProject();
		Document	document	= PsiDocumentManager.getInstance( project ).getDocument( psiFile );
		if ( document == null || psiFile.getVirtualFile() == null ) {
			return new StructureViewTreeElement[ 0 ];
		}
		BoxLangLspClientService			service		= BoxLangLspClientService.getInstance( project );
		List<DocumentSymbol>			symbols		= service.requestDocumentSymbols( psiFile.getVirtualFile(), document );
		List<StructureViewTreeElement>	children	= new ArrayList<>();
		for ( DocumentSymbol symbol : symbols ) {
			children.add( new BoxLangStructureViewSymbolElement( psiFile, symbol ) );
		}
		return children.toArray( new StructureViewTreeElement[ 0 ] );
	}

	@Override
	public @NotNull ItemPresentation getPresentation() {
		return new ItemPresentation() {

			@Override
			public @Nullable String getPresentableText() {
				return psiFile.getName();
			}

			@Override
			public @Nullable Icon getIcon( boolean unused ) {
				return BoxLangIcons.FILE;
			}
		};
	}
}
