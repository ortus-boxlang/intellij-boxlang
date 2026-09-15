package com.ortussolutions.intellijboxlang.cfml;

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import java.util.Locale;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

public final class CfmlClosingTagHandler extends TypedHandlerDelegate {

	// Only block tags: optional-body/action tags and custom tags are deliberately excluded.
	private static final Set<String> BLOCK_TAGS = Set.of(
	    "cfif", "cfoutput", "cfloop", "cffunction", "cfcomponent", "cfinterface", "cfscript", "cfquery",
	    "cftry", "cfcatch", "cffinally", "cfswitch", "cfcase", "cfdefaultcase", "cfsavecontent",
	    "cfsilent", "cflock", "cfform", "cfdocument", "cfdocumentsection", "cfdocumentitem",
	    "cfhtmltopdf", "cfmail", "cfmailpart", "cfselect" );

	@Override
	public @NotNull Result charTyped( char c, @NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file ) {
		if ( c != '>' || !CfmlEditorContext.isCfml( file ) )
			return Result.CONTINUE;
		int						offset	= editor.getCaretModel().getOffset();
		var						source	= editor.getDocument().getCharsSequence();
		var						tags	= CfmlEditorContext.tags( source );
		CfmlEditorContext.Tag	current	= null;
		for ( var tag : tags )
			if ( tag.end() == offset && tag.complete() )
				current = tag;
		if ( current == null || current.closing() || current.selfClosing()
		    || !BLOCK_TAGS.contains( current.name().toLowerCase( Locale.ROOT ) ) )
			return Result.CONTINUE;
		int	ancestors			= 0;
		int	suffixBalance		= 0;
		int	availableClosers	= 0;
		for ( var tag : tags ) {
			if ( !tag.complete() || tag.selfClosing() || !tag.name().equalsIgnoreCase( current.name() ) )
				continue;
			if ( tag.start() < current.start() )
				ancestors = Math.max( 0, ancestors + ( tag.closing() ? -1 : 1 ) );
			else if ( tag.start() > current.start() ) {
				suffixBalance		+= tag.closing() ? 1 : -1;
				availableClosers	= Math.max( availableClosers, suffixBalance );
			}
		}
		if ( availableClosers > ancestors )
			return Result.CONTINUE;
		editor.getDocument().insertString( offset, "</" + current.name() + ">" );
		editor.getCaretModel().moveToOffset( offset );
		return Result.STOP;
	}
}
