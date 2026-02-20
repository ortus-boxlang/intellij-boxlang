package com.ortussolutions.intellijboxlang.run;

import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.List;

/**
 * Tests for BoxLang run configuration producer.
 */
public class BoxLangRunConfigurationProducerTest extends BasePlatformTestCase {

    private ConfigurationContext createContextForFile(PsiFile psiFile) {
        DataContext dataContext = SimpleDataContext.builder()
                .add(CommonDataKeys.PSI_FILE, psiFile)
                .add(CommonDataKeys.PSI_ELEMENT, psiFile.findElementAt(0))
                .add(CommonDataKeys.PROJECT, getProject())
                .add(CommonDataKeys.VIRTUAL_FILE, psiFile.getVirtualFile())
                .build();
        return ConfigurationContext.getFromContext(dataContext, "Test");
    }

    public void testProducerCreatesConfigurationFromBxFile() {
        PsiFile psiFile = myFixture.configureByText("test.bx", "println('Hello')");
        ConfigurationContext context = createContextForFile(psiFile);

        List<ConfigurationFromContext> configs = context.getConfigurationsFromContext();
        assertNotNull("Should produce configurations from .bx file", configs);
        assertFalse("Should have at least one configuration", configs.isEmpty());

        boolean hasBoxLangConfig = configs.stream()
                .anyMatch(c -> c.getConfiguration() instanceof BoxLangRunConfiguration);
        assertTrue("Should have a BoxLang configuration", hasBoxLangConfig);
    }

    public void testProducerCreatesConfigurationFromBxsFile() {
        PsiFile psiFile = myFixture.configureByText("test.bxs", "println('Hello Script')");
        ConfigurationContext context = createContextForFile(psiFile);

        List<ConfigurationFromContext> configs = context.getConfigurationsFromContext();
        assertNotNull("Should produce configurations from .bxs file", configs);
        assertFalse("Should have at least one configuration", configs.isEmpty());

        boolean hasBoxLangConfig = configs.stream()
                .anyMatch(c -> c.getConfiguration() instanceof BoxLangRunConfiguration);
        assertTrue("Should have a BoxLang configuration", hasBoxLangConfig);
    }

    public void testProducerCreatesConfigurationFromBxmFile() {
        PsiFile psiFile = myFixture.configureByText("test.bxm", "<bx:output>Hello Template</bx:output>");
        ConfigurationContext context = createContextForFile(psiFile);

        List<ConfigurationFromContext> configs = context.getConfigurationsFromContext();
        assertNotNull("Should produce configurations from .bxm file", configs);
        assertFalse("Should have at least one configuration", configs.isEmpty());

        boolean hasBoxLangConfig = configs.stream()
                .anyMatch(c -> c.getConfiguration() instanceof BoxLangRunConfiguration);
        assertTrue("Should have a BoxLang configuration", hasBoxLangConfig);
    }

    public void testProducerDoesNotCreateConfigurationFromNonBoxLangFile() {
        PsiFile psiFile = myFixture.configureByText("test.txt", "Hello plain text");
        ConfigurationContext context = createContextForFile(psiFile);

        List<ConfigurationFromContext> configs = context.getConfigurationsFromContext();
        
        if (configs != null && !configs.isEmpty()) {
            boolean hasBoxLangConfig = configs.stream()
                    .anyMatch(c -> c.getConfiguration() instanceof BoxLangRunConfiguration);
            assertFalse("Should not have a BoxLang configuration for .txt file", hasBoxLangConfig);
        }
        // If no configs, that's also acceptable
    }

    public void testProducerSetsCorrectConfigurationName() {
        PsiFile psiFile = myFixture.configureByText("myScript.bx", "println('Hello')");
        ConfigurationContext context = createContextForFile(psiFile);

        List<ConfigurationFromContext> configs = context.getConfigurationsFromContext();
        assertNotNull(configs);
        
        ConfigurationFromContext boxLangConfig = configs.stream()
                .filter(c -> c.getConfiguration() instanceof BoxLangRunConfiguration)
                .findFirst()
                .orElse(null);
        
        assertNotNull("Should have a BoxLang configuration", boxLangConfig);
        assertEquals("Configuration name should match file name", 
                "myScript.bx", 
                boxLangConfig.getConfiguration().getName());
    }

    public void testProducerSetsCorrectScriptPath() {
        PsiFile psiFile = myFixture.configureByText("testPath.bx", "println('Hello')");
        VirtualFile virtualFile = psiFile.getVirtualFile();
        ConfigurationContext context = createContextForFile(psiFile);

        List<ConfigurationFromContext> configs = context.getConfigurationsFromContext();
        assertNotNull(configs);
        
        BoxLangRunConfiguration boxLangConfig = configs.stream()
                .filter(c -> c.getConfiguration() instanceof BoxLangRunConfiguration)
                .map(c -> (BoxLangRunConfiguration) c.getConfiguration())
                .findFirst()
                .orElse(null);
        
        assertNotNull("Should have a BoxLang configuration", boxLangConfig);
        assertEquals("Script path should match file path", 
                virtualFile.getPath(), 
                boxLangConfig.getScriptPath());
        assertFalse("useCurrentFile should be false when created from context", 
                boxLangConfig.isUseCurrentFile());
    }

    public void testProducerMatchesExistingConfiguration() {
        PsiFile psiFile = myFixture.configureByText("matching.bx", "println('Hello')");
        ConfigurationContext context = createContextForFile(psiFile);

        // Get the first configuration
        List<ConfigurationFromContext> configs1 = context.getConfigurationsFromContext();
        assertNotNull(configs1);
        assertFalse(configs1.isEmpty());
        
        // Get configurations again - should match existing
        List<ConfigurationFromContext> configs2 = context.getConfigurationsFromContext();
        assertNotNull(configs2);
        assertFalse(configs2.isEmpty());
        
        // Both should contain BoxLang configurations
        assertTrue(configs1.stream().anyMatch(c -> c.getConfiguration() instanceof BoxLangRunConfiguration));
        assertTrue(configs2.stream().anyMatch(c -> c.getConfiguration() instanceof BoxLangRunConfiguration));
    }
}
