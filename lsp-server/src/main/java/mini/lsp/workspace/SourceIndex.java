package mini.lsp.workspace;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;

public final class SourceIndex {

    private static final Set<String> SKIPPED_DIRECTORIES = Set.of(
            ".git", ".idea", ".gradle", ".mvn", ".kiro", "target", "dist", "out",
            "build", "node_modules", "third-party");

    private final Map<String, TypeLocation> types;
    private final List<Path> files;

    private SourceIndex(Map<String, TypeLocation> types, List<Path> files) {
        this.types = types;
        this.files = files;
    }

    public static SourceIndex build(Path corelib, List<Path> roots) {
        Set<Path> files = new LinkedHashSet<>();
        if (corelib != null) collect(corelib, files);
        for (Path root : roots) collect(root, files);

        Map<String, TypeLocation> types = new LinkedHashMap<>();
        for (Path file : files) {
            String text;
            try {
                text = Files.readString(file);
            } catch (IOException e) {
                continue;
            }
            CompilationUnit unit = parse(file, text);
            if (unit == null) continue;
            String pkg = unit.getPackage() == null ? ""
                    : unit.getPackage().getName().getFullyQualifiedName();
            for (Object item : unit.types()) {
                if (item instanceof AbstractTypeDeclaration declaration) {
                    addTypes(file, unit, pkg, declaration, types);
                }
            }
        }
        return new SourceIndex(Map.copyOf(types), List.copyOf(files));
    }

    public TypeLocation type(String qualifiedName) {
        if (qualifiedName == null) return null;
        TypeLocation location = types.get(qualifiedName);
        if (location != null) return location;
        String erased = qualifiedName;
        int generic = erased.indexOf('<');
        if (generic >= 0) erased = erased.substring(0, generic);
        return types.get(erased);
    }

    public List<TypeLocation> typesBySimpleName(String simpleName) {
        List<TypeLocation> matches = new java.util.ArrayList<>();
        if (simpleName == null || simpleName.isBlank()) return matches;
        for (TypeLocation location : types.values()) {
            String qualified = location.qualifiedName();
            int dot = qualified.lastIndexOf('.');
            String simple = dot < 0 ? qualified : qualified.substring(dot + 1);
            if (simple.equals(simpleName)) matches.add(location);
        }
        return matches;
    }

    public List<Path> files() {
        return files;
    }

    public Set<String> simpleTypeNames() {
        Set<String> names = new LinkedHashSet<>();
        for (String qualified : types.keySet()) {
            int dot = qualified.lastIndexOf('.');
            names.add(dot < 0 ? qualified : qualified.substring(dot + 1));
        }
        return names;
    }

    private static CompilationUnit parse(Path file, String text) {
        try {
            ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
            parser.setSource(text.toCharArray());
            parser.setUnitName(file.toString());
            return (CompilationUnit) parser.createAST(null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static void addTypes(Path file, CompilationUnit unit, String prefix,
                                 AbstractTypeDeclaration declaration, Map<String, TypeLocation> types) {
        String simple = declaration.getName().getIdentifier();
        String qualified = prefix == null || prefix.isBlank() ? simple : prefix + "." + simple;
        int start = declaration.getName().getStartPosition();
        int end = start + declaration.getName().getLength();
        types.putIfAbsent(qualified, new TypeLocation(
                qualified, file, start, end,
                unit.getLineNumber(start) - 1, unit.getColumnNumber(start),
                unit.getLineNumber(end) - 1, unit.getColumnNumber(end)));
        for (Object item : declaration.bodyDeclarations()) {
            if (item instanceof AbstractTypeDeclaration child) {
                addTypes(file, unit, qualified, child, types);
            }
        }
    }

    private static void collect(Path root, Set<Path> files) {
        if (root == null || !Files.isDirectory(root)) return;
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (!dir.equals(root) && SKIPPED_DIRECTORIES.contains(dir.getFileName().toString())) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    String name = file.getFileName().toString();
                    if (name.endsWith(".java") && !name.equals("module-info.java")) {
                        files.add(file.toAbsolutePath().normalize());
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
        }
    }

    public record TypeLocation(String qualifiedName, Path path, int start, int end,
                               int startLine, int startColumn, int endLine, int endColumn) {
    }
}
