package com.ortussolutions.intellijboxlang.runtime;

import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.ortussolutions.intellijboxlang.settings.BoxLangSettingsResolver;
import com.ortussolutions.intellijboxlang.settings.BoxLangStoragePaths;
import java.util.Map;

/** A local snapshot only: no network, source files, environment dump, or automatic sharing. */
public final class BoxLangDiagnosticReport {

	private BoxLangDiagnosticReport() {
	}

	public static String collect( Project project ) {
		StringBuilder	out		= new StringBuilder( "BoxLang tooling diagnostic report\n" );
		var				plugin	= PluginManagerCore.getPlugin( PluginId.getId( "com.ortussolutions.intellij-boxlang" ) );
		line( out, "Plugin", plugin == null ? "unknown" : plugin.getVersion() );
		line( out, "IDE", ApplicationInfo.getInstance().getFullApplicationName() + " (" + ApplicationInfo.getInstance().getBuild() + ")" );
		line( out, "OS", System.getProperty( "os.name" ) + " " + System.getProperty( "os.version" ) + " " + System.getProperty( "os.arch" ) );
		line( out, "IDE Java", System.getProperty( "java.version" ) + " (" + System.getProperty( "java.vendor" ) + ")" );
		var settings = BoxLangSettingsResolver.resolve( project );
		out.append( "\nEffective configuration\n" );
		line( out, "Runtime version requested", settings.boxLangVersion );
		line( out, "Runtime JAR override", settings.boxLangJarPath );
		line( out, "Runtime home", settings.boxLangHome );
		line( out, "Java home override", settings.javaHome );
		line( out, "LSP version requested", settings.lspVersion );
		line( out, "LSP runtime version requested", settings.lspBoxLangVersion );
		line( out, "LSP module override", settings.lspModulePath );
		line( out, "LSP home", settings.lspBoxLangHome );
		line( out, "Runtime cache", BoxLangStoragePaths.getRuntimeCacheRoot().toString() );
		line( out, "IDE logs folder", PathManager.getLogPath() );
		out.append( "\nLocally detected installations (presence does not prove readiness)\n" );
		try {
			installation( out, "Runtime candidate", ModuleStatusResolver.resolveRuntimeStatus( null ) );
		} catch ( RuntimeException e ) {
			line( out, "Runtime inspection", e.toString() );
		}
		try {
			installation( out, "LSP module candidate", ModuleStatusResolver.resolveLspStatus( project ) );
		} catch ( RuntimeException e ) {
			line( out, "LSP inspection", e.toString() );
		}
		appendStatus( out, "Project session", BoxLangToolingStatus.get( project ) );
		appendStatus( out, "IDE-wide setup session", BoxLangToolingStatus.get( null ) );
		out.append( "\nReport excludes source files, environment variables, and custom JVM arguments.\n" );
		out.append( "Home/project paths and common credential patterns are redacted. Review startup output before sharing.\n" );
		return redact( out.toString(), System.getProperty( "user.home" ), project.getBasePath() );
	}

	private static void installation( StringBuilder out, String name, InstalledModuleStatus status ) {
		line( out, name, status.installed ? "installed; version=" + status.version + "; path=" + status.path : "not found" );
	}

	private static void appendStatus( StringBuilder out, String title, BoxLangToolingStatus status ) {
		out.append( "\n" ).append( title ).append( "\n" );
		Map<String, BoxLangToolingStatus.Entry> entries = status.entries();
		if ( entries.isEmpty() )
			out.append( "No setup or startup has been attempted in this session.\n" );
		entries.forEach( ( name, entry ) -> line( out, name, entry.phase() + ": " + entry.detail() ) );
		out.append( "Recent events and startup output (last 100 entries):\n" );
		status.events().forEach( event -> out.append( event ).append( '\n' ) );
	}

	private static void line( StringBuilder out, String name, String value ) {
		out.append( name ).append( ": " ).append( value == null || value.isBlank() ? "default / not configured" : value ).append( '\n' );
	}

	static String redact( String text, String home, String projectPath ) {
		if ( projectPath != null && !projectPath.isBlank() )
			text = text.replace( projectPath, "$PROJECT" );
		if ( home != null && !home.isBlank() )
			text = text.replace( home, "$HOME" );
		text	= text.replaceAll( "(?i)(https?://)[^\\s/@]+:[^\\s/@]+@", "$1[redacted]@" );
		text	= text.replaceAll( "(?i)(authorization\\s*[:=]\\s*)(?:bearer|basic)\\s+[^\\s]+", "$1[redacted]" );
		return text.replaceAll( "(?i)((?:password|passwd|token|secret|api[_-]?key|access[_-]?key)\\s*[=:]\\s*)[^\\s&,;]+", "$1[redacted]" );
	}
}
