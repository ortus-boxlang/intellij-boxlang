package com.ortussolutions.intellijboxlang.debug;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xdebugger.breakpoints.XLineBreakpointType;
import com.ortussolutions.intellijboxlang.file.BoxLangFileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Defines the line breakpoint type for BoxLang files.
 * This allows users to set breakpoints in .bx, .bxs, and .bxm files.
 */
public class BoxLangLineBreakpointType extends XLineBreakpointType<BoxLangBreakpointProperties> {

	public static final String	ID		= "boxlang-line";
	public static final String	NAME	= "BoxLang Line Breakpoints";

	public BoxLangLineBreakpointType() {
		super( ID, NAME );
	}

	/**
	 * Determines whether a breakpoint can be set at the given line in the file.
	 * Returns true for all BoxLang files (.bx, .bxs, .bxm).
	 */
	@Override
	public boolean canPutAt( @NotNull VirtualFile file, int line, @NotNull Project project ) {
		return file.getFileType() instanceof BoxLangFileType;
	}

	/**
	 * Creates breakpoint properties for a new breakpoint.
	 * Returns null if no special properties are needed.
	 */
	@Nullable
	@Override
	public BoxLangBreakpointProperties createBreakpointProperties( @NotNull VirtualFile file, int line ) {
		return new BoxLangBreakpointProperties();
	}

	/**
	 * Returns the priority of this breakpoint type.
	 * Higher priority means this type will be preferred when multiple types can handle a location.
	 */
	@Override
	public int getPriority() {
		return 100;
	}
}
