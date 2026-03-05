package com.ortussolutions.intellijboxlang.testbox;

import com.intellij.execution.configurations.RunConfigurationOptions;
import com.intellij.openapi.components.StoredProperty;

/**
 * Stores the persisted options for a TestBox run configuration.
 */
public class TestBoxRunConfigurationOptions extends RunConfigurationOptions {

	/**
	 * The test scope: BUNDLE, DIRECTORY, or ALL.
	 */
	private final StoredProperty<String>	testScope				= string( "BUNDLE" )
	    .provideDelegate( this, "testScope" );

	/**
	 * Dot-notation path to a specific test bundle (e.g., tests.specs.MySpec).
	 */
	private final StoredProperty<String>	bundlePath				= string( "" )
	    .provideDelegate( this, "bundlePath" );

	/**
	 * Directory to scan for test bundles (dot-notation, e.g., tests.specs).
	 */
	private final StoredProperty<String>	directory				= string( "tests.specs" )
	    .provideDelegate( this, "directory" );

	/**
	 * Filter to run specific specs only (comma-separated spec names).
	 */
	private final StoredProperty<String>	filterSpecs				= string( "" )
	    .provideDelegate( this, "filterSpecs" );

	/**
	 * Filter to run specific suites only (comma-separated suite names).
	 */
	private final StoredProperty<String>	filterSuites			= string( "" )
	    .provideDelegate( this, "filterSuites" );

	/**
	 * Labels to include in the execution.
	 */
	private final StoredProperty<String>	labels					= string( "" )
	    .provideDelegate( this, "labels" );

	/**
	 * Labels to exclude from the execution.
	 */
	private final StoredProperty<String>	excludes				= string( "" )
	    .provideDelegate( this, "excludes" );

	/**
	 * Whether to use verbose output (stream progress).
	 */
	private final StoredProperty<Boolean>	verbose					= property( false )
	    .provideDelegate( this, "verbose" );

	/**
	 * Whether to fail fast on first error.
	 */
	private final StoredProperty<Boolean>	eagerFailure			= property( false )
	    .provideDelegate( this, "eagerFailure" );

	/**
	 * Working directory for test execution (defaults to project root).
	 */
	private final StoredProperty<String>	workingDirectory		= string( "" )
	    .provideDelegate( this, "workingDirectory" );

	/**
	 * Override BoxLang home directory.
	 */
	private final StoredProperty<String>	boxLangHome				= string( "" )
	    .provideDelegate( this, "boxLangHome" );

	/**
	 * Additional JVM arguments.
	 */
	private final StoredProperty<String>	jvmArgs					= string( "" )
	    .provideDelegate( this, "jvmArgs" );

	/**
	 * Additional environment variables.
	 */
	private final StoredProperty<String>	environmentVariables	= string( "" )
	    .provideDelegate( this, "environmentVariables" );

	// --- Getters and Setters ---

	public String getTestScope() {
		return testScope.getValue( this );
	}

	public void setTestScope( String scope ) {
		testScope.setValue( this, scope );
	}

	public String getBundlePath() {
		return bundlePath.getValue( this );
	}

	public void setBundlePath( String path ) {
		bundlePath.setValue( this, path );
	}

	public String getDirectory() {
		return directory.getValue( this );
	}

	public void setDirectory( String dir ) {
		directory.setValue( this, dir );
	}

	public String getFilterSpecs() {
		return filterSpecs.getValue( this );
	}

	public void setFilterSpecs( String specs ) {
		filterSpecs.setValue( this, specs );
	}

	public String getFilterSuites() {
		return filterSuites.getValue( this );
	}

	public void setFilterSuites( String suites ) {
		filterSuites.setValue( this, suites );
	}

	public String getLabels() {
		return labels.getValue( this );
	}

	public void setLabels( String labels ) {
		this.labels.setValue( this, labels );
	}

	public String getExcludes() {
		return excludes.getValue( this );
	}

	public void setExcludes( String excludes ) {
		this.excludes.setValue( this, excludes );
	}

	public boolean isVerbose() {
		return verbose.getValue( this );
	}

	public void setVerbose( boolean verbose ) {
		this.verbose.setValue( this, verbose );
	}

	public boolean isEagerFailure() {
		return eagerFailure.getValue( this );
	}

	public void setEagerFailure( boolean eagerFailure ) {
		this.eagerFailure.setValue( this, eagerFailure );
	}

	public String getWorkingDirectory() {
		return workingDirectory.getValue( this );
	}

	public void setWorkingDirectory( String directory ) {
		workingDirectory.setValue( this, directory );
	}

	public String getBoxLangHome() {
		return boxLangHome.getValue( this );
	}

	public void setBoxLangHome( String home ) {
		boxLangHome.setValue( this, home );
	}

	public String getJvmArgs() {
		return jvmArgs.getValue( this );
	}

	public void setJvmArgs( String args ) {
		jvmArgs.setValue( this, args );
	}

	public String getEnvironmentVariables() {
		return environmentVariables.getValue( this );
	}

	public void setEnvironmentVariables( String variables ) {
		environmentVariables.setValue( this, variables );
	}
}
