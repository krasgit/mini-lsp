package mini.lsp.protocol;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import mini.lsp.workspace.WorkspaceManager;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionOptions;
import org.eclipse.lsp4j.CompletionOptions;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.RenameOptions;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.ServerInfo;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.TextDocumentSyncOptions;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

public final class LspServer implements LanguageServer, LanguageClientAware {

    private final WorkspaceManager workspace = new WorkspaceManager();
    private final TextDocumentService textDocuments = new LspTextDocumentService(this, workspace);
    private final WorkspaceService workspaceService = new LspWorkspaceService();
    private volatile LanguageClient client;
    private volatile boolean shutdown;

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        Path root = root(params);
        try {
            workspace.configure(root, params.getInitializationOptions());
        } catch (RuntimeException e) {
            workspace.configure(root, null);
            log("workspace setup failed: " + e.getMessage());
        }

        TextDocumentSyncOptions sync = new TextDocumentSyncOptions();
        sync.setOpenClose(true);
        sync.setChange(TextDocumentSyncKind.Full);

        ServerCapabilities caps = new ServerCapabilities();
        caps.setTextDocumentSync(sync);
        caps.setPositionEncoding("utf-16");
        caps.setDefinitionProvider(true);
        caps.setReferencesProvider(true);
        caps.setHoverProvider(true);
        caps.setDocumentSymbolProvider(true);
        caps.setCompletionProvider(new CompletionOptions(false, List.of(".", ":")));
        caps.setCodeActionProvider(new CodeActionOptions(List.of(CodeActionKind.QuickFix)));
        caps.setRenameProvider(new RenameOptions(true));

        ServerInfo info = new ServerInfo("mini-lsp", "0.1.0");
        return CompletableFuture.completedFuture(new InitializeResult(caps, info));
    }

    @Override
    public void initialized(org.eclipse.lsp4j.InitializedParams params) {
        log("mini-lsp ready: " + workspace.root());
    }

    @Override
    public CompletableFuture<Object> shutdown() {
        shutdown = true;
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void exit() {
        System.exit(shutdown ? 0 : 1);
    }

    @Override
    public TextDocumentService getTextDocumentService() {
        return textDocuments;
    }

    @Override
    public WorkspaceService getWorkspaceService() {
        return workspaceService;
    }

    @Override
    public void connect(LanguageClient client) {
        this.client = client;
    }

    void open(String uri, String text) {
        workspace.open(uri, text);
        publish(uri);
    }

    void change(String uri, String text) {
        workspace.change(uri, text);
        publish(uri);
    }

    void save(String uri, String text) {
        if (text != null) workspace.change(uri, text);
        publish(uri);
    }

    void close(String uri) {
        workspace.close(uri);
        LanguageClient c = client;
        if (c != null) c.publishDiagnostics(new PublishDiagnosticsParams(uri, List.of()));
    }

    private void publish(String uri) {
        LanguageClient c = client;
        if (c == null) return;
        List<Diagnostic> diagnostics;
        try {
            diagnostics = workspace.diagnostics(uri);
        } catch (Throwable t) {
            diagnostics = List.of();
            log("analysis failed for " + uri + ": " + t);
        }
        c.publishDiagnostics(new PublishDiagnosticsParams(uri, diagnostics));
    }

    void error(String message, Throwable error) {
        log(message + ": " + error);
    }

    private void log(String message) {
        LanguageClient c = client;
        if (c == null) return;
        c.logMessage(new MessageParams(MessageType.Log, message));
    }

    private static Path root(InitializeParams params) {
        List<WorkspaceFolder> folders = params.getWorkspaceFolders();
        if (folders != null && !folders.isEmpty()) {
            Path p = path(folders.get(0).getUri());
            if (p != null) return p;
        }
        Path p = path(params.getRootUri());
        if (p != null) return p;
        if (params.getRootPath() != null && !params.getRootPath().isBlank()) {
            return Path.of(params.getRootPath()).toAbsolutePath().normalize();
        }
        return Path.of("").toAbsolutePath().normalize();
    }

    private static Path path(String uri) {
        if (uri == null || uri.isBlank()) return null;
        try {
            return Path.of(URI.create(uri)).toAbsolutePath().normalize();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
