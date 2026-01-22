package com.ortussolutions.intellijboxlang.lsp;

import com.intellij.openapi.diagnostic.Logger;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.services.LanguageClient;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class BoxLangLspClient implements LanguageClient {
    private static final Logger LOG = Logger.getInstance(BoxLangLspClient.class);

    @Override
    public void publishDiagnostics(PublishDiagnosticsParams diagnostics) {
        LOG.debug("Diagnostics: " + diagnostics.getUri());
    }

    @Override
    public void showMessage(MessageParams messageParams) {
        LOG.debug("LSP message: " + messageParams.getMessage());
    }

    @Override
    public CompletableFuture<List<WorkspaceFolder>> workspaceFolders() {
        return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public void logMessage(MessageParams message) {
        LOG.debug("LSP log: " + message.getMessage());
    }

    @Override
    public void telemetryEvent(Object object) {
        LOG.debug("LSP telemetry event received");
    }

    @Override
    public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams requestParams) {
        return CompletableFuture.completedFuture(null);
    }
}
