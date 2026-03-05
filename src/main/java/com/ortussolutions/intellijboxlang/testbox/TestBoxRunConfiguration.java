package com.ortussolutions.intellijboxlang.testbox;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Run configuration for executing TestBox tests.
 */
public class TestBoxRunConfiguration extends RunConfigurationBase<TestBoxRunConfigurationOptions> {

	protected TestBoxRunConfiguration( @NotNull Project project,
	    @NotNull ConfigurationFactory factory,
	    @Nullable String name ) {
		super( project, factory, name );
	}

	@Override
	protected @NotNull TestBoxRunConfigurationOptions getOptions() {
		return ( TestBoxRunConfigurationOptions ) super.getOptions();
	}

	// --- Delegate accessors ---

	public String getTestScope() {
		return getOptions().getTestScope();
	}

	public void setTestScope( String scope ) {
		getOptions().setTestScope( scope );
	}

	public String getBundlePath() {
		return getOptions().getBundlePath();
	}

	public void setBundlePath( String path ) {
		getOptions().setBundlePath( path );
	}

	public String getDirectory() {
		return getOptions().getDirectory();
	}

	public void setDirectory( String dir ) {
		getOptions().setDirectory( dir );
	}

	public String getFilterSpecs() {
		return getOptions().getFilterSpecs();
	}

	public void setFilterSpecs( String specs ) {
		getOptions().setFilterSpecs( specs );
	}

	public String getFilterSuites() {
		return getOptions().getFilterSuites();
	}

	public void setFilterSuites( String suites ) {
		getOptions().setFilterSuites( suites );
	}

	public String getLabels() {
		return getOptions().getLabels();
	}

	public void setLabels( String labels ) {
		getOptions().setLabels( labels );
	}

	public String getExcludes() {
		return getOptions().getExcludes();
	}

	public void setExcludes( String excludes ) {
		getOptions().setExcludes( excludes );
	}

	public boolean isVerbose() {
		return getOptions().isVerbose();
	}

	public void setVerbose( boolean verbose ) {
		getOptions().setVerbose( verbose );
	}

	public boolean isEagerFailure() {
		return getOptions().isEagerFailure();
	}

	public void setEagerFailure( boolean eagerFailure ) {
		getOptions().setEagerFailure( eagerFailure );
	}

	public String getWorkingDirectory() {
		return getOptions().getWorkingDirectory();
	}

	public void setWorkingDirectory( String directory ) {
		getOptions().setWorkingDirectory( directory );
	}

	public String getBoxLangHome() {
		return getOptions().getBoxLangHome();
	}

	public void setBoxLangHome( String home ) {
		getOptions().setBoxLangHome( home );
	}

	public String getJvmArgs() {
		return getOptions().getJvmArgs();
	}

	public void setJvmArgs( String args ) {
		getOptions().setJvmArgs( args );
	}

	public String getEnvironmentVariables() {
		return getOptions().getEnvironmentVariables();
	}

	public void setEnvironmentVariables( String variables ) {
		getOptions().setEnvironmentVariables( variables );
	}

	@Override
	public @NotNull SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
		return new TestBoxRunConfigurationEditor( getProject() );
	}

	@Override
	public void checkConfiguration() throws RuntimeConfigurationException {
		String scope = getTestScope();
		if ( "BUNDLE".equals( scope ) ) {
			String bundlePath = getBundlePath();
			if ( bundlePath == null || bundlePath.isBlank() ) {
				throw new RuntimeConfigurationError( "Bundle path is not specified" );
			}
		} else if ( "DIRECTORY".equals( scope ) ) {
			String directory = getDirectory();
			if ( directory == null || directory.isBlank() ) {
				throw new RuntimeConfigurationError( "Test directory is not specified" );
			}
		}
	}

	@Override
	public @Nullable RunProfileState getState( @NotNull Executor executor,
	    @NotNull ExecutionEnvironment environment ) throws ExecutionException {
		return new TestBoxRunProfileState( this, environment );
	}
}
