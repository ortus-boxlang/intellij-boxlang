package com.ortussolutions.intellijboxlang.lsp;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.execution.ParametersListUtil;
import com.ortussolutions.intellijboxlang.runtime.BoxLangLspBootstrapService;
import com.ortussolutions.intellijboxlang.runtime.LspBootstrapResult;
import com.ortussolutions.intellijboxlang.settings.BoxLangResolvedSettings;
import com.ortussolutions.intellijboxlang.settings.BoxLangSettingsResolver;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensCapabilities;
import org.eclipse.lsp4j.SemanticTokensClientCapabilitiesRequests;
import org.eclipse.lsp4j.SemanticTokensLegend;
import org.eclipse.lsp4j.SemanticTokensParams;
import org.eclipse.lsp4j.TextDocumentClientCapabilities;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageServer;

@Service(Service.Level.PROJECT)
public final class BoxLangLspClientService {
    private static final Logger LOG = Logger.getInstance(BoxLangLspClientService.class);
    private static final int CONNECT_TIMEOUT_MS = 10000;
    private static final int REQUEST_TIMEOUT_MS = 5000;
    private static final int INITIALIZE_TIMEOUT_MS = 20000;

    private final Project project;
    private final Map<String, Integer> documentVersions = new ConcurrentHashMap<>();
    private final Map<String, Long> documentStamps = new ConcurrentHashMap<>();
    private final Map<String, CachedTokens> tokenCache = new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicBoolean starting = new java.util.concurrent.atomic.AtomicBoolean(false);
    private final Object startLock = new Object();

    private Process process;
    private Socket socket;
    private LanguageServer server;
    private SemanticTokensLegend legend;

    public BoxLangLspClientService(Project project) {
        this.project = project;
    }

    public static BoxLangLspClientService getInstance(Project project) {
        return project.getService(BoxLangLspClientService.class);
    }

    public SemanticTokensLegend getLegend() {
        return legend;
    }

    public SemanticTokens requestSemanticTokens(VirtualFile file, Document document) {
        if (!ensureStarted()) {
            return null;
        }
        String uri = toUri(file);
        CachedTokens cached = tokenCache.get(uri);
        if (cached != null && cached.stamp == document.getModificationStamp()) {
            return cached.tokens;
        }
        syncDocument(uri, document);
        SemanticTokensParams params = new SemanticTokensParams(new TextDocumentIdentifier(uri));
        try {
            CompletableFuture<SemanticTokens> future = server.getTextDocumentService().semanticTokensFull(params);
            SemanticTokens tokens = future.get(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (tokens != null) {
                tokenCache.put(uri, new CachedTokens(document.getModificationStamp(), tokens));
            }
            return tokens;
        } catch (Exception e) {
            LOG.debug("Failed to request semantic tokens", e);
            return null;
        }
    }

    private boolean ensureStarted() {
        if (server != null) {
            return true;
        }
        if (ApplicationManager.getApplication().isDispatchThread()
            || ApplicationManager.getApplication().isReadAccessAllowed()) {
            ensureStartedAsync();
            return false;
        }
        synchronized (startLock) {
            if (server != null) {
                return true;
            }
            try {
                startServer();
                return true;
            } catch (Exception e) {
                LOG.warn("Unable to start BoxLang LSP", e);
                return false;
            }
        }
    }

    public void ensureStartedAsync() {
        if (server != null) {
            return;
        }
        if (!starting.compareAndSet(false, true)) {
            return;
        }
        LOG.info("BoxLang LSP async start requested");
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            synchronized (startLock) {
                if (server != null) {
                    starting.set(false);
                    return;
                }
                try {
                    LOG.info("Starting BoxLang LSP process");
                    startServer();
                    LOG.info("BoxLang LSP started");
                } catch (Exception e) {
                    LOG.warn("Unable to start BoxLang LSP", e);
                } finally {
                    starting.set(false);
                }
            }
        });
    }

    private void startServer() throws Exception {
        BoxLangResolvedSettings settings = BoxLangSettingsResolver.resolve(project);
        LspBootstrapResult bootstrap = BoxLangLspBootstrapService.prepare(project);
        int port = allocatePort();

        GeneralCommandLine commandLine = new GeneralCommandLine(resolveJavaExecutable(settings));
        commandLine.withCharset(StandardCharsets.UTF_8);
        commandLine.withEnvironment("BOXLANG_HOME", bootstrap.lspBoxLangHome.toString());
        commandLine.withEnvironment("BOXLANG_MODULESDIRECTORY", bootstrap.lspModulePath.toString());
        commandLine.withEnvironment("CLASSPATH", bootstrap.boxLangJarPath.toString());
        if (settings.javaHome != null && !settings.javaHome.isBlank()) {
            commandLine.withEnvironment("JAVA_HOME", settings.javaHome);
        }

        commandLine.addParameters(buildJvmArgs(settings));
        commandLine.addParameter("ortus.boxlang.runtime.BoxRunner");
        commandLine.addParameter("module:bx-lsp");
        commandLine.addParameters("--debug-server-port", String.valueOf(port));

        process = commandLine.createProcess();
        socket = connectWithRetries(port);

        BoxLangLspClient client = new BoxLangLspClient();
        var launcher = LSPLauncher.createClientLauncher(client, socket.getInputStream(), socket.getOutputStream());
        server = launcher.getRemoteProxy();
        launcher.startListening();

        InitializeResult result = server.initialize(createInitializeParams())
            .get(INITIALIZE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        legend = result.getCapabilities() != null && result.getCapabilities().getSemanticTokensProvider() != null
            ? result.getCapabilities().getSemanticTokensProvider().getLegend()
            : null;
        server.initialized(new InitializedParams());
    }

    private InitializeParams createInitializeParams() {
        InitializeParams params = new InitializeParams();
        params.setRootUri(getProjectUri());
        params.setWorkspaceFolders(getWorkspaceFolders());
        params.setCapabilities(createClientCapabilities());
        params.setProcessId((int) ProcessHandle.current().pid());
        return params;
    }

    private ClientCapabilities createClientCapabilities() {
        ClientCapabilities capabilities = new ClientCapabilities();
        TextDocumentClientCapabilities textDocument = new TextDocumentClientCapabilities();
        SemanticTokensCapabilities semanticTokens = new SemanticTokensCapabilities();
        SemanticTokensClientCapabilitiesRequests requests = new SemanticTokensClientCapabilitiesRequests();
        requests.setFull(Either.forLeft(true));
        requests.setRange(Either.forLeft(false));
        semanticTokens.setRequests(requests);
        semanticTokens.setTokenTypes(List.of());
        semanticTokens.setTokenModifiers(List.of());
        semanticTokens.setFormats(List.of("relative"));
        semanticTokens.setOverlappingTokenSupport(true);
        semanticTokens.setMultilineTokenSupport(true);
        textDocument.setSemanticTokens(semanticTokens);
        capabilities.setTextDocument(textDocument);
        return capabilities;
    }

    private void syncDocument(String uri, Document document) {
        long stamp = document.getModificationStamp();
        Long cachedStamp = documentStamps.get(uri);
        if (cachedStamp != null && cachedStamp == stamp) {
            return;
        }

        int version = documentVersions.getOrDefault(uri, 0) + 1;
        documentVersions.put(uri, version);
        documentStamps.put(uri, stamp);

        if (version == 1) {
            TextDocumentItem item = new TextDocumentItem(uri, "boxlang", version, document.getText());
            server.getTextDocumentService().didOpen(new org.eclipse.lsp4j.DidOpenTextDocumentParams(item));
        } else {
            VersionedTextDocumentIdentifier identifier = new VersionedTextDocumentIdentifier(uri, version);
            TextDocumentContentChangeEvent change = new TextDocumentContentChangeEvent(document.getText());
            server.getTextDocumentService().didChange(
                new org.eclipse.lsp4j.DidChangeTextDocumentParams(identifier, List.of(change))
            );
        }
    }

    private int allocatePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private Socket connectWithRetries(int port) throws IOException {
        IOException lastError = null;
        long deadline = System.currentTimeMillis() + CONNECT_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            try {
                Socket socket = new Socket();
                socket.connect(new InetSocketAddress("127.0.0.1", port), CONNECT_TIMEOUT_MS);
                return socket;
            } catch (IOException e) {
                lastError = e;
                try {
                    Thread.sleep(200);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        if (lastError != null) {
            throw lastError;
        }
        throw new IOException("Unable to connect to BoxLang LSP");
    }

    private String resolveJavaExecutable(BoxLangResolvedSettings settings) {
        if (settings.javaHome == null || settings.javaHome.isBlank()) {
            return "java";
        }
        String javaExecutable = SystemInfo.isWindows ? "java.exe" : "java";
        return Path.of(settings.javaHome, "bin", javaExecutable).toString();
    }

    private List<String> buildJvmArgs(BoxLangResolvedSettings settings) {
        List<String> args = new ArrayList<>();
        int heapSize = settings.lspMaxHeapSize > 0 ? settings.lspMaxHeapSize : 512;
        args.add("-Xmx" + heapSize + "m");
        if (settings.lspJvmArgs != null && !settings.lspJvmArgs.isBlank()) {
            args.addAll(ParametersListUtil.parse(settings.lspJvmArgs));
        }
        return args;
    }

    private String getProjectUri() {
        String basePath = project.getBasePath();
        if (basePath == null) {
            return null;
        }
        return Path.of(basePath).toUri().toString();
    }

    private List<WorkspaceFolder> getWorkspaceFolders() {
        String basePath = project.getBasePath();
        if (basePath == null) {
            return List.of();
        }
        String uri = Path.of(basePath).toUri().toString();
        return List.of(new WorkspaceFolder(uri, project.getName()));
    }

    private String toUri(VirtualFile file) {
        return file.toNioPath().toUri().toString();
    }

    private static final class CachedTokens {
        private final long stamp;
        private final SemanticTokens tokens;

        private CachedTokens(long stamp, SemanticTokens tokens) {
            this.stamp = stamp;
            this.tokens = tokens;
        }
    }
}
