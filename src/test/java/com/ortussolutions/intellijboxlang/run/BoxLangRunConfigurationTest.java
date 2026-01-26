package com.ortussolutions.intellijboxlang.run;

import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

/**
 * Tests for BoxLang run configuration.
 */
public class BoxLangRunConfigurationTest extends BasePlatformTestCase {

    public void testConfigurationTypeRegistered() {
        ConfigurationType type = ConfigurationTypeUtil.findConfigurationType(BoxLangConfigurationType.ID);
        assertNotNull("BoxLang configuration type should be registered", type);
        assertEquals("BoxLang", type.getDisplayName());
    }

    public void testConfigurationFactoryCreatesConfiguration() {
        ConfigurationType type = ConfigurationTypeUtil.findConfigurationType(BoxLangConfigurationType.ID);
        assertNotNull(type);
        
        var factories = type.getConfigurationFactories();
        assertEquals(1, factories.length);
        
        RunConfiguration config = factories[0].createTemplateConfiguration(getProject());
        assertInstanceOf(config, BoxLangRunConfiguration.class);
    }

    public void testConfigurationOptionsDefaults() {
        ConfigurationType type = ConfigurationTypeUtil.findConfigurationType(BoxLangConfigurationType.ID);
        assertNotNull(type);
        
        BoxLangRunConfiguration config = (BoxLangRunConfiguration) type.getConfigurationFactories()[0]
                .createTemplateConfiguration(getProject());
        
        // By default, use current file should be enabled
        assertTrue("useCurrentFile should default to true", config.isUseCurrentFile());
        assertEquals("", config.getScriptPath());
        assertEquals("", config.getWorkingDirectory());
        assertEquals("", config.getProgramArguments());
        assertEquals("", config.getEnvironmentVariables());
        assertEquals("", config.getBoxLangHome());
        assertEquals("", config.getJvmArgs());
    }

    public void testConfigurationOptionsPersistence() {
        ConfigurationType type = ConfigurationTypeUtil.findConfigurationType(BoxLangConfigurationType.ID);
        assertNotNull(type);
        
        BoxLangRunConfiguration config = (BoxLangRunConfiguration) type.getConfigurationFactories()[0]
                .createTemplateConfiguration(getProject());
        
        config.setUseCurrentFile(false);
        config.setScriptPath("/path/to/script.bx");
        config.setWorkingDirectory("/path/to/working");
        config.setProgramArguments("--arg1 value1");
        config.setEnvironmentVariables("VAR1=value1");
        config.setBoxLangHome("/path/to/boxlang");
        config.setJvmArgs("-Xmx1g");
        
        assertFalse(config.isUseCurrentFile());
        assertEquals("/path/to/script.bx", config.getScriptPath());
        assertEquals("/path/to/working", config.getWorkingDirectory());
        assertEquals("--arg1 value1", config.getProgramArguments());
        assertEquals("VAR1=value1", config.getEnvironmentVariables());
        assertEquals("/path/to/boxlang", config.getBoxLangHome());
        assertEquals("-Xmx1g", config.getJvmArgs());
    }

    public void testConfigurationValidationPassesWithUseCurrentFile() {
        ConfigurationType type = ConfigurationTypeUtil.findConfigurationType(BoxLangConfigurationType.ID);
        assertNotNull(type);
        
        BoxLangRunConfiguration config = (BoxLangRunConfiguration) type.getConfigurationFactories()[0]
                .createTemplateConfiguration(getProject());
        
        // With useCurrentFile enabled, validation should pass even without a script path
        config.setUseCurrentFile(true);
        
        try {
            config.checkConfiguration();
            // Should not throw - validation passes when using current file
        } catch (Exception e) {
            fail("Should not throw exception when useCurrentFile is enabled");
        }
    }

    public void testConfigurationValidationEmptyScriptWithoutUseCurrentFile() {
        ConfigurationType type = ConfigurationTypeUtil.findConfigurationType(BoxLangConfigurationType.ID);
        assertNotNull(type);
        
        BoxLangRunConfiguration config = (BoxLangRunConfiguration) type.getConfigurationFactories()[0]
                .createTemplateConfiguration(getProject());
        
        // Disable useCurrentFile - now we need a script path
        config.setUseCurrentFile(false);
        
        try {
            config.checkConfiguration();
            fail("Should throw exception for empty script path when useCurrentFile is disabled");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("Script path"));
        }
    }

    public void testConfigurationValidationNonExistentScript() {
        ConfigurationType type = ConfigurationTypeUtil.findConfigurationType(BoxLangConfigurationType.ID);
        assertNotNull(type);
        
        BoxLangRunConfiguration config = (BoxLangRunConfiguration) type.getConfigurationFactories()[0]
                .createTemplateConfiguration(getProject());
        
        config.setUseCurrentFile(false);
        config.setScriptPath("/non/existent/script.bx");
        
        try {
            config.checkConfiguration();
            fail("Should throw exception for non-existent script");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("does not exist"));
        }
    }
}
