package mini.lsp;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

import mini.lsp.protocol.LspServer;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.MessageConsumer;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;

public final class MiniLanguageServer {

    private MiniLanguageServer() {
    }

    public static void main(String[] args) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "mini-lsp");
            t.setDaemon(false);
            return t;
        });
        LspServer server = new LspServer();
        Function<MessageConsumer, MessageConsumer> trace = Function.identity();
        Launcher<LanguageClient> launcher = LSPLauncher.createServerLauncher(
                server, System.in, System.out, executor, trace);
        server.connect(launcher.getRemoteProxy());
        launcher.startListening().get();
        executor.shutdown();
    }
}
