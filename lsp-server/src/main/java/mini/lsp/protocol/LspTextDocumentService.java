package mini.lsp.protocol;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import mini.lsp.workspace.WorkspaceManager;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.PrepareRenameDefaultBehavior;
import org.eclipse.lsp4j.PrepareRenameParams;
import org.eclipse.lsp4j.PrepareRenameResult;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ReferenceParams;
import org.eclipse.lsp4j.RenameParams;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.Either3;
import org.eclipse.lsp4j.services.TextDocumentService;

final class LspTextDocumentService implements TextDocumentService {

    private final LspServer server;
    private final WorkspaceManager workspace;

    LspTextDocumentService(LspServer server, WorkspaceManager workspace) {
        this.server = server;
        this.workspace = workspace;
    }

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        server.open(params.getTextDocument().getUri(), params.getTextDocument().getText());
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        List<TextDocumentContentChangeEvent> changes = params.getContentChanges();
        String text = changes.isEmpty() ? workspace.text(params.getTextDocument().getUri())
                : changes.get(changes.size() - 1).getText();
        server.change(params.getTextDocument().getUri(), text);
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        server.close(params.getTextDocument().getUri());
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {
        server.save(params.getTextDocument().getUri(), params.getText());
    }

    @Override
    public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> definition(
            DefinitionParams params) {
        String uri = params.getTextDocument().getUri();
        try {
            List<? extends Location> locations = workspace.definition(uri, params.getPosition());
            return CompletableFuture.completedFuture(
                    Either.<List<? extends Location>, List<? extends LocationLink>>forLeft(locations));
        } catch (Throwable error) {
            server.error("definition failed for " + uri, error);
            return CompletableFuture.completedFuture(
                    Either.<List<? extends Location>, List<? extends LocationLink>>forLeft(List.of()));
        }
    }

    @Override
    public CompletableFuture<List<? extends Location>> references(ReferenceParams params) {
        String uri = params.getTextDocument().getUri();
        try {
            boolean include = params.getContext() != null && params.getContext().isIncludeDeclaration();
            return CompletableFuture.completedFuture(
                    workspace.references(uri, params.getPosition(), include));
        } catch (Throwable error) {
            server.error("references failed for " + uri, error);
            return CompletableFuture.completedFuture(List.of());
        }
    }

    @Override
    public CompletableFuture<Hover> hover(HoverParams params) {
        String uri = params.getTextDocument().getUri();
        try {
            return CompletableFuture.completedFuture(workspace.hover(uri, params.getPosition()));
        } catch (Throwable error) {
            server.error("hover failed for " + uri, error);
            return CompletableFuture.completedFuture(null);
        }
    }

    @Override
    public CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> documentSymbol(
            DocumentSymbolParams params) {
        String uri = params.getTextDocument().getUri();
        try {
            List<DocumentSymbol> symbols = workspace.symbols(uri);
            List<Either<SymbolInformation, DocumentSymbol>> out = new ArrayList<>(symbols.size());
            for (DocumentSymbol symbol : symbols) out.add(Either.forRight(symbol));
            return CompletableFuture.completedFuture(out);
        } catch (Throwable error) {
            server.error("document symbols failed for " + uri, error);
            return CompletableFuture.completedFuture(List.of());
        }
    }

    @Override
    public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams params) {
        String uri = params.getTextDocument().getUri();
        try {
            List<CompletionItem> items = workspace.completion(uri, params.getPosition());
            return CompletableFuture.completedFuture(Either.forLeft(items));
        } catch (Throwable error) {
            server.error("completion failed for " + uri, error);
            return CompletableFuture.completedFuture(Either.forLeft(List.of()));
        }
    }

    @Override
    public CompletableFuture<List<Either<Command, CodeAction>>> codeAction(CodeActionParams params) {
        String uri = params.getTextDocument().getUri();
        try {
            return CompletableFuture.completedFuture(workspace.codeActions(uri, params));
        } catch (Throwable error) {
            server.error("code actions failed for " + uri, error);
            return CompletableFuture.completedFuture(List.of());
        }
    }

    @Override
    public CompletableFuture<Either3<Range, PrepareRenameResult, PrepareRenameDefaultBehavior>> prepareRename(
            PrepareRenameParams params) {
        String uri = params.getTextDocument().getUri();
        try {
            Range range = workspace.prepareRename(uri, params.getPosition());
            return CompletableFuture.completedFuture(
                    range == null ? null : Either3.forFirst(range));
        } catch (Throwable error) {
            server.error("prepare rename failed for " + uri, error);
            return CompletableFuture.completedFuture(null);
        }
    }

    @Override
    public CompletableFuture<WorkspaceEdit> rename(RenameParams params) {
        String uri = params.getTextDocument().getUri();
        try {
            return CompletableFuture.completedFuture(
                    workspace.rename(uri, params.getPosition(), params.getNewName()));
        } catch (Throwable error) {
            server.error("rename failed for " + uri, error);
            return CompletableFuture.completedFuture(null);
        }
    }
}
