package com.ortussolutions.intellijboxlang.testbox;

import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.testframework.sm.runner.SMTestLocator;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Locates test source from a protocol URL used in the SMTRunner test tree.
 *
 * URL format: testbox://filePath
 * Example: testbox:///Users/dev/project/tests/specs/MySpec.bx
 */
public class TestBoxTestLocator implements SMTestLocator, DumbAware {

	public static final String				PROTOCOL	= "testbox";
	public static final TestBoxTestLocator	INSTANCE	= new TestBoxTestLocator();

	@Override
	public @NotNull List<Location> getLocation(
	    @NotNull String protocol,
	    @NotNull String path,
	    @NotNull Project project,
	    @NotNull GlobalSearchScope scope ) {
		if ( !PROTOCOL.equals( protocol ) ) {
			return Collections.emptyList();
		}

		return getLocationForPath( path, project );
	}

	private @NotNull List<Location> getLocationForPath( @NotNull String path, @NotNull Project project ) {
		// Path may be a file path, optionally with ::lineNumber suffix
		int			colonIndex	= path.lastIndexOf( "::" );
		String		filePath	= colonIndex > 0 ? path.substring( 0, colonIndex ) : path;

		VirtualFile	vFile		= LocalFileSystem.getInstance().findFileByPath( filePath );
		if ( vFile == null ) {
			return Collections.emptyList();
		}

		PsiFile psiFile = PsiManager.getInstance( project ).findFile( vFile );
		if ( psiFile == null ) {
			return Collections.emptyList();
		}

		return Collections.singletonList( new PsiLocation<>( psiFile ) );
	}
}
