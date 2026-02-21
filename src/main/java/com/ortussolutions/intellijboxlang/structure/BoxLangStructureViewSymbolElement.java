package com.ortussolutions.intellijboxlang.structure;

import com.intellij.icons.AllIcons;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Icon;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class BoxLangStructureViewSymbolElement implements StructureViewTreeElement {

	private final PsiFile			psiFile;
	private final DocumentSymbol	symbol;

	public BoxLangStructureViewSymbolElement( PsiFile psiFile, DocumentSymbol symbol ) {
		this.psiFile	= psiFile;
		this.symbol		= symbol;
	}

	@Override
	public Object getValue() {
		return symbol;
	}

	@Override
	public void navigate( boolean requestFocus ) {
		Range range = symbol.getSelectionRange();
		if ( range == null || psiFile.getVirtualFile() == null ) {
			return;
		}
		Document document = PsiDocumentManager.getInstance( psiFile.getProject() ).getDocument( psiFile );
		if ( document == null ) {
			return;
		}
		int	line		= range.getStart().getLine();
		int	character	= range.getStart().getCharacter();
		if ( line >= document.getLineCount() ) {
			return;
		}
		int offset = Math.min( document.getLineStartOffset( line ) + character, document.getTextLength() );
		new OpenFileDescriptor( psiFile.getProject(), psiFile.getVirtualFile(), offset ).navigate( requestFocus );
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
		if ( symbol.getChildren() == null || symbol.getChildren().isEmpty() ) {
			return new StructureViewTreeElement[ 0 ];
		}
		List<StructureViewTreeElement> children = new ArrayList<>();
		for ( DocumentSymbol child : symbol.getChildren() ) {
			children.add( new BoxLangStructureViewSymbolElement( psiFile, child ) );
		}
		return children.toArray( new StructureViewTreeElement[ 0 ] );
	}

	public boolean hasChildren() {
		return symbol.getChildren() != null && !symbol.getChildren().isEmpty();
	}

	@Override
	public @NotNull ItemPresentation getPresentation() {
		return new ItemPresentation() {

			@Override
			public @Nullable String getPresentableText() {
				return symbol.getName();
			}

			@Override
			public @Nullable Icon getIcon( boolean unused ) {
				return iconFor( symbol.getKind() );
			}

			@Override
			public @Nullable String getLocationString() {
				return symbol.getDetail();
			}
		};
	}

	private Icon iconFor( SymbolKind kind ) {
		if ( kind == null ) {
			return AllIcons.Nodes.Variable;
		}
		return switch ( kind ) {
			case Class, Interface, Struct -> AllIcons.Nodes.Class;
			case Method, Function, Constructor -> AllIcons.Nodes.Method;
			case Property, Field -> AllIcons.Nodes.Field;
			case Variable, Constant -> AllIcons.Nodes.Variable;
			case Namespace, Package, Module -> AllIcons.Nodes.Package;
			default -> AllIcons.Nodes.Variable;
		};
	}
}
