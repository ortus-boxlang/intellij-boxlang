package com.ortussolutions.intellijboxlang.run;

import com.intellij.execution.lineMarker.ExecutorAction;
import com.intellij.execution.lineMarker.RunLineMarkerContributor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides run icons in the gutter for BoxLang files.
 * Shows a run icon on the first element of BoxLang files.
 */
public class BoxLangRunLineMarkerProvider extends RunLineMarkerContributor {

    @Override
    public @Nullable Info getInfo(@NotNull PsiElement element) {
        // Only show on the first element of the file to avoid multiple markers
        if (!isFirstElementInFile(element)) {
            return null;
        }

        PsiFile file = element.getContainingFile();
        if (file == null) {
            return null;
        }

        // Check if it's a BoxLang file
        String fileName = file.getName();
        if (!isBoxLangFile(fileName)) {
            return null;
        }

        // Create the run actions
        AnAction[] actions = ExecutorAction.getActions(0);
        
        return new Info(
                AllIcons.RunConfigurations.TestState.Run,
                actions,
                psiElement -> "Run " + file.getName()
        );
    }

    /**
     * Checks if the element is the first significant element in the file.
     * We check if this is the first child of the file to avoid duplicate markers.
     */
    private boolean isFirstElementInFile(PsiElement element) {
        PsiFile file = element.getContainingFile();
        if (file == null) {
            return false;
        }

        // We want to place the marker on the first leaf element of the file
        PsiElement firstChild = file.getFirstChild();
        if (firstChild == null) {
            return false;
        }

        // Navigate to the first leaf element
        while (firstChild.getFirstChild() != null) {
            firstChild = firstChild.getFirstChild();
        }

        return element.equals(firstChild);
    }

    /**
     * Checks if the file is a BoxLang file based on its extension.
     */
    private boolean isBoxLangFile(String fileName) {
        if (fileName == null) {
            return false;
        }
        String lowerName = fileName.toLowerCase();
        return lowerName.endsWith(".bx") || lowerName.endsWith(".bxm") || lowerName.endsWith(".bxs");
    }
}
