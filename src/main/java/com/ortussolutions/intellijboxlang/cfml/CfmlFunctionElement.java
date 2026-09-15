package com.ortussolutions.intellijboxlang.cfml;

import com.intellij.psi.impl.FakePsiElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.openapi.util.TextRange;

final class CfmlFunctionElement extends FakePsiElement {

	final CfmlFunctionCatalog.Function	function;
	final int							offset;
	final int							parameter;
	final int							argumentStart;
	final java.util.Set<String>			suppliedArguments;
	private final PsiFile				file;

	CfmlFunctionElement( PsiFile file, CfmlFunctionCatalog.Function function, int offset, int parameter ) {
		this( file, function, offset, parameter, offset + 1, java.util.Set.of() );
	}

	CfmlFunctionElement( PsiFile file, CfmlFunctionCatalog.Function function, int offset, int parameter, int argumentStart,
	    java.util.Set<String> suppliedArguments ) {
		this.argumentStart		= argumentStart;
		this.suppliedArguments	= java.util.Set.copyOf( suppliedArguments );
		this.file				= file;
		this.function			= function;
		this.offset				= offset;
		this.parameter			= parameter;
	}

	@Override
	public com.intellij.lang.Language getLanguage() {
		return file.getLanguage();
	}

	@Override
	public PsiElement getParent() {
		return file;
	}

	@Override
	public String getName() {
		return function.name();
	}

	@Override
	public TextRange getTextRange() {
		return TextRange.from( offset, 1 );
	}

	@Override
	public int getTextOffset() {
		return offset;
	}
}
