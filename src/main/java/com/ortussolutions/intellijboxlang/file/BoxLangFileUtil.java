package com.ortussolutions.intellijboxlang.file;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Shared helpers for determining whether a file/path/extension is BoxLang.
 */
public final class BoxLangFileUtil {

	private BoxLangFileUtil() {
	}

	public static boolean isBoxLangFile( @Nullable VirtualFile file ) {
		return file != null && isBoxLangExtension( file.getExtension() );
	}

	public static boolean isBoxLangExtension( @Nullable String extension ) {
		if ( extension == null || extension.isBlank() ) {
			return false;
		}

		String normalized = extension.toLowerCase( Locale.ROOT );
		return normalized.equals( "bx" )
		    || normalized.equals( "bxs" )
		    || normalized.equals( "bxm" );
	}
}
