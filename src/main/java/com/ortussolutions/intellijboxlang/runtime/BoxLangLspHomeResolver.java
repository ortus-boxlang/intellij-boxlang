package com.ortussolutions.intellijboxlang.runtime;

import com.intellij.openapi.project.Project;
import com.ortussolutions.intellijboxlang.settings.BoxLangResolvedSettings;
import com.ortussolutions.intellijboxlang.settings.BoxLangStoragePaths;
import java.nio.file.Path;

public final class BoxLangLspHomeResolver {
    private BoxLangLspHomeResolver() {
    }

    public static Path resolve(Project project, BoxLangResolvedSettings settings) {
        if (settings.lspBoxLangHome != null && !settings.lspBoxLangHome.isBlank()) {
            Path customPath = Path.of(settings.lspBoxLangHome);
            if (!customPath.isAbsolute()) {
                String basePath = project.getBasePath();
                if (basePath != null) {
                    return Path.of(basePath).resolve(customPath);
                }
            }
            return customPath;
        }

        String projectKey = project.getLocationHash();
        return BoxLangStoragePaths.getCacheRoot().resolve("lsp-home").resolve(projectKey);
    }
}
