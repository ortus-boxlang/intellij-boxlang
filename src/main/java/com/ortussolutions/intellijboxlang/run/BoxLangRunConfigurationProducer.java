package com.ortussolutions.intellijboxlang.run;

import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.LazyRunConfigurationProducer;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * Produces run configurations from context (e.g., right-click on a BoxLang file).
 * This enables "Run 'filename.bx'" in the context menu.
 */
public class BoxLangRunConfigurationProducer extends LazyRunConfigurationProducer<BoxLangRunConfiguration> {

    @Override
    public @NotNull ConfigurationFactory getConfigurationFactory() {
        BoxLangConfigurationType type = ConfigurationTypeUtil.findConfigurationType(BoxLangConfigurationType.class);
        return type.getConfigurationFactories()[0];
    }

    @Override
    protected boolean setupConfigurationFromContext(@NotNull BoxLangRunConfiguration configuration,
                                                     @NotNull ConfigurationContext context,
                                                     @NotNull Ref<PsiElement> sourceElement) {
        VirtualFile file = getBoxLangFile(context);
        if (file == null) {
            return false;
        }

        // Set up the configuration for this specific file
        configuration.setUseCurrentFile(false);
        configuration.setScriptPath(file.getPath());
        configuration.setName(file.getName());
        
        sourceElement.set(context.getPsiLocation());
        return true;
    }

    @Override
    public boolean isConfigurationFromContext(@NotNull BoxLangRunConfiguration configuration,
                                               @NotNull ConfigurationContext context) {
        VirtualFile file = getBoxLangFile(context);
        if (file == null) {
            return false;
        }

        // Only match if the script path exactly matches the file
        // Don't match "use current file" configurations - those are meant to be used
        // from the run toolbar, not from context menus on specific files
        if (configuration.isUseCurrentFile()) {
            return false;
        }
        
        // Check if the script path matches
        String configPath = configuration.getScriptPath();
        return configPath != null && configPath.equals(file.getPath());
    }

    /**
     * Gets the BoxLang file from the context, or null if not applicable.
     */
    private VirtualFile getBoxLangFile(ConfigurationContext context) {
        PsiElement element = context.getPsiLocation();
        if (element == null) {
            return null;
        }

        PsiFile psiFile = element.getContainingFile();
        if (psiFile == null) {
            return null;
        }

        VirtualFile file = psiFile.getVirtualFile();
        if (file == null) {
            return null;
        }

        // Check if it's a BoxLang file
        String ext = file.getExtension();
        if (ext == null) {
            return null;
        }

        if (ext.equalsIgnoreCase("bx") || ext.equalsIgnoreCase("bxm") || ext.equalsIgnoreCase("bxs")) {
            return file;
        }

        return null;
    }
}
