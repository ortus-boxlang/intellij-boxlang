package com.ortussolutions.intellijboxlang.runtime;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

public final class BoxLangBvmrcService {

	private static final String		BVMRC_FILE		= ".bvmrc";
	private static final Pattern	VERSION_PATTERN	= Pattern.compile( "^\\d+(\\.\\d+)*$" );

	private BoxLangBvmrcService() {
	}

	public static String findVersion( Project project ) {
		VirtualFile[] roots = ProjectRootManager.getInstance( project ).getContentRoots();
		for ( VirtualFile root : roots ) {
			Path	candidate	= Path.of( root.getPath(), BVMRC_FILE );
			String	version		= readVersion( candidate );
			if ( version != null ) {
				return version;
			}
		}
		String basePath = project.getBasePath();
		if ( basePath != null ) {
			return readVersion( Path.of( basePath, BVMRC_FILE ) );
		}
		return null;
	}

	private static String readVersion( Path path ) {
		if ( !Files.exists( path ) ) {
			return null;
		}
		try {
			String content = Files.readString( path, StandardCharsets.UTF_8 ).trim();
			if ( content.isEmpty() ) {
				return null;
			}
			if ( "latest".equalsIgnoreCase( content ) ) {
				return "latest";
			}
			return VERSION_PATTERN.matcher( content ).matches() ? content : null;
		} catch ( IOException ignored ) {
			return null;
		}
	}
}
