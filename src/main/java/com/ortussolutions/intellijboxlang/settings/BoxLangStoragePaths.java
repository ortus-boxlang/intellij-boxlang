package com.ortussolutions.intellijboxlang.settings;

import com.intellij.openapi.application.PathManager;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class BoxLangStoragePaths {
    private static final String ROOT_DIR_NAME = "boxlang";

    private BoxLangStoragePaths() {
    }

    public static Path getCacheRoot() {
        return Paths.get(PathManager.getSystemPath(), ROOT_DIR_NAME);
    }

    public static Path getLspCacheRoot() {
        return getCacheRoot().resolve("lsp");
    }

    public static Path getRuntimeCacheRoot() {
        return getCacheRoot().resolve("runtime");
    }
}
