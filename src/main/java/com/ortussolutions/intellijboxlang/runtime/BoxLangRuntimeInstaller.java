package com.ortussolutions.intellijboxlang.runtime;

import com.intellij.openapi.progress.ProgressIndicator;
import com.ortussolutions.intellijboxlang.settings.BoxLangStoragePaths;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class BoxLangRuntimeInstaller {
    private BoxLangRuntimeInstaller() {
    }

    public static BoxLangRuntimeSelection installRuntime(String resolvedVersion, String downloadUrl, ProgressIndicator indicator) throws IOException {
        if (downloadUrl == null || downloadUrl.isBlank()) {
            throw new IOException("Missing BoxLang download URL for " + resolvedVersion);
        }
        String filename = resolvedVersion + ".jar";
        URL url = new URL(downloadUrl);
        Path versionDir = BoxLangStoragePaths.getRuntimeCacheRoot().resolve(resolvedVersion);
        Files.createDirectories(versionDir);

        Path jarPath = versionDir.resolve(filename);
        BoxLangDownloadService.downloadTo(url, jarPath, indicator);
        writeMetadata(versionDir, resolvedVersion);
        BoxLangRuntimeSelection selection = new BoxLangRuntimeSelection();
        selection.jarPath = jarPath;
        selection.resolvedVersion = resolvedVersion;
        return selection;
    }

    public static BoxLangRuntimeSelection resolveCachedJar(String resolvedVersion) throws IOException {
        Path versionDir = BoxLangStoragePaths.getRuntimeCacheRoot().resolve(resolvedVersion);
        Path jarPath = versionDir.resolve(resolvedVersion + ".jar");
        if (!Files.exists(jarPath)) {
            return null;
        }
        BoxLangRuntimeSelection selection = new BoxLangRuntimeSelection();
        selection.jarPath = jarPath;
        selection.resolvedVersion = resolvedVersion;
        return selection;
    }

    private static void writeMetadata(Path versionDir, String resolvedVersion) throws IOException {
        Path metadata = versionDir.resolve("version.json");
        String content = "{\"name\":\"" + resolvedVersion + "\"}";
        Files.writeString(metadata, content, StandardCharsets.UTF_8);
    }

    public static String normalizeVersionName(String version) {
        if (version == null) {
            return null;
        }
        String normalized = version.trim();
        if (normalized.endsWith(".jar")) {
            normalized = normalized.substring(0, normalized.length() - 4);
        }
        if (!normalized.startsWith("boxlang-")) {
            normalized = "boxlang-" + normalized;
        }
        return normalized;
    }
}
