package com.ortussolutions.intellijboxlang.actions;

import com.intellij.ide.actions.CreateFileFromTemplateAction;
import com.intellij.ide.actions.CreateFileFromTemplateDialog;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.ortussolutions.intellijboxlang.BoxLangIcons;
import org.jetbrains.annotations.NotNull;

public final class BoxLangCreateFileAction extends CreateFileFromTemplateAction implements DumbAware {

	private static final String	CLASS_TEMPLATE_NAME		= "BoxLang Class";
	private static final String	SCRIPT_TEMPLATE_NAME	= "BoxLang Script";
	private static final String	TEMPLATE_TEMPLATE_NAME	= "BoxLang Template";

	public BoxLangCreateFileAction() {
		super( "BoxLang File", "Create a BoxLang file", BoxLangIcons.FILE );
	}

	@Override
	protected void buildDialog( @NotNull Project project, @NotNull PsiDirectory directory,
	    @NotNull CreateFileFromTemplateDialog.Builder builder ) {
		builder.setTitle( "New BoxLang File" )
		    .addKind( "BoxLang Class", BoxLangIcons.FILE, CLASS_TEMPLATE_NAME )
		    .addKind( "BoxLang Script", BoxLangIcons.FILE, SCRIPT_TEMPLATE_NAME )
		    .addKind( "BoxLang Template", BoxLangIcons.FILE, TEMPLATE_TEMPLATE_NAME );
	}

	@Override
	protected String getActionName( @NotNull PsiDirectory directory, @NotNull String newName,
	    @NotNull String templateName ) {
		return "Create BoxLang file";
	}
}
