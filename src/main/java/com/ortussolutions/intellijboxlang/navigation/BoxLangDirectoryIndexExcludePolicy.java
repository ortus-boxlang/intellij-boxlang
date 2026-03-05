package com.ortussolutions.intellijboxlang.navigation;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.impl.DirectoryIndexExcludePolicy;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.ortussolutions.intellijboxlang.settings.BoxLangStoragePaths;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * Excludes the project-local {@code .boxlang} directory from the project directory index.
 * <p>
 * BoxLang compiles source files to {@code .class} artifacts stored under
 * {@code <projectDir>/.boxlang/}. These compiled files are not useful for editing
 * or navigation and should not appear in search results, file indexes, or
 * code navigation. Excluding the directory at the index level ensures they are
 * completely invisible to all IDE features that rely on the project file index.
 */
public class BoxLangDirectoryIndexExcludePolicy implements DirectoryIndexExcludePolicy {

	private final Project project;

	public BoxLangDirectoryIndexExcludePolicy( @NotNull Project project ) {
		this.project = project;
	}

	@Override
	public String @NotNull [] getExcludeUrlsForProject() {
		Path projectBoxLangHome = BoxLangStoragePaths.getProjectBoxLangHome( project );
		if ( projectBoxLangHome == null ) {
			return new String[ 0 ];
		}
		VirtualFile dir = VirtualFileManager.getInstance()
		    .findFileByNioPath( projectBoxLangHome );
		if ( dir == null ) {
			// Directory may not exist yet — return the URL anyway so it's excluded
			// when it is eventually created
			return new String[] { VirtualFileManager.constructUrl( "file", projectBoxLangHome.toString() ) };
		}
		return new String[] { dir.getUrl() };
	}
}
