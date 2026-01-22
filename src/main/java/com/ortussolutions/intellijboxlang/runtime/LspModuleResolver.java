package com.ortussolutions.intellijboxlang.runtime;

import com.ortussolutions.intellijboxlang.settings.BoxLangResolvedSettings;
import com.ortussolutions.intellijboxlang.settings.BoxLangStoragePaths;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

public final class LspModuleResolver {
    private LspModuleResolver() {
    }

    public static LspModuleInfo resolve(BoxLangResolvedSettings settings) {
        LspModuleInfo info = new LspModuleInfo();
        info.requestedVersion = settings.lspVersion;

        if (info.requestedVersion == null || info.requestedVersion.isBlank()) {
            info.needsDownload = true;
            return info;
        }

        Path moduleRoot = BoxLangStoragePaths.getLspCacheRoot().resolve(info.requestedVersion);
        info.modulePath = moduleRoot.resolve("bx-lsp");
        info.boxJsonPath = findBoxJson(info.modulePath);
        info.needsDownload = info.boxJsonPath == null;
        return info;
    }

    private static Path findBoxJson(Path moduleRoot) {
        if (moduleRoot == null || !Files.exists(moduleRoot)) {
            return null;
        }
        try (Stream<Path> stream = Files.walk(moduleRoot)) {
            Optional<Path> match = stream
                .filter(path -> path.getFileName().toString().equals("box.json"))
                .findFirst();
            return match.orElse(null);
        } catch (IOException ignored) {
            return null;
        }
    }
}
