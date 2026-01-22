package com.ortussolutions.intellijboxlang.runtime;

import com.intellij.openapi.progress.ProgressIndicator;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ForgeBoxLspInstaller {
    private static final com.intellij.openapi.diagnostic.Logger LOG =
        com.intellij.openapi.diagnostic.Logger.getInstance(ForgeBoxLspInstaller.class);
    private ForgeBoxLspInstaller() {
    }

    public static void install(String version, Path targetDir, ProgressIndicator indicator) throws IOException {
        Files.createDirectories(targetDir);
        ForgeBoxLspDescriptor descriptor = ForgeBoxLspResolver.resolve(version);
        LOG.info("bx-lsp download URL: " + descriptor.downloadUrl);
        Path archive = targetDir.resolve("bx-lsp.zip");
        BoxLangDownloadService.downloadTo(new URL(descriptor.downloadUrl), archive, indicator);
        LOG.info("bx-lsp download complete: " + archive + " (" + Files.size(archive) + " bytes)");

        BoxLangZipExtractor.extract(archive, targetDir);
        LOG.info("bx-lsp extracted to: " + targetDir);
        Files.deleteIfExists(archive);
    }
}
