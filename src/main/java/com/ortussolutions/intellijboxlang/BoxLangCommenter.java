package com.ortussolutions.intellijboxlang;

import com.intellij.lang.Commenter;
import org.jetbrains.annotations.Nullable;

/**
 * Provides comment toggling support for BoxLang files.
 *
 * Line comments use {@code //} syntax.
 * Block comments use {@code /* ... * /} syntax.
 */
public class BoxLangCommenter implements Commenter {

	@Override
	public @Nullable String getLineCommentPrefix() {
		return "//";
	}

	@Override
	public @Nullable String getBlockCommentPrefix() {
		return "/*";
	}

	@Override
	public @Nullable String getBlockCommentSuffix() {
		return "*/";
	}

	@Override
	public @Nullable String getCommentedBlockCommentPrefix() {
		return null;
	}

	@Override
	public @Nullable String getCommentedBlockCommentSuffix() {
		return null;
	}
}
