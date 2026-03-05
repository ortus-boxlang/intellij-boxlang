package com.ortussolutions.intellijboxlang.parser;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.openapi.fileTypes.FileType;
import com.ortussolutions.intellijboxlang.BoxLangLanguage;
import com.ortussolutions.intellijboxlang.file.BoxLangFileType;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;

public final class BoxLangFile extends PsiFileBase {

	public BoxLangFile( @NotNull FileViewProvider viewProvider ) {
		super( viewProvider, BoxLangLanguage.INSTANCE );
	}

	@Override
	public @NotNull IFileElementType getFileElementType() {
		return BoxLangFileElementType.INSTANCE;
	}

	@Override
	public @NotNull FileType getFileType() {
		return BoxLangFileType.INSTANCE;
	}

	@Override
	public @NotNull String toString() {
		return "BoxLang File";
	}

	@Override
	public Icon getIcon( int flags ) {
		return BoxLangFileType.INSTANCE.getIcon();
	}
}
