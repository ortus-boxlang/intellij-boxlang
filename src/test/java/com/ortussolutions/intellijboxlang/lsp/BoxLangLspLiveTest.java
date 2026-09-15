package com.ortussolutions.intellijboxlang.lsp;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.ortussolutions.intellijboxlang.runtime.BoxLangToolingStatus;
import com.ortussolutions.intellijboxlang.settings.BoxLangProjectSettings;
import com.ortussolutions.intellijboxlang.settings.BoxLangStoragePaths;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/** Opt-in test of actual plugin startup, CFML diagnostics, and restart with local build artifacts. */
public class BoxLangLspLiveTest extends BasePlatformTestCase {

	public void testRealServerInitializationDiagnosticsAndRestart() throws Exception {
		String	runtimeProperty	= System.getProperty( "boxlang.liveRuntimeJar", "" );
		String	moduleProperty	= System.getProperty( "boxlang.liveLspModule", "" );
		// BasePlatformTestCase uses the JUnit 3 runner, which treats JUnit 4 assumptions as failures.
		// CI has no local runtime artifacts; return before integration assertions in that case.
		if ( runtimeProperty.isBlank() || moduleProperty.isBlank() )
			return;
		Path	runtime			= Path.of( runtimeProperty );
		String	version			= runtime.getFileName().toString().replaceFirst( "\\.jar$", "" );
		Path	cache			= BoxLangStoragePaths.getRuntimeCacheRoot().resolve( version );
		Path	cachedJar		= cache.resolve( version + ".jar" );
		boolean	createdCache	= !Files.exists( cache );
		boolean	createdJar		= !Files.exists( cachedJar );
		Path	temporary		= Files.createTempDirectory( "intellij-lsp-live" ).toRealPath();
		var		settings		= BoxLangProjectSettings.getInstance( getProject() ).getSettings();
		var		previous		= new com.ortussolutions.intellijboxlang.settings.BoxLangProjectSettingsState();
		com.intellij.util.xmlb.XmlSerializerUtil.copyBean( settings, previous );
		var service = new BoxLangLspClientService( getProject() );
		com.intellij.testFramework.ServiceContainerUtil.replaceService( getProject(), BoxLangLspClientService.class, service, getTestRootDisposable() );
		try {
			Files.createDirectories( cache );
			if ( createdJar )
				Files.copy( runtime, cachedJar );
			Path module = temporary.resolve( "modules/bx-lsp" );
			com.intellij.openapi.util.io.FileUtil.copyDir( Path.of( moduleProperty ).toFile(), module.toFile() );
			settings.lspModulePath		= module.toString();
			settings.lspBoxLangHome		= temporary.resolve( "home" ).toString();
			settings.lspBoxLangVersion	= version.replaceFirst( "^boxlang-", "" );
			settings.lspModules			= "";
			com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess.allowRootAccess( getTestRootDisposable(), temporary.toString() );
			Path physical = temporary.resolve( "broken.cfm" );
			Files.writeString( physical, "<cfset value = >" );
			var virtualFile = com.intellij.openapi.vfs.LocalFileSystem.getInstance().refreshAndFindFileByNioFile( physical );
			assertNotNull( virtualFile );
			myFixture.configureFromExistingVirtualFile( virtualFile );
			var	file			= myFixture.getFile();
			var	status			= BoxLangToolingStatus.get( getProject() );
			var	firstRequest	= ApplicationManager.getApplication().executeOnPooledThread(
			    () -> service.requestDiagnostics( file.getVirtualFile(), myFixture.getEditor().getDocument() ) );
			PlatformTestUtil.waitWithEventsDispatching( "real LSP initialization", () -> {
				var state = status.entries().get( "BoxLang language server" );
				return state != null && ( state.phase() == BoxLangToolingStatus.Phase.READY || state.phase() == BoxLangToolingStatus.Phase.FAILED );
			}, 45 );
			assertEquals( status.events().toString(), BoxLangToolingStatus.Phase.READY, status.entries().get( "BoxLang language server" ).phase() );
			var diagnostics = firstRequest.get( 15, TimeUnit.SECONDS );
			assertTrue( diagnostics.toString(), diagnostics.stream().anyMatch( d -> d.getSeverity() == org.eclipse.lsp4j.DiagnosticSeverity.Error ) );
			assertSame( service, BoxLangLspClientService.getInstance( file.getProject() ) );
			com.intellij.util.ui.UIUtil.dispatchAllInvocationEvents();
			assertTrue( "BoxLang annotator must be registered",
			    com.intellij.lang.LanguageAnnotators.INSTANCE.allForLanguage( file.getLanguage() ).stream().anyMatch( a -> a instanceof BoxLangLspAnnotator ) );
			var highlights = myFixture.doHighlighting( com.intellij.lang.annotation.HighlightSeverity.ERROR );
			assertFalse( "The real LSP error must be rendered in the CFML editor", highlights.isEmpty() );
			com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction( getProject(),
			    () -> myFixture.getEditor().getDocument().setText( "<cfset value = 1>" ) );
			// bx-lsp debounces document changes, so wait for its updated diagnostics and the editor pass.
			PlatformTestUtil.waitWithEventsDispatching( "corrected CFML diagnostics",
			    () -> myFixture.doHighlighting( com.intellij.lang.annotation.HighlightSeverity.ERROR ).isEmpty(), 10 );
			Path	completionFile		= temporary.resolve( "Completion.cfc" );
			String	completionSource	= "component { numeric function len(required numeric customLength) { return customLength; } function run() { le; } }";
			Files.writeString( completionFile, completionSource );
			var completionVirtualFile = com.intellij.openapi.vfs.LocalFileSystem.getInstance().refreshAndFindFileByNioFile( completionFile );
			myFixture.configureFromExistingVirtualFile( completionVirtualFile );
			int completionOffset = completionSource.indexOf( "le;" ) + 2;
			myFixture.getEditor().getCaretModel().moveToOffset( completionOffset );
			var	completionDocument	= myFixture.getEditor().getDocument();
			var	completionRequest	= ApplicationManager.getApplication().executeOnPooledThread(
			    () -> service.requestCompletions( completionVirtualFile, completionDocument, completionOffset ) );
			PlatformTestUtil.waitWithEventsDispatching( "project completions", completionRequest::isDone, 15 );
			assertTrue( "Project len signature must be returned",
			    completionRequest.get().stream().anyMatch( item -> "len".equals( item.getLabel() ) && item.getData() != null ) );
			var lookups = myFixture.completeBasic();
			assertNotNull( lookups );
			var localLen = java.util.Arrays.stream( lookups ).filter( item -> item.getLookupString().equalsIgnoreCase( "len" ) ).toList();
			assertEquals( 1, localLen.size() );
			assertTrue( localLen.getFirst().getObject() instanceof com.ortussolutions.intellijboxlang.cfml.CfmlFunctionCatalog.Function );
			var localSignature = ( com.ortussolutions.intellijboxlang.cfml.CfmlFunctionCatalog.Function ) localLen.getFirst().getObject();
			assertEquals( "project", localSignature.origin() );
			assertEquals( "customLength", localSignature.params().getFirst().name() );
			com.intellij.codeInsight.lookup.LookupManager.getInstance( getProject() ).hideActiveLookup();
			String namedSource = completionSource.replace( "le;", "len(cu);" );
			com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction( getProject(), () -> completionDocument.setText( namedSource ) );
			myFixture.getEditor().getCaretModel().moveToOffset( namedSource.indexOf( "cu);" ) + 2 );
			myFixture.completeBasic();
			if ( myFixture.getLookup() != null ) {
				assertTrue( java.util.Arrays.stream( myFixture.getLookupElements() ).anyMatch( item -> item.getLookupString().equals( "customLength" ) ) );
				com.intellij.codeInsight.lookup.LookupManager.getInstance( getProject() ).hideActiveLookup();
			} else {
				assertTrue( completionDocument.getText(), completionDocument.getText().contains( "len(customLength=)" ) );
			}
			String memberSource = "component { function run(required array values) { values.ap; } }";
			com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction( getProject(), () -> completionDocument.setText( memberSource ) );
			myFixture.getEditor().getCaretModel().moveToOffset( memberSource.indexOf( "ap;" ) + 2 );
			myFixture.completeBasic();
			if ( myFixture.getLookup() != null ) {
				var	append		= java.util.Arrays.stream( myFixture.getLookupElements() ).filter( item -> item.getLookupString().equalsIgnoreCase( "append" ) )
				    .findFirst().orElseThrow();
				var	signature	= ( com.ortussolutions.intellijboxlang.cfml.CfmlFunctionCatalog.Function ) append.getObject();
				assertEquals( "value", signature.params().getFirst().name() );
				assertEquals( "arrayAppend", signature.sourceName() );
				assertFalse(
				    java.util.Arrays.stream( myFixture.getLookupElements() ).anyMatch( item -> item.getLookupString().equalsIgnoreCase( "keyExists" ) ) );
				com.intellij.codeInsight.lookup.LookupManager.getInstance( getProject() ).hideActiveLookup();
			} else {
				assertTrue( completionDocument.getText(), completionDocument.getText().contains( "values.append()" ) );
			}
			long readyCount = status.events().stream().filter( e -> e.contains( "BoxLang language server: READY" ) ).count();
			service.retryStartup();
			PlatformTestUtil.waitWithEventsDispatching( "real LSP restart", () -> status.events().stream()
			    .filter( e -> e.contains( "BoxLang language server: READY" ) ).count() > readyCount, 45 );
			assertEquals( BoxLangToolingStatus.Phase.READY, status.entries().get( "BoxLang language server" ).phase() );
		} finally {
			service.dispose();
			com.intellij.util.xmlb.XmlSerializerUtil.copyBean( previous, settings );
			com.intellij.openapi.util.io.FileUtil.delete( temporary.toFile() );
			if ( createdJar )
				Files.deleteIfExists( cachedJar );
			if ( createdCache )
				Files.deleteIfExists( cache );
		}
	}
}
