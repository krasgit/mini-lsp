package mini.lsp.completion;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import mini.lsp.workspace.Analysis;
import mini.lsp.workspace.AstUtil;
import mini.lsp.workspace.SourceIndex;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.Comment;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.Position;

public final class CompletionProvider {

    private static final List<String> KEYWORDS = List.of(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
            "class", "const", "continue", "default", "do", "double", "else", "enum",
            "extends", "final", "finally", "float", "for", "goto", "if", "implements",
            "import", "instanceof", "int", "interface", "long", "native", "new",
            "package", "private", "protected", "public", "record", "return", "short",
            "static", "strictfp", "super", "switch", "synchronized", "this", "throw",
            "throws", "transient", "try", "var", "void", "volatile", "while",
            "null", "true", "false");

    private CompletionProvider() {
    }

    public static List<CompletionItem> complete(Analysis analysis, SourceIndex index, Position position) {
        CompilationUnit unit = analysis.unit();
        String text = analysis.text();
        int offset = AstUtil.offset(unit, position, text.length());
        ASTNode node = AstUtil.nodeAt(unit, offset);
        if (node instanceof Comment || node instanceof StringLiteral) return List.of();

        String prefix = prefix(text, offset);
        String before = text.substring(0, offset);
        int dot = before.lastIndexOf('.');
        if (dot >= 0) {
            ITypeBinding receiver = receiverType(unit, text, dot);
            if (receiver != null) return members(receiver, prefix);
        }
        return general(analysis, index, offset, prefix);
    }

    private static List<CompletionItem> members(ITypeBinding receiver, String prefix) {
        List<CompletionItem> items = new ArrayList<>();
        Set<String> visited = new LinkedHashSet<>();
        Set<String> seen = new LinkedHashSet<>();
        collectMembers(receiver, receiver, prefix, visited, seen, items);
        return items;
    }

    private static void collectMembers(ITypeBinding root, ITypeBinding type, String prefix,
                                       Set<String> visited, Set<String> seen, List<CompletionItem> items) {
        if (type == null) return;
        String qualified = AstUtil.qualifiedTypeName(type);
        if (!visited.add(qualified)) return;

        for (IMethodBinding method : type.getDeclaredMethods()) {
            if (method.isConstructor()) continue;
            if (type != root && Modifier.isPrivate(method.getModifiers())) continue;
            if (!startsWith(method.getName(), prefix)) continue;

            StringBuilder label = new StringBuilder(method.getName()).append('(');
            ITypeBinding[] parameters = method.getParameterTypes();
            for (int i = 0; i < parameters.length; i++) {
                if (i > 0) label.append(", ");
                label.append(AstUtil.typeName(parameters[i]));
            }
            label.append(')');

            String key = "m:" + label;
            if (!seen.add(key)) continue;
            CompletionItem item = new CompletionItem(label.toString());
            item.setKind(CompletionItemKind.Method);
            item.setDetail(AstUtil.typeName(method.getReturnType()) + " - "
                    + AstUtil.qualifiedTypeName(method.getDeclaringClass()));
            item.setInsertText(parameters.length == 0 ? method.getName() + "()" : method.getName() + "(");
            item.setSortText("0" + method.getName());
            items.add(item);
        }

        for (IVariableBinding field : type.getDeclaredFields()) {
            if (!startsWith(field.getName(), prefix)) continue;
            String key = "f:" + field.getName();
            if (!seen.add(key)) continue;
            CompletionItem item = new CompletionItem(field.getName());
            item.setKind(Modifier.isFinal(field.getModifiers()) && Modifier.isStatic(field.getModifiers())
                    ? CompletionItemKind.Constant : CompletionItemKind.Field);
            item.setDetail(AstUtil.typeName(field.getType()));
            item.setInsertText(field.getName());
            item.setSortText("1" + field.getName());
            items.add(item);
        }

        collectMembers(root, type.getSuperclass(), prefix, visited, seen, items);
        for (ITypeBinding iface : type.getInterfaces()) {
            collectMembers(root, iface, prefix, visited, seen, items);
        }
    }

    private static List<CompletionItem> general(Analysis analysis, SourceIndex index,
                                                int offset, String prefix) {
        List<CompletionItem> items = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        for (String keyword : KEYWORDS) {
            if (startsWith(keyword, prefix) && seen.add("k:" + keyword)) {
                CompletionItem item = new CompletionItem(keyword);
                item.setKind(CompletionItemKind.Keyword);
                item.setSortText("3" + keyword);
                items.add(item);
            }
        }

        for (String type : index.simpleTypeNames()) {
            if (startsWith(type, prefix) && seen.add("t:" + type)) {
                CompletionItem item = new CompletionItem(type);
                item.setKind(CompletionItemKind.Class);
                item.setSortText("2" + type);
                items.add(item);
            }
        }

        CompilationUnit unit = analysis.unit();
        ASTNode node = AstUtil.nodeAt(unit, offset);
        ITypeBinding enclosing = AstUtil.enclosingType(node);
        if (enclosing != null) {
            Set<String> visited = new LinkedHashSet<>();
            List<CompletionItem> memberItems = new ArrayList<>();
            collectMembers(enclosing, enclosing, prefix, visited, seen, memberItems);
            items.addAll(memberItems);
        }

        ASTNode scope = executableScope(node);
        if (scope != null) {
            scope.accept(new ASTVisitor() {
                @Override
                public boolean visit(SingleVariableDeclaration declaration) {
                    addVariable(declaration.getName().getIdentifier(), declaration.resolveBinding());
                    return true;
                }

                @Override
                public boolean visit(VariableDeclarationFragment fragment) {
                    addVariable(fragment.getName().getIdentifier(), fragment.resolveBinding());
                    return true;
                }

                private void addVariable(String name, IBinding binding) {
                    if (name == null || !startsWith(name, prefix) || !seen.add("v:" + name)) return;
                    CompletionItem item = new CompletionItem(name);
                    item.setKind(CompletionItemKind.Variable);
                    if (binding instanceof IVariableBinding variable) {
                        item.setDetail(AstUtil.typeName(variable.getType()));
                    }
                    item.setSortText("1" + name);
                    items.add(item);
                }
            });
        }
        return items;
    }

    private static ASTNode executableScope(ASTNode node) {
        ASTNode current = node;
        while (current != null) {
            String type = current.getClass().getSimpleName();
            if (type.equals("MethodDeclaration") || type.equals("ConstructorDeclaration")
                    || type.equals("Initializer")) {
                return current;
            }
            if (current instanceof AbstractTypeDeclaration) return null;
            current = current.getParent();
        }
        return null;
    }

    private static ITypeBinding receiverType(CompilationUnit unit, String text, int dot) {
        int probe = dot - 1;
        while (probe >= 0 && Character.isWhitespace(text.charAt(probe))) probe--;
        if (probe < 0) return null;

        ASTNode node = AstUtil.nodeAt(unit, probe);
        IBinding binding = AstUtil.binding(node);
        if (binding instanceof IVariableBinding variable) return variable.getType();
        if (binding instanceof IMethodBinding method) return method.getReturnType();
        if (binding instanceof ITypeBinding type) return type;
        if (node instanceof Expression expression) return expression.resolveTypeBinding();
        return null;
    }

    private static String prefix(String text, int offset) {
        int start = offset;
        while (start > 0 && Character.isJavaIdentifierPart(text.charAt(start - 1))) start--;
        return text.substring(start, offset);
    }

    private static boolean startsWith(String value, String prefix) {
        return prefix == null || prefix.isEmpty() || value.startsWith(prefix);
    }
}
