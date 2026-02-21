package com.ortussolutions.intellijboxlang.debug;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.EvaluationMode;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.ortussolutions.intellijboxlang.file.BoxLangFileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides editor support for expression evaluation in the debugger.
 * This allows users to type BoxLang expressions in watch windows, evaluate dialogs, etc.
 */
public class BoxLangDebuggerEditorsProvider extends XDebuggerEditorsProvider {

	@Override
	public @NotNull FileType getFileType() {
		return BoxLangFileType.INSTANCE;
	}

	@Override
	public @NotNull Document createDocument( @NotNull Project project,
	    @NotNull String text,
	    @Nullable XSourcePosition sourcePosition,
	    @NotNull EvaluationMode mode ) {
		return EditorFactory.getInstance().createDocument( text );
	}
}
