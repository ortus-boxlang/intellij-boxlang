package com.ortussolutions.intellijboxlang.navigation;

import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper;
import com.intellij.ide.actions.searcheverywhere.SEResultsEqualityProvider;
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereFoundElementInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Unconditionally hides compiled .class files that originate from BoxLang/CFML sources.
 * <p>
 * Any .class file whose name matches BoxLang compilation patterns (e.g.
 * {@code Querybuilder$cfc.class}, {@code Foo$bx.class}, {@code Bar$cfc$Closure_1.class})
 * is skipped from Search Everywhere results regardless of whether a corresponding source
 * file is present. These artifacts are never useful in search and only clutter results.
 */
public class BoxLangSearchEqualityProvider implements SEResultsEqualityProvider {

	@Override
	public @NotNull SEEqualElementsActionType compareItems(
	    @NotNull SearchEverywhereFoundElementInfo newItem,
	    @NotNull List<? extends SearchEverywhereFoundElementInfo> alreadyFoundItems ) {
		Object newElement = newItem.getElement();
		if ( newElement instanceof BoxLangSymbolNavigationItem newSymbolItem ) {
			String newKey = newSymbolItem.getDeduplicationKey();
			for ( SearchEverywhereFoundElementInfo existing : alreadyFoundItems ) {
				Object existingElement = existing.getElement();
				if ( existingElement instanceof BoxLangSymbolNavigationItem existingSymbolItem
				    && newKey.equals( existingSymbolItem.getDeduplicationKey() ) ) {
					return SEEqualElementsActionType.Skip.INSTANCE;
				}
			}
		}

		VirtualFile newFile = extractFile( newItem );
		if ( newFile == null ) {
			return SEEqualElementsActionType.DoNothing.INSTANCE;
		}

		// Unconditionally skip any BoxLang-compiled .class file — we never want these in search results
		if ( isBoxLangCompiledClass( newFile ) ) {
			return SEEqualElementsActionType.Skip.INSTANCE;
		}

		// If the new item is a BoxLang source file, replace any existing compiled .class files
		// that may have snuck in before this provider was consulted
		if ( isBoxLangSource( newFile ) ) {
			List<SearchEverywhereFoundElementInfo> toReplace = new ArrayList<>();
			for ( SearchEverywhereFoundElementInfo existing : alreadyFoundItems ) {
				VirtualFile existingFile = extractFile( existing );
				if ( existingFile != null && isBoxLangCompiledClass( existingFile ) ) {
					toReplace.add( existing );
				}
			}
			if ( !toReplace.isEmpty() ) {
				return new SEEqualElementsActionType.Replace( toReplace );
			}
		}

		return SEEqualElementsActionType.DoNothing.INSTANCE;
	}

	/**
	 * Extracts a {@link VirtualFile} from a search result element.
	 * <p>
	 * Search Everywhere wraps elements in presentation wrappers
	 * ({@link PSIPresentationBgRendererWrapper.ItemWithPresentation},
	 * {@code PsiItemWithSimilarity}, etc.) before they reach the equality
	 * provider. We must unwrap them using the platform's own
	 * {@link PSIPresentationBgRendererWrapper#toPsi(Object)} to get the
	 * underlying {@link PsiElement}, exactly as the built-in
	 * {@code PsiElementsEqualityProvider} does.
	 */
	private static VirtualFile extractFile( SearchEverywhereFoundElementInfo info ) {
		Object		element	= info.getElement();

		// Unwrap presentation/similarity wrappers the same way the platform does
		PsiElement	psi		= PSIPresentationBgRendererWrapper.toPsi( element );

		if ( psi instanceof PsiFile psiFile ) {
			return psiFile.getVirtualFile();
		}
		if ( psi instanceof PsiFileSystemItem fsItem ) {
			return fsItem.getVirtualFile();
		}
		if ( psi != null ) {
			PsiFile containingFile = psi.getContainingFile();
			if ( containingFile != null ) {
				return containingFile.getVirtualFile();
			}
		}
		return null;
	}

	/**
	 * Returns true if the file is a .class file that likely originated from a BoxLang/CFML source.
	 * Patterns include:
	 * <ul>
	 * <li>{@code Foo$cfc.class} / {@code Foo$cfm.class} / {@code Foo$bx.class} / etc.</li>
	 * <li>{@code Foo$cfc$Closure_1.class} (inner closures)</li>
	 * <li>{@code foo_cfc$cf.class} (Lucee-style compiled classes)</li>
	 * </ul>
	 */
	private static boolean isBoxLangCompiledClass( VirtualFile file ) {
		String name = file.getName().toLowerCase( Locale.ROOT );
		if ( !name.endsWith( ".class" ) ) {
			return false;
		}
		// Match patterns like: name$cfc.class, name$cfm.class, name$bx.class, name$bxs.class, name$bxm.class
		// Also: name$cfc$Closure_1.class, name_cfc$cf.class
		return name.contains( "$cfc" )
		    || name.contains( "$cfm" )
		    || name.contains( "$bx$" )
		    || name.contains( "$bx." )
		    || name.contains( "$bxs" )
		    || name.contains( "$bxm" )
		    || name.contains( "_cfc$" )
		    || name.contains( "_cfm$" );
	}

	private static boolean isBoxLangSource( VirtualFile file ) {
		String ext = file.getExtension();
		if ( ext == null ) {
			return false;
		}
		String normalized = ext.toLowerCase( Locale.ROOT );
		return normalized.equals( "bx" )
		    || normalized.equals( "bxs" )
		    || normalized.equals( "bxm" )
		    || normalized.equals( "cfc" )
		    || normalized.equals( "cfm" );
	}
}
