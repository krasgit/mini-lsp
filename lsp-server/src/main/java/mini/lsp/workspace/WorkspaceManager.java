package mini.lsp.workspace;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import mini.compiler.JdtEnvironment;
import mini.lsp.codeaction.CodeActionProvider;
import mini.lsp.completion.CompletionProvider;
import mini.lsp.diagnostics.JdtDiagnostics;
import mini.lsp.hover.HoverProvider;
import mini.lsp.navigation.Navigator;
import mini.lsp.symbols.DocumentSymbols;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.IPackageBinding;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

public final class WorkspaceManager {

    private final Map<String, Document> documents = new LinkedHashMap<>();
    private final Map<String, Analysis> analyses = new HashMap<>();
    private Path root;
    private SourceIndex index;
    private Path corelib;
    private List<Path> sourcepath = List.of();
    private List<Path> classpath = List.of();

    public synchronized void configure(Path root, Object options) {
        this.root = root.toAbsolutePath().normalize();
        JsonObject settings = settings(options);

        Path configuredCorelib = path(this.root, settings, "corelib");
        this.corelib = configuredCorelib != null ? configuredCorelib : findCorelib(this.root);
        this.sourcepath = discoverSourceRoots(this.root, settings);
        List<Path> configuredClasspath = configuredPaths(this.root, settings, "classpath");
        this.classpath = configuredClasspath.isEmpty() ? vendoredJars(this.root) : configuredClasspath;
    }

    public synchronized Path root() {
        return root;
    }

    public synchronized void open(String uri, String text) {
        documents.put(uri, new Document(uri(uri), text));
        invalidate();
    }

    public synchronized void change(String uri, String text) {
        Document d = documents.get(uri);
        if (d == null) {
            documents.put(uri, new Document(uri(uri), text));
        } else {
            documents.put(uri, new Document(d.path(), text));
        }
        invalidate();
    }

    public synchronized void close(String uri) {
        documents.remove(uri);
        invalidate();
    }

    public synchronized String text(String uri) {
        Document d = documents.get(uri);
        return d == null ? "" : d.text();
    }

    public synchronized List<Diagnostic> diagnostics(String uri) {
        Analysis analysis = analysis(uri);
        return analysis == null ? List.of() : JdtDiagnostics.analyze(analysis.unit(), analysis.text());
    }

    public synchronized List<Location> definition(String uri, Position position) {
        Analysis analysis = analysis(uri);
        if (analysis == null) return List.of();
        return Navigator.definition(analysis, index(), position, this::analysisFor);
    }

    public synchronized List<Location> references(String uri, Position position, boolean includeDeclaration) {
        Analysis analysis = analysis(uri);
        if (analysis == null) return List.of();
        return Navigator.references(analysis, index(), position, includeDeclaration, this::analysisFor);
    }

    public synchronized Hover hover(String uri, Position position) {
        Analysis analysis = analysis(uri);
        return analysis == null ? null : HoverProvider.hover(analysis, position);
    }

    public synchronized List<DocumentSymbol> symbols(String uri) {
        Analysis analysis = analysis(uri);
        return analysis == null ? List.of() : DocumentSymbols.symbols(analysis);
    }

    public synchronized List<CompletionItem> completion(String uri, Position position) {
        Analysis analysis = analysis(uri);
        return analysis == null ? List.of() : CompletionProvider.complete(analysis, index(), position);
    }

    public synchronized List<Either<Command, CodeAction>> codeActions(String uri, CodeActionParams params) {
        Analysis analysis = analysis(uri);
        if (analysis == null) return List.of();
        List<Diagnostic> diagnostics = params.getContext() == null
                ? List.of() : params.getContext().getDiagnostics();
        List<CodeAction> actions = CodeActionProvider.actions(
                analysis, index(), params.getRange(), diagnostics, this::analysisFor);
        List<Either<Command, CodeAction>> out = new ArrayList<>(actions.size());
        for (CodeAction action : actions) out.add(Either.forRight(action));
        return out;
    }

    public synchronized Range prepareRename(String uri, Position position) {
        Analysis analysis = analysis(uri);
        if (analysis == null) return null;
        ASTNode node = AstUtil.nodeAt(analysis.unit(), position, analysis.text().length());
        IBinding binding = AstUtil.binding(node);
        if (!renameable(binding)) return null;
        SimpleName name = AstUtil.nameNode(node);
        if (name == null) return null;
        return AstUtil.range(analysis.unit(), name, analysis.text().length());
    }

    public synchronized WorkspaceEdit rename(String uri, Position position, String newName) {
        Analysis analysis = analysis(uri);
        if (analysis == null || !validIdentifier(newName)) return null;
        ASTNode node = AstUtil.nodeAt(analysis.unit(), position, analysis.text().length());
        IBinding binding = AstUtil.binding(node);
        if (!renameable(binding)) return null;

        List<Location> locations = Navigator.references(
                analysis, index(), position, true, this::analysisFor);
        if (locations.isEmpty()) return null;

        Map<String, List<TextEdit>> changes = new LinkedHashMap<>();
        for (Location location : locations) {
            changes.computeIfAbsent(location.getUri(), key -> new ArrayList<>())
                    .add(new TextEdit(location.getRange(), newName));
        }
        return new WorkspaceEdit(changes);
    }

    private static boolean renameable(IBinding binding) {
        if (binding == null) return false;
        if (binding instanceof IPackageBinding) return false;
        return !(binding instanceof IMethodBinding method && method.isConstructor());
    }

    private static boolean validIdentifier(String name) {
        if (name == null || name.isBlank()) return false;
        if (!Character.isJavaIdentifierStart(name.charAt(0))) return false;
        for (int i = 1; i < name.length(); i++) {
            if (!Character.isJavaIdentifierPart(name.charAt(i))) return false;
        }
        return !javax.lang.model.SourceVersion.isKeyword(name);
    }

    private synchronized SourceIndex index() {
        if (index == null) index = SourceIndex.build(corelib, sourcepath);
        return index;
    }

    private synchronized Analysis analysis(String uri) {
        Document document = documents.get(uri);
        if (document == null || document.path() == null || !document.path().toString().endsWith(".java")) {
            return null;
        }
        Analysis cached = analyses.get(uri);
        if (cached != null && cached.text().equals(document.text())) return cached;
        Analysis analysis = parse(document.path(), document.text());
        analyses.put(uri, analysis);
        return analysis;
    }

    private Analysis analysisFor(Path path) {
        for (Analysis analysis : analyses.values()) {
            if (analysis.path().equals(path)) return analysis;
        }
        try {
            return parse(path, Files.readString(path));
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private Analysis parse(Path path, String text) {
        ASTParser parser = JdtEnvironment.parser(corelib, sourcepath, classpath, path.toString());
        parser.setSource(text.toCharArray());
        CompilationUnit unit = (CompilationUnit) parser.createAST(null);
        return new Analysis(path, text, unit);
    }

    private void invalidate() {
        analyses.clear();
        index = null;
    }

    private static Path uri(String uri) {
        try {
            return Path.of(URI.create(uri)).toAbsolutePath().normalize();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static JsonObject settings(Object options) {
        if (options instanceof JsonObject object) return object;
        if (options instanceof JsonElement element && element.isJsonObject()) return element.getAsJsonObject();
        return new JsonObject();
    }

    private static Path path(Path root, JsonObject settings, String key) {
        if (settings == null || !settings.has(key) || !settings.get(key).isJsonPrimitive()) return null;
        String value = settings.get(key).getAsString();
        if (value == null || value.isBlank()) return null;
        return resolve(root, value);
    }

    private static List<Path> configuredPaths(Path root, JsonObject settings, String key) {
        if (settings == null || !settings.has(key)) return List.of();
        JsonElement value = settings.get(key);
        List<Path> paths = new ArrayList<>();
        if (value.isJsonArray()) {
            JsonArray array = value.getAsJsonArray();
            for (JsonElement item : array) {
                if (item.isJsonPrimitive()) paths.add(resolve(root, item.getAsString()));
            }
        } else if (value.isJsonPrimitive()) {
            paths.add(resolve(root, value.getAsString()));
        }
        return List.copyOf(paths);
    }

    private static Path resolve(Path root, String value) {
        Path path = Path.of(value);
        return (path.isAbsolute() ? path : root.resolve(path)).normalize();
    }

    private static Path findCorelib(Path root) {
        String env = System.getenv("MINI_CORELIB");
        if (env != null && !env.isBlank()) return resolve(root, env);

        Path current = root;
        while (current != null) {
            Path direct = current.resolve("corelib/src");
            if (isCorelib(direct)) return direct;
            Path own = current.resolve("src/java");
            if (isCorelib(own)) return own;
            current = current.getParent();
        }
        Path cwd = Path.of("corelib/src").toAbsolutePath().normalize();
        return isCorelib(cwd) ? cwd : null;
    }

    private static boolean isCorelib(Path path) {
        return Files.isRegularFile(path.resolve("java/lang/String.java"));
    }

    private static List<Path> vendoredJars(Path root) {
        Path current = root;
        while (current != null) {
            Path repository = current.resolve("third-party/repository");
            if (Files.isDirectory(repository)) {
                try (Stream<Path> files = Files.walk(repository)) {
                    return files.filter(p -> p.toString().endsWith(".jar")).sorted().toList();
                } catch (IOException ignored) {
                    return List.of();
                }
            }
            current = current.getParent();
        }
        return List.of();
    }

    private static List<Path> discoverSourceRoots(Path root, JsonObject settings) {
        Set<Path> roots = new LinkedHashSet<>();
        add(roots, root.resolve("src/main/java"));
        add(roots, root.resolve("src/test/java"));
        add(roots, root.resolve("src"));

        try (Stream<Path> children = Files.list(root)) {
            List<Path> dirs = children.filter(Files::isDirectory).sorted().toList();
            for (Path child : dirs) {
                if (Files.isRegularFile(child.resolve("module.xml")) || Files.isDirectory(child.resolve("src"))) {
                    add(roots, child.resolve("src/main/java"));
                    add(roots, child.resolve("src/test/java"));
                    add(roots, child.resolve("src"));
                }
            }
        } catch (IOException ignored) {
        }

        add(roots, root);
        roots.addAll(configuredPaths(root, settings, "sourcePaths"));
        return List.copyOf(roots);
    }

    private static void add(Collection<Path> roots, Path path) {
        if (Files.isDirectory(path)) roots.add(path.toAbsolutePath().normalize());
    }

    private record Document(Path path, String text) {
    }
}
