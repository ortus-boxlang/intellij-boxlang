package com.ortussolutions.intellijboxlang.runtime;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.ortussolutions.intellijboxlang.settings.BoxLangResolvedSettings;
import com.ortussolutions.intellijboxlang.settings.BoxLangSettingsResolver;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jetbrains.annotations.NotNull;

public final class BoxLangLspBootstrapService {
    private static final java.util.concurrent.ConcurrentHashMap<Project, Object> PROJECT_LOCKS =
        new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<Project, Boolean> LSP_PROMPTED =
        new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<Project, Boolean> RUNTIME_PROMPTED =
        new java.util.concurrent.ConcurrentHashMap<>();
    private static final com.intellij.openapi.diagnostic.Logger LOG =
        com.intellij.openapi.diagnostic.Logger.getInstance(BoxLangLspBootstrapService.class);
    private BoxLangLspBootstrapService() {
    }

    public static LspBootstrapResult prepare(Project project) throws IOException {
        LOG.info("Preparing BoxLang LSP bootstrap");
        synchronized (getProjectLock(project)) {
            BoxLangResolvedSettings settings = BoxLangSettingsResolver.resolve(project);
            LspModuleInfo lspModule = resolveLspModule(project, settings);
            LspRequirements requirements = LspRequirementsReader.read(lspModule.boxJsonPath);

            String requiredVersion = settings.lspBoxLangVersion != null ? settings.lspBoxLangVersion
                : (requirements != null ? requirements.minimumBoxLangVersion : null);
            if (requiredVersion == null || requiredVersion.isBlank()) {
                throw new IOException("Unable to determine required BoxLang version for LSP.");
            }

            boolean treatAsMinimum = settings.lspBoxLangVersion == null || settings.lspBoxLangVersion.isBlank();
            BoxLangRuntimeInfo runtimeInfo = BoxLangRuntimeResolver.resolveLspRuntime(requiredVersion, treatAsMinimum);
        LOG.info("Resolved BoxLang runtime for LSP: required=" + requiredVersion + ", resolved=" + runtimeInfo.resolvedVersion);
        BoxLangRuntimeSelection runtimeSelection = ensureRuntime(project, runtimeInfo, settings);

            LspBootstrapResult result = new LspBootstrapResult();
            Path moduleRoot = lspModule.modulePath.getParent();
            result.lspModulePath = moduleRoot != null ? moduleRoot : lspModule.modulePath;
            result.lspBoxLangHome = ensureLspHome(project, settings);
            result.boxLangJarPath = runtimeSelection.jarPath;
            result.boxLangVersion = runtimeSelection.resolvedVersion;
            return result;
        }
    }

    private static Path ensureLspHome(Project project, BoxLangResolvedSettings settings) throws IOException {
        Path home = BoxLangLspHomeResolver.resolve(project, settings);
        Files.createDirectories(home);
        Files.createDirectories(home.resolve("modules"));
        return home;
    }

    private static LspModuleInfo resolveLspModule(Project project, BoxLangResolvedSettings settings) throws IOException {
        LspModuleInfo info = LspModuleResolver.resolve(settings);
        if (settings.lspVersion == null || settings.lspVersion.isBlank()) {
            throw new IOException("LSP version is not configured.");
        }
        if (!info.needsDownload) {
            return info;
        }

        if (settings.promptForDownloads && !hasPrompted(LSP_PROMPTED, project)) {
            if (!BoxLangPromptService.confirmDownload(project,
                "Download BoxLang LSP",
                "BoxLang LSP module (" + settings.lspVersion + ") is not installed. Download now?")) {
                throw new IOException("BoxLang LSP module download was declined.");
            }
            LSP_PROMPTED.put(project, true);
        }

        ProgressManager.getInstance().run(new DownloadTask(project, "Downloading BoxLang LSP module") {
            @Override
            protected void runTask(@NotNull ProgressIndicator indicator) throws IOException {
                LOG.info("Downloading bx-lsp " + settings.lspVersion + " to " + info.modulePath);
                ForgeBoxLspInstaller.install(settings.lspVersion, info.modulePath, indicator);
            }
        });

        LspModuleInfo refreshed = LspModuleResolver.resolve(settings);
        if (refreshed.needsDownload) {
            throw new IOException("BoxLang LSP module installation failed.");
        }
        return refreshed;
    }

    private static Object getProjectLock(Project project) {
        return PROJECT_LOCKS.computeIfAbsent(project, key -> new Object());
    }

    private static boolean hasPrompted(java.util.concurrent.ConcurrentHashMap<Project, Boolean> map, Project project) {
        return Boolean.TRUE.equals(map.get(project));
    }

    private static BoxLangRuntimeSelection ensureRuntime(Project project, BoxLangRuntimeInfo runtimeInfo, BoxLangResolvedSettings settings) throws IOException {
        if (!runtimeInfo.needsDownload) {
            BoxLangRuntimeSelection selection = new BoxLangRuntimeSelection();
            selection.jarPath = Path.of(runtimeInfo.jarPath);
            selection.resolvedVersion = runtimeInfo.resolvedVersion;
            return selection;
        }

        if (settings.promptForDownloads && !hasPrompted(RUNTIME_PROMPTED, project)) {
            if (!BoxLangPromptService.confirmDownload(project,
                "Download BoxLang Runtime",
                "BoxLang runtime ^" + runtimeInfo.requestedVersion + " is required for the LSP. Download now?")) {
                throw new IOException("BoxLang runtime download was declined.");
            }
            RUNTIME_PROMPTED.put(project, true);
        }

        ProgressManager.getInstance().run(new DownloadTask(project, "Downloading BoxLang runtime") {
            @Override
            protected void runTask(@NotNull ProgressIndicator indicator) throws IOException {
                BoxLangRuntimeInstaller.installRuntime(runtimeInfo.resolvedVersion, runtimeInfo.downloadUrl, indicator);
            }
        });

        BoxLangRuntimeSelection selection = BoxLangRuntimeInstaller.resolveCachedJar(runtimeInfo.resolvedVersion);
        if (selection == null) {
            throw new IOException("BoxLang runtime installation failed.");
        }
        return selection;
    }

    private abstract static class DownloadTask extends Task.WithResult<Void, IOException> {
        protected DownloadTask(Project project, @NlsContexts.ProgressTitle String title) {
            super(project, title, true);
        }

        @Override
        protected Void compute(@NotNull ProgressIndicator indicator) throws IOException {
            runTask(indicator);
            return null;
        }

        protected abstract void runTask(@NotNull ProgressIndicator indicator) throws IOException;
    }
}
