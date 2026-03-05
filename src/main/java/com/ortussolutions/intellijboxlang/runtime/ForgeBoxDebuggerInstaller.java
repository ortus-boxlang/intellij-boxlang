package com.ortussolutions.intellijboxlang.runtime;

import com.intellij.openapi.progress.ProgressIndicator;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ForgeBoxDebuggerInstaller {

	private static final com.intellij.openapi.diagnostic.Logger LOG = com.intellij.openapi.diagnostic.Logger.getInstance( ForgeBoxDebuggerInstaller.class );

	private ForgeBoxDebuggerInstaller() {
	}

	public static void install( String version, Path targetDir, ProgressIndicator indicator ) throws IOException {
		Files.createDirectories( targetDir );
		ForgeBoxDebuggerDescriptor descriptor = ForgeBoxDebuggerResolver.resolve( version );
		LOG.info( "bx-debugger download URL: " + descriptor.downloadUrl );
		Path archive = targetDir.resolve( "bx-debugger.zip" );
		BoxLangDownloadService.downloadTo( URI.create( descriptor.downloadUrl ).toURL(), archive, indicator );
		LOG.info( "bx-debugger download complete: " + archive + " (" + Files.size( archive ) + " bytes)" );

		BoxLangZipExtractor.extract( archive, targetDir );
		LOG.info( "bx-debugger extracted to: " + targetDir );
		Files.deleteIfExists( archive );
	}
}
