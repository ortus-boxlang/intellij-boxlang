package com.ortussolutions.intellijboxlang.run;

import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

/**
 * Tests for BoxLang attach run configuration.
 */
public class BoxLangAttachRunConfigurationTest extends BasePlatformTestCase {

	public void testConfigurationTypeRegistered() {
		ConfigurationType type = ConfigurationTypeUtil.findConfigurationType( BoxLangAttachConfigurationType.ID );
		assertNotNull( "BoxLang attach configuration type should be registered", type );
		assertEquals( "BoxLang Attach", type.getDisplayName() );
	}

	public void testConfigurationFactoryCreatesConfiguration() {
		ConfigurationType type = ConfigurationTypeUtil.findConfigurationType( BoxLangAttachConfigurationType.ID );
		assertNotNull( type );

		var factories = type.getConfigurationFactories();
		assertEquals( 1, factories.length );

		RunConfiguration config = factories[ 0 ].createTemplateConfiguration( getProject() );
		assertInstanceOf( config, BoxLangAttachRunConfiguration.class );
	}

	public void testConfigurationOptionsDefaults() {
		ConfigurationType type = ConfigurationTypeUtil.findConfigurationType( BoxLangAttachConfigurationType.ID );
		assertNotNull( type );

		BoxLangAttachRunConfiguration config = ( BoxLangAttachRunConfiguration ) type.getConfigurationFactories()[ 0 ]
		    .createTemplateConfiguration( getProject() );

		assertEquals( "localhost", config.getHost() );
		assertEquals( 5005, config.getJdwpPort() );
		assertEquals( "", config.getLocalRoot() );
		assertEquals( "", config.getRemoteRoot() );
		assertEquals( "", config.getBoxLangHome() );
	}

	public void testConfigurationOptionsPersistence() {
		ConfigurationType type = ConfigurationTypeUtil.findConfigurationType( BoxLangAttachConfigurationType.ID );
		assertNotNull( type );

		BoxLangAttachRunConfiguration config = ( BoxLangAttachRunConfiguration ) type.getConfigurationFactories()[ 0 ]
		    .createTemplateConfiguration( getProject() );

		config.setHost( "192.168.1.100" );
		config.setJdwpPort( 9999 );
		config.setLocalRoot( "/local/path/webroot" );
		config.setRemoteRoot( "/remote/path/webroot" );
		config.setBoxLangHome( "/path/to/boxlang" );

		assertEquals( "192.168.1.100", config.getHost() );
		assertEquals( 9999, config.getJdwpPort() );
		assertEquals( "/local/path/webroot", config.getLocalRoot() );
		assertEquals( "/remote/path/webroot", config.getRemoteRoot() );
		assertEquals( "/path/to/boxlang", config.getBoxLangHome() );
	}

	public void testValidationPassesWithValidConfig() {
		ConfigurationType type = ConfigurationTypeUtil.findConfigurationType( BoxLangAttachConfigurationType.ID );
		assertNotNull( type );

		BoxLangAttachRunConfiguration config = ( BoxLangAttachRunConfiguration ) type.getConfigurationFactories()[ 0 ]
		    .createTemplateConfiguration( getProject() );

		// Defaults should be valid
		try {
			config.checkConfiguration();
		} catch ( Exception e ) {
			fail( "Default configuration should be valid: " + e.getMessage() );
		}
	}

	public void testValidationFailsWithEmptyHost() {
		ConfigurationType type = ConfigurationTypeUtil.findConfigurationType( BoxLangAttachConfigurationType.ID );
		assertNotNull( type );

		BoxLangAttachRunConfiguration config = ( BoxLangAttachRunConfiguration ) type.getConfigurationFactories()[ 0 ]
		    .createTemplateConfiguration( getProject() );

		config.setHost( "" );

		try {
			config.checkConfiguration();
			fail( "Should throw exception for empty host" );
		} catch ( Exception e ) {
			assertTrue( e.getMessage().contains( "Host" ) );
		}
	}

	public void testValidationFailsWithZeroPort() {
		ConfigurationType type = ConfigurationTypeUtil.findConfigurationType( BoxLangAttachConfigurationType.ID );
		assertNotNull( type );

		BoxLangAttachRunConfiguration config = ( BoxLangAttachRunConfiguration ) type.getConfigurationFactories()[ 0 ]
		    .createTemplateConfiguration( getProject() );

		config.setJdwpPort( 0 );

		try {
			config.checkConfiguration();
			fail( "Should throw exception for zero port" );
		} catch ( Exception e ) {
			assertTrue( e.getMessage().contains( "port" ) );
		}
	}

	public void testValidationFailsWithPortOutOfRange() {
		ConfigurationType type = ConfigurationTypeUtil.findConfigurationType( BoxLangAttachConfigurationType.ID );
		assertNotNull( type );

		BoxLangAttachRunConfiguration config = ( BoxLangAttachRunConfiguration ) type.getConfigurationFactories()[ 0 ]
		    .createTemplateConfiguration( getProject() );

		config.setJdwpPort( 70000 );

		try {
			config.checkConfiguration();
			fail( "Should throw exception for port out of range" );
		} catch ( Exception e ) {
			assertTrue( e.getMessage().contains( "port" ) );
		}
	}

	public void testValidationFailsWithNegativePort() {
		ConfigurationType type = ConfigurationTypeUtil.findConfigurationType( BoxLangAttachConfigurationType.ID );
		assertNotNull( type );

		BoxLangAttachRunConfiguration config = ( BoxLangAttachRunConfiguration ) type.getConfigurationFactories()[ 0 ]
		    .createTemplateConfiguration( getProject() );

		config.setJdwpPort( -1 );

		try {
			config.checkConfiguration();
			fail( "Should throw exception for negative port" );
		} catch ( Exception e ) {
			assertTrue( e.getMessage().contains( "port" ) );
		}
	}
}
