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

/**
 * Contributes BoxLang files (.bx, .bxs, .bxm, .cfc, .cfm) to the "Go to File" / Search Everywhere Files tab.
 * This ensures BoxLang files appear in file search results.
 */
public class BoxLangGotoFileContributor implements ChooseByNameContributorEx {

	@Override
	public void processNames( @NotNull Processor<? super String> processor, @NotNull GlobalSearchScope scope, @Nullable IdFilter filter ) {
		Project project = scope.getProject();
		if ( project == null ) {
			return;
		}
		PsiManager psiManager = PsiManager.getInstance( project );
		FileTypeIndex.processFiles( BoxLangFileType.INSTANCE, file -> {
			PsiFile psiFile = psiManager.findFile( file );
			if ( psiFile != null ) {
				processor.process( file.getName() );
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
			if ( file.getName().equals( name ) ) {
				PsiFile psiFile = psiManager.findFile( file );
				if ( psiFile != null ) {
					processor.process( psiFile );
				}
			}
			return true;
		}, parameters.getSearchScope() );
	}
}
