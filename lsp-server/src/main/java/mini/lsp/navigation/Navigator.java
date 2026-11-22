package mini.lsp.navigation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import mini.lsp.workspace.Analysis;
import mini.lsp.workspace.AstUtil;
import mini.lsp.workspace.SourceIndex;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AnnotationTypeMemberDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnumConstantDeclaration;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.TypeParameter;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

public final class Navigator {

    private Navigator() {
    }

    public static List<Location> definition(Analysis current, SourceIndex index, Position position,
                                            Function<Path, Analysis> parser) {
        ASTNode node = AstUtil.nodeAt(current.unit(), position, current.text().length());
        IBinding binding = AstUtil.binding(node);
        Location location = declaration(current, index, parser, binding);
        return location == null ? List.of() : List.of(location);
    }

    public static List<Location> references(Analysis current, SourceIndex index, Position position,
                                            boolean includeDeclaration, Function<Path, Analysis> parser) {
        ASTNode node = AstUtil.nodeAt(current.unit(), position, current.text().length());
        IBinding target = AstUtil.binding(node);
        if (target == null || target.getKey() == null || target.getName() == null) return List.of();

        String key = target.getKey();
        String name = target.getName();
        boolean local = target instanceof IVariableBinding variable && !variable.isField();
        List<Path> files;
        if (local) {
            files = List.of(current.path());
        } else {
            files = new ArrayList<>(index.files());
            if (!files.contains(current.path())) files.add(0, current.path());
        }
        Set<String> seen = new LinkedHashSet<>();
        List<Location> locations = new ArrayList<>();

        for (Path file : files) {
            String text;
            CompilationUnit unit;
            if (file.equals(current.path())) {
                text = current.text();
                unit = current.unit();
            } else {
                Analysis analysis = parser.apply(file);
                if (analysis == null) continue;
                text = analysis.text();
                unit = analysis.unit();
            }
            if (!text.contains(name)) continue;

            unit.accept(new ASTVisitor() {
                @Override
                public boolean visit(SimpleName candidate) {
                    if (!name.equals(candidate.getIdentifier())) return true;
                    if (AstUtil.isPackage(candidate)) return true;
                    if (AstUtil.isImport(candidate) && !includeDeclaration) return true;
                    if (!includeDeclaration && AstUtil.isDeclarationName(candidate)) return true;

                    IBinding found = candidate.resolveBinding();
                    if (found == null) found = AstUtil.binding(candidate);
                    if (found == null || !key.equals(found.getKey())) return true;

                    Range range = AstUtil.range(unit, candidate, text.length());
                    String uri = file.toUri().toString();
                    String id = uri + "#" + range.getStart().getLine() + ":" + range.getStart().getCharacter();
                    if (seen.add(id)) locations.add(new Location(uri, range));
                    return true;
                }
            });
        }
        return locations;
    }

    private static Location declaration(Analysis current, SourceIndex index,
                                        Function<Path, Analysis> parser, IBinding binding) {
        if (binding == null) return null;

        if (binding instanceof ITypeBinding type) {
            SourceIndex.TypeLocation location = index.type(AstUtil.qualifiedTypeName(type));
            if (location != null) return location(location);
            return find(current, binding);
        }

        Analysis target = current;
        ITypeBinding declaring = declaringType(binding);
        if (declaring != null && !(binding instanceof IVariableBinding variable && !variable.isField())) {
            SourceIndex.TypeLocation location = index.type(AstUtil.qualifiedTypeName(declaring));
            if (location != null && !location.path().equals(current.path())) {
                Analysis parsed = parser.apply(location.path());
                if (parsed != null) target = parsed;
            }
        }

        Location found = find(target, binding);
        if (found != null) return found;
        return target == current ? null : find(current, binding);
    }

    private static ITypeBinding declaringType(IBinding binding) {
        if (binding instanceof IMethodBinding method) return method.getDeclaringClass();
        if (binding instanceof IVariableBinding variable) return variable.getDeclaringClass();
        return null;
    }

    private static Location find(Analysis analysis, IBinding target) {
        if (analysis == null || target.getKey() == null) return null;
        Location[] result = new Location[1];
        CompilationUnit unit = analysis.unit();
        String text = analysis.text();

        unit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodDeclaration node) {
                check(node.getName(), node.resolveBinding());
                return result[0] == null;
            }

            @Override
            public boolean visit(VariableDeclarationFragment node) {
                check(node.getName(), node.resolveBinding());
                return result[0] == null;
            }

            @Override
            public boolean visit(SingleVariableDeclaration node) {
                check(node.getName(), node.resolveBinding());
                return result[0] == null;
            }

            @Override
            public boolean visit(EnumConstantDeclaration node) {
                check(node.getName(), node.resolveVariable());
                return result[0] == null;
            }

            @Override
            public boolean visit(AnnotationTypeMemberDeclaration node) {
                check(node.getName(), node.resolveBinding());
                return result[0] == null;
            }

            @Override
            public boolean visit(TypeParameter node) {
                check(node.getName(), node.resolveBinding());
                return result[0] == null;
            }

            private void check(SimpleName name, IBinding binding) {
                if (result[0] != null || binding == null || !target.getKey().equals(binding.getKey())) return;
                Range range = AstUtil.range(unit, name, text.length());
                result[0] = new Location(analysis.path().toUri().toString(), range);
            }
        });
        return result[0];
    }

    private static Location location(SourceIndex.TypeLocation location) {
        Range range = new Range(
                new Position(location.startLine(), location.startColumn()),
                new Position(location.endLine(), location.endColumn()));
        return new Location(location.path().toUri().toString(), range);
    }
}
