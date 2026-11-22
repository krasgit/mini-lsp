package mini.lsp.protocol;

import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.services.WorkspaceService;

final class LspWorkspaceService implements WorkspaceService {

    @Override
    public void didChangeConfiguration(DidChangeConfigurationParams params) {
    }

    @Override
    public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
    }

    @Override
    public CompletableFuture<Object> executeCommand(org.eclipse.lsp4j.ExecuteCommandParams params) {
        return CompletableFuture.completedFuture(null);
    }
}
