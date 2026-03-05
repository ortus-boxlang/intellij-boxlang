package com.ortussolutions.intellijboxlang.navigation;

import com.intellij.navigation.ChooseByNameContributorEx;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Processor;
import com.intellij.util.indexing.FindSymbolParameters;
import com.intellij.util.indexing.IdFilter;
import com.ortussolutions.intellijboxlang.file.BoxLangFileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Set;

/**
 * Contributes BoxLang class files (.bx, .cfc) to the "Go to Class" / Search Everywhere Classes tab.
 * Files with .bx or .cfc extensions are treated as class definitions.
 */
public class BoxLangGotoClassContributor implements ChooseByNameContributorEx {

	private static final Set<String> CLASS_EXTENSIONS = Set.of( "bx", "cfc" );

	@Override
	public void processNames( @NotNull Processor<? super String> processor, @NotNull GlobalSearchScope scope, @Nullable IdFilter filter ) {
		Project project = scope.getProject();
		if ( project == null ) {
			return;
		}
		PsiManager psiManager = PsiManager.getInstance( project );
		FileTypeIndex.processFiles( BoxLangFileType.INSTANCE, file -> {
			if ( isClassExtension( file.getExtension() ) ) {
				PsiFile psiFile = psiManager.findFile( file );
				if ( psiFile != null ) {
					// Use the file name without extension as the class name
					String className = file.getNameWithoutExtension();
					processor.process( className );
				}
			}
			return true;
		}, scope );
	}

	@Override
	public void processElementsWithName( @NotNull String name, @NotNull Processor<? super NavigationItem> processor,
	    @NotNull FindSymbolParameters parameters ) {
		Project		project		= parameters.getProject();
		PsiManager	psiManager	= PsiManager.getInstance( project );
		FileTypeIndex.processFiles( BoxLangFileType.INSTANCE, file -> {
			if ( isClassExtension( file.getExtension() ) && file.getNameWithoutExtension().equals( name ) ) {
				PsiFile psiFile = psiManager.findFile( file );
				if ( psiFile != null ) {
					processor.process( psiFile );
				}
			}
			return true;
		}, parameters.getSearchScope() );
	}

	private static boolean isClassExtension( @Nullable String extension ) {
		return extension != null && CLASS_EXTENSIONS.contains( extension.toLowerCase( Locale.ROOT ) );
	}
}
