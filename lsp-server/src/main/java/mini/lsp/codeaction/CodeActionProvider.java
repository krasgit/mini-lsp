package mini.lsp.codeaction;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import mini.lsp.workspace.Analysis;
import mini.lsp.workspace.AstUtil;
import mini.lsp.workspace.SourceIndex;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CreateFile;
import org.eclipse.lsp4j.CreateFileOptions;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ResourceOperation;
import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

public final class CodeActionProvider {

    private static final Map<String, String> KNOWN_IMPORTS = knownImports();

    private CodeActionProvider() {
    }

    private static Map<String, String> knownImports() {
        Map<String, String> imports = new LinkedHashMap<>();
        imports.put("List", "java.util.List");
        imports.put("ArrayList", "java.util.ArrayList");
        imports.put("LinkedList", "java.util.LinkedList");
        imports.put("Map", "java.util.Map");
        imports.put("HashMap", "java.util.HashMap");
        imports.put("TreeMap", "java.util.TreeMap");
        imports.put("Set", "java.util.Set");
        imports.put("HashSet", "java.util.HashSet");
        imports.put("TreeSet", "java.util.TreeSet");
        imports.put("Collection", "java.util.Collection");
        imports.put("Collections", "java.util.Collections");
        imports.put("Arrays", "java.util.Arrays");
        imports.put("Objects", "java.util.Objects");
        imports.put("Optional", "java.util.Optional");
        imports.put("Iterator", "java.util.Iterator");
        imports.put("Queue", "java.util.Queue");
        imports.put("Deque", "java.util.Deque");
        imports.put("ArrayDeque", "java.util.ArrayDeque");
        imports.put("Comparator", "java.util.Comparator");
        imports.put("UUID", "java.util.UUID");
        imports.put("Random", "java.util.Random");
        imports.put("Scanner", "java.util.Scanner");
        imports.put("Stream", "java.util.stream.Stream");
        imports.put("Collectors", "java.util.stream.Collectors");
        imports.put("Function", "java.util.function.Function");
        imports.put("Supplier", "java.util.function.Supplier");
        imports.put("Consumer", "java.util.function.Consumer");
        imports.put("Predicate", "java.util.function.Predicate");
        imports.put("BiFunction", "java.util.function.BiFunction");
        imports.put("UnaryOperator", "java.util.function.UnaryOperator");
        imports.put("CompletableFuture", "java.util.concurrent.CompletableFuture");
        imports.put("ExecutorService", "java.util.concurrent.ExecutorService");
        imports.put("Executors", "java.util.concurrent.Executors");
        imports.put("TimeUnit", "java.util.concurrent.TimeUnit");
        imports.put("Pattern", "java.util.regex.Pattern");
        imports.put("Matcher", "java.util.regex.Matcher");
        imports.put("File", "java.io.File");
        imports.put("IOException", "java.io.IOException");
        imports.put("InputStream", "java.io.InputStream");
        imports.put("OutputStream", "java.io.OutputStream");
        imports.put("Reader", "java.io.Reader");
        imports.put("Writer", "java.io.Writer");
        imports.put("BufferedReader", "java.io.BufferedReader");
        imports.put("PrintWriter", "java.io.PrintWriter");
        imports.put("Path", "java.nio.file.Path");
        imports.put("Files", "java.nio.file.Files");
        imports.put("Paths", "java.nio.file.Paths");
        imports.put("LocalDate", "java.time.LocalDate");
        imports.put("LocalDateTime", "java.time.LocalDateTime");
        imports.put("LocalTime", "java.time.LocalTime");
        imports.put("Duration", "java.time.Duration");
        imports.put("Instant", "java.time.Instant");
        imports.put("Period", "java.time.Period");
        imports.put("ZonedDateTime", "java.time.ZonedDateTime");
        imports.put("DateTimeFormatter", "java.time.format.DateTimeFormatter");
        imports.put("BigDecimal", "java.math.BigDecimal");
        imports.put("BigInteger", "java.math.BigInteger");
        imports.put("URI", "java.net.URI");
        imports.put("URL", "java.net.URL");
        imports.put("InetAddress", "java.net.InetAddress");
        imports.put("SimpleDateFormat", "java.text.SimpleDateFormat");
        imports.put("DateFormat", "java.text.DateFormat");
        return Map.copyOf(imports);
    }

    public static List<CodeAction> actions(Analysis current, SourceIndex index, Range requested,
                                           List<Diagnostic> diagnostics,
                                           Function<Path, Analysis> parser) {
        CompilationUnit unit = current.unit();
        String text = current.text();
        Map<String, CodeAction> actions = new LinkedHashMap<>();

        List<Diagnostic> relevant = relevantDiagnostics(unit, text, requested, diagnostics);
        for (Diagnostic diagnostic : relevant) {
            int code = code(diagnostic);
            if (code == 0) continue;
            ASTNode node = nodeAt(unit, text, diagnostic.getRange());
            if (node == null && code != IProblem.UnusedImport) continue;
            switch (code) {
                case IProblem.UndefinedMethod -> add(actions, missingMethod(node, index, parser));
                case IProblem.UndefinedConstructor, IProblem.UndefinedConstructorInDefaultConstructor,
                     IProblem.UndefinedConstructorInImplicitConstructorCall ->
                        add(actions, missingConstructor(node, index, parser));
                case IProblem.UndefinedField -> add(actions, missingField(node, index, parser));
                case IProblem.UndefinedName, IProblem.UnresolvedVariable -> unresolvedName(
                        actions, node, code, diagnostic.getMessage(), current, index, parser);
                case IProblem.UndefinedType -> undefinedType(actions, node, current, index, parser);
                case IProblem.UnusedImport -> add(actions, removeUnusedImport(node, current));
                case IProblem.TypeMismatch, IProblem.ReturnTypeMismatch ->
                        typeMismatch(actions, node, current);
                case IProblem.LocalVariableIsNeverUsed -> add(actions, removeUnusedVariable(node, current));
                case IProblem.UnusedPrivateField -> unusedField(actions, node, current);
                default -> {
                }
            }
        }

        if (actions.isEmpty()) {
            fallbackMissingMethods(actions, unit, text, requested, index, parser);
        }
        return new ArrayList<>(actions.values());
    }

    private static List<Diagnostic> relevantDiagnostics(CompilationUnit unit, String text,
                                                         Range requested, List<Diagnostic> diagnostics) {
        List<Diagnostic> relevant = new ArrayList<>();
        if (diagnostics == null) return relevant;
        int requestedStart = AstUtil.offset(unit, requested.getStart(), text.length());
        int requestedEnd = AstUtil.offset(unit, requested.getEnd(), text.length());
        if (requestedEnd < requestedStart) requestedEnd = requestedStart;
        int requestedStartLine = requested.getStart().getLine();
        int requestedEndLine = requested.getEnd().getLine();
        for (Diagnostic diagnostic : diagnostics) {
            if (diagnostic == null || diagnostic.getRange() == null) continue;
            Range range = diagnostic.getRange();
            int diagnosticStart = AstUtil.offset(unit, range.getStart(), text.length());
            int diagnosticEnd = AstUtil.offset(unit, range.getEnd(), text.length());
            if (diagnosticEnd < diagnosticStart) diagnosticEnd = diagnosticStart;
            boolean lineOverlap = requestedStartLine <= range.getEnd().getLine()
                    && requestedEndLine >= range.getStart().getLine();
            boolean offsetOverlap = requestedStart == requestedEnd
                    ? diagnosticStart <= requestedStart && requestedStart <= diagnosticEnd
                    : requestedStart <= diagnosticEnd && requestedEnd >= diagnosticStart;
            if (lineOverlap || offsetOverlap) relevant.add(diagnostic);
        }
        return relevant;
    }

    private static int code(Diagnostic diagnostic) {
        Either<String, Integer> code = diagnostic.getCode();
        if (code == null) return 0;
        if (code.isRight() && code.getRight() != null) return code.getRight();
        if (code.isLeft() && code.getLeft() != null) {
            try {
                return Integer.parseInt(code.getLeft());
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private static ASTNode nodeAt(CompilationUnit unit, String text, Range range) {
        if (range == null) return null;
        int start = AstUtil.offset(unit, range.getStart(), text.length());
        int end = AstUtil.offset(unit, range.getEnd(), text.length());
        if (end < start) end = start;
        try {
            return NodeFinder.perform(unit, start, end - start);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static void add(Map<String, CodeAction> actions, CodeAction action) {
        if (action != null) actions.putIfAbsent(action.getTitle(), action);
    }

    private static void fallbackMissingMethods(Map<String, CodeAction> actions, CompilationUnit unit,
                                               String text, Range requested, SourceIndex index,
                                               Function<Path, Analysis> parser) {
        int start = AstUtil.offset(unit, requested.getStart(), text.length());
        int requestedEnd = AstUtil.offset(unit, requested.getEnd(), text.length());
        final int from = start;
        final int to = Math.max(start, requestedEnd);
        unit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation invocation) {
                if (invocation.resolveMethodBinding() != null) return true;
                if (from == to) {
                    int line = unit.getLineNumber(from);
                    int startLine = unit.getLineNumber(invocation.getStartPosition());
                    int endLine = unit.getLineNumber(invocation.getStartPosition() + invocation.getLength());
                    if (line < startLine || line > endLine) return true;
                } else {
                    int start = invocation.getStartPosition();
                    int end = start + invocation.getLength();
                    if (end < from || start > to) return true;
                }
                add(actions, missingMethod(invocation, index, parser));
                return true;
            }
        });
    }

    private static CodeAction missingMethod(ASTNode node, SourceIndex index,
                                            Function<Path, Analysis> parser) {
        MethodInvocation invocation = enclosing(node, MethodInvocation.class);
        if (invocation == null || invocation.resolveMethodBinding() != null) return null;

        ITypeBinding receiver = invocation.getExpression() == null
                ? AstUtil.enclosingType(invocation)
                : invocation.getExpression().resolveTypeBinding();
        if (receiver == null || receiver.isPrimitive() || receiver.isArray()) return null;

        Target target = target(receiver, index, parser);
        if (target == null) return null;

        CodeAction accessor = accessor(invocation, target, receiver);
        if (accessor != null) return accessor;

        String methodName = invocation.getName().getIdentifier();
        List<String> parameterTypes = new ArrayList<>();
        List<String> parameterNames = new ArrayList<>();
        parameterTypes(invocation.arguments(), parameterTypes, parameterNames);

        String signature = methodName + "(" + String.join(", ", parameterTypes) + ")";
        String simpleType = AstUtil.simpleName(receiver);
        String returnType = returnType(invocation);
        boolean interfaceType = declarationIsInterface(target.declaration());

        String memberIndent = indent(target.analysis().text(), target.declaration().getStartPosition()) + "  ";
        StringBuilder insert = new StringBuilder();
        insert.append(memberIndent).append(interfaceType ? "" : "public ")
                .append(returnType).append(' ').append(methodName).append('(');
        for (int i = 0; i < parameterTypes.size(); i++) {
            if (i > 0) insert.append(", ");
            insert.append(parameterTypes.get(i)).append(' ').append(parameterNames.get(i));
        }
        insert.append(')');
        if (interfaceType) {
            insert.append(";\n");
        } else {
            insert.append(" {\n")
                    .append(memberIndent).append("  // TODO Auto-generated method stub\n")
                    .append(memberIndent).append("}\n");
        }

        return insertMemberAction(
                "Create method '" + signature + "' in '" + simpleType + "'", target, insert.toString());
    }

    private static CodeAction accessor(MethodInvocation invocation, Target target, ITypeBinding receiver) {
        String methodName = invocation.getName().getIdentifier();
        String property = null;
        boolean getter = false;
        boolean setter = false;
        if (methodName.startsWith("get") && methodName.length() > 3) {
            property = decapitalize(methodName.substring(3));
            getter = true;
        } else if (methodName.startsWith("is") && methodName.length() > 2) {
            property = decapitalize(methodName.substring(2));
            getter = true;
        } else if (methodName.startsWith("set") && methodName.length() > 3) {
            property = decapitalize(methodName.substring(3));
            setter = true;
        }
        if (property == null || !validIdentifier(property)) return null;

        IVariableBinding field = findField(receiver, property);
        if (field == null) return null;

        String memberIndent = indent(target.analysis().text(), target.declaration().getStartPosition()) + "  ";
        String simpleType = AstUtil.simpleName(receiver);
        String fieldType = AstUtil.typeName(field.getType());
        boolean isStatic = java.lang.reflect.Modifier.isStatic(field.getModifiers());
        if (getter) {
            String body = memberIndent + "public " + fieldType + " " + methodName + "() {\n"
                    + memberIndent + "  return " + (isStatic ? simpleType + "." : "") + property + ";\n"
                    + memberIndent + "}\n";
            return insertMemberAction(
                    "Generate getter '" + methodName + "()' in '" + simpleType + "'", target, body);
        }
        if (setter) {
            List<String> parameterTypes = new ArrayList<>();
            List<String> parameterNames = new ArrayList<>();
            parameterTypes(invocation.arguments(), parameterTypes, parameterNames);
            String parameterType = parameterTypes.isEmpty() ? fieldType : parameterTypes.get(0);
            String body = memberIndent + "public void " + methodName + "(" + parameterType + " value) {\n"
                    + memberIndent + "  " + (isStatic ? simpleType + "." : "this.") + property + " = value;\n"
                    + memberIndent + "}\n";
            return insertMemberAction(
                    "Generate setter '" + methodName + "(...)' in '" + simpleType + "'", target, body);
        }
        return null;
    }

    private static CodeAction missingConstructor(ASTNode node, SourceIndex index,
                                                 Function<Path, Analysis> parser) {
        ClassInstanceCreation creation = enclosing(node, ClassInstanceCreation.class);
        if (creation == null) return null;
        ITypeBinding receiver = creation.getType().resolveBinding();
        if (receiver == null || receiver.isPrimitive() || receiver.isArray()) return null;

        if (receiver.isEnum() || receiver.isRecord()) return null;
        Target target = target(receiver, index, parser);
        if (target == null || declarationIsInterface(target.declaration())) return null;

        String simpleType = AstUtil.simpleName(receiver);
        List<String> parameterTypes = new ArrayList<>();
        List<String> parameterNames = new ArrayList<>();
        parameterTypes(creation.arguments(), parameterTypes, parameterNames);

        String signature = simpleType + "(" + String.join(", ", parameterTypes) + ")";
        String memberIndent = indent(target.analysis().text(), target.declaration().getStartPosition()) + "  ";
        StringBuilder insert = new StringBuilder();
        insert.append(memberIndent).append("public ").append(simpleType).append('(');
        for (int i = 0; i < parameterTypes.size(); i++) {
            if (i > 0) insert.append(", ");
            insert.append(parameterTypes.get(i)).append(' ').append(parameterNames.get(i));
        }
        insert.append(") {\n")
                .append(memberIndent).append("  // TODO Auto-generated constructor stub\n")
                .append(memberIndent).append("}\n");

        return insertMemberAction(
                "Create constructor '" + signature + "' in '" + simpleType + "'", target, insert.toString());
    }

    private static CodeAction missingField(ASTNode node, SourceIndex index,
                                           Function<Path, Analysis> parser) {
        FieldAccess access = enclosing(node, FieldAccess.class);
        SimpleName name = null;
        ITypeBinding receiver = null;
        if (access != null) {
            name = access.getName();
            receiver = access.getExpression() == null ? null : access.getExpression().resolveTypeBinding();
        }
        if (name == null) {
            QualifiedName qualified = enclosing(node, QualifiedName.class);
            if (qualified != null) {
                name = qualified.getName();
                receiver = qualified.getQualifier().resolveTypeBinding();
            }
        }
        if (name == null) name = AstUtil.nameNode(node);
        if (name == null || !validIdentifier(name.getIdentifier())) return null;
        if (receiver == null) receiver = AstUtil.enclosingType(node);
        if (receiver == null || receiver.isPrimitive() || receiver.isArray()) return null;

        Target target = target(receiver, index, parser);
        if (target == null || declarationIsInterface(target.declaration())) return null;

        ITypeBinding type = expectedType(outerExpression(name));
        String fieldType = typeName(type);
        String simpleType = AstUtil.simpleName(receiver);
        ITypeBinding enclosingType = AstUtil.enclosingType(node);
        boolean sameType = enclosingType != null
                && AstUtil.qualifiedTypeName(enclosingType).equals(AstUtil.qualifiedTypeName(receiver));
        String memberIndent = indent(target.analysis().text(), target.declaration().getStartPosition()) + "  ";
        String member = memberIndent + (sameType ? "private " : "public ") + fieldType + " "
                + name.getIdentifier() + ";\n";

        return insertMemberAction(
                "Create field '" + fieldType + " " + name.getIdentifier() + "' in '" + simpleType + "'",
                target, member);
    }

    private static void unresolvedName(Map<String, CodeAction> actions, ASTNode node, int code,
                                       String message, Analysis current, SourceIndex index,
                                       Function<Path, Analysis> parser) {
        SimpleName name = AstUtil.nameNode(node);
        if (name == null || name.getIdentifier().isBlank()) return;
        if (AstUtil.isImport(name) || AstUtil.isPackage(name) || AstUtil.isDeclarationName(name)) return;
        ASTNode parent = name.getParent();
        if (parent instanceof MethodInvocation invocation && invocation.getName() == name) return;
        if (parent instanceof QualifiedName qualified && qualified.getQualifier() == name
                && isLikelyTypeQualifier(qualified)) return;
        if (code == IProblem.UndefinedName && message != null
                && message.contains("cannot be resolved to a type")) {
            undefinedType(actions, node, current, index, parser);
            return;
        }
        if (!validIdentifier(name.getIdentifier())) return;

        ITypeBinding type = expectedType(outerExpression(name));
        String typeName = typeName(type);
        String variableName = name.getIdentifier();

        Statement statement = enclosing(name, Statement.class);
        String declaration = null;
        if (statement != null) {
            int start = statement.getStartPosition();
            if (start >= 0) {
                String indent = indent(current.text(), start);
                declaration = indent + typeName + " " + variableName + " = " + defaultValue(type) + ";\n";
                int offset = lineStart(current.text(), start);
                TextEdit edit = new TextEdit(range(current, offset, offset), declaration);
                add(actions, action("Create local variable '" + typeName + " " + variableName + "'",
                        current.path(), List.of(edit)));
            }
        } else {
            Block block = enclosing(name, Block.class);
            if (block != null) {
                int offset = block.getStartPosition() + 1;
                int lineEnd = current.text().indexOf('\n', offset);
                if (lineEnd >= 0) offset = lineEnd + 1;
                String indent = indent(current.text(), block.getStartPosition()) + "  ";
                declaration = indent + typeName + " " + variableName + " = " + defaultValue(type) + ";\n";
                TextEdit edit = new TextEdit(range(current, offset, offset), declaration);
                add(actions, action("Create local variable '" + typeName + " " + variableName + "'",
                        current.path(), List.of(edit)));
            }
        }

        ITypeBinding enclosingType = AstUtil.enclosingType(name);
        if (enclosingType != null && !enclosingType.isInterface() && !enclosingType.isAnnotation()) {
            Target target = target(enclosingType, index, parser);
            if (target != null && !declarationIsInterface(target.declaration())) {
                String memberIndent = indent(target.analysis().text(),
                        target.declaration().getStartPosition()) + "  ";
                String member = memberIndent + "private " + typeName + " " + variableName + ";\n";
                add(actions, insertMemberAction("Create field '" + typeName + " " + variableName
                        + "' in '" + AstUtil.simpleName(enclosingType) + "'", target, member));
            }
        }
    }

    private static boolean isLikelyTypeQualifier(QualifiedName qualified) {
        String next = qualified.getName().getIdentifier();
        if (next.isEmpty() || !Character.isUpperCase(next.charAt(0))) return false;
        String qualifier = qualified.getQualifier().getFullyQualifiedName();
        return qualifier.indexOf('.') < 0 && !qualifier.isEmpty()
                && Character.isLowerCase(qualifier.charAt(0));
    }

    private static void undefinedType(Map<String, CodeAction> actions, ASTNode node, Analysis current,
                                      SourceIndex index, Function<Path, Analysis> parser) {
        SimpleName name = AstUtil.nameNode(node);
        if (name == null || !validIdentifier(name.getIdentifier())) return;
        String simple = name.getIdentifier();
        String currentPackage = packageName(current.unit());
        Set<String> candidates = new LinkedHashSet<>();

        for (SourceIndex.TypeLocation location : index.typesBySimpleName(simple)) {
            Analysis target = parser.apply(location.path());
            if (target == null) continue;
            if (packageName(target.unit()).equals(currentPackage)) continue;
            candidates.add(location.qualifiedName());
        }
        String known = KNOWN_IMPORTS.get(simple);
        if (known != null) candidates.add(known);

        for (String candidate : candidates) {
            TextEdit edit = insertImport(current, candidate);
            if (edit == null) continue;
            add(actions, action("Add import '" + candidate + "'",
                    current.path(), List.of(edit)));
        }

        if (Character.isUpperCase(simple.charAt(0)) && !isKnownJdkType(simple)
                && !KNOWN_IMPORTS.containsKey(simple)) {
            Path targetPath = current.path().resolveSibling(simple + ".java");
            if (!Files.exists(targetPath)) {
                String pkg = packageName(current.unit());
                String content = (pkg.isBlank() ? "" : "package " + pkg + ";\n\n")
                        + "public class " + simple + " {\n}\n";
                String targetUri = targetPath.toUri().toString();
                CreateFile create = new CreateFile(targetUri);
                create.setKind("create");
                create.setOptions(new CreateFileOptions(false, true));
                TextDocumentEdit textEdit = new TextDocumentEdit(
                        new VersionedTextDocumentIdentifier(targetUri, (Integer) null),
                        List.of(new TextEdit(new Range(new Position(0, 0), new Position(0, 0)), content)));
                List<Either<TextDocumentEdit, ResourceOperation>> changes = new ArrayList<>();
                changes.add(Either.forRight(create));
                changes.add(Either.forLeft(textEdit));
                WorkspaceEdit workspaceEdit = new WorkspaceEdit();
                workspaceEdit.setDocumentChanges(changes);
                CodeAction action = new CodeAction(
                        "Create class '" + simple + "' in new file '" + simple + ".java'");
                action.setKind(CodeActionKind.QuickFix);
                action.setEdit(workspaceEdit);
                add(actions, action);
            }

            int offset = current.text().length();
            List<?> types = current.unit().types();
            if (!types.isEmpty()) {
                ASTNode last = (ASTNode) types.get(types.size() - 1);
                offset = last.getStartPosition() + last.getLength();
            }
            TextEdit edit = new TextEdit(range(current, offset, offset),
                    "\n\nclass " + simple + " {\n}\n");
            add(actions, action("Create class '" + simple + "' in this file",
                    current.path(), List.of(edit)));
        }
    }

    private static TextEdit insertImport(Analysis target, String qualifiedName) {
        CompilationUnit unit = target.unit();
        String text = target.text();
        int dot = qualifiedName.lastIndexOf('.');
        String simpleName = dot < 0 ? qualifiedName : qualifiedName.substring(dot + 1);
        for (Object item : unit.imports()) {
            ImportDeclaration declaration = (ImportDeclaration) item;
            String imported = declaration.getName().getFullyQualifiedName();
            if (imported.equals(qualifiedName)) return null;
            if (imported.endsWith("." + simpleName)) return null;
        }
        int offset = 0;
        PackageDeclaration packageDeclaration = unit.getPackage();
        if (packageDeclaration != null) {
            offset = packageDeclaration.getStartPosition() + packageDeclaration.getLength();
            if (text.startsWith("\r\n", offset)) offset += 2;
            else if (offset < text.length() && text.charAt(offset) == '\n') offset++;
        }
        String line = "import " + qualifiedName + ";\n";
        if (offset > 0 && offset < text.length() && !startsWithNewline(text, offset)) {
            line = "\n" + line;
        }
        return new TextEdit(range(target, offset, offset), line);
    }

    private static boolean startsWithNewline(String text, int offset) {
        return offset < text.length() && (text.charAt(offset) == '\n' || text.charAt(offset) == '\r');
    }

    private static CodeAction removeUnusedImport(ASTNode node, Analysis current) {
        ImportDeclaration declaration = enclosing(node, ImportDeclaration.class);
        if (declaration == null) {
            for (Object item : current.unit().imports()) {
                ImportDeclaration candidate = (ImportDeclaration) item;
                if (node != null && candidate.getStartPosition() <= node.getStartPosition()
                        && candidate.getStartPosition() + candidate.getLength() >= node.getStartPosition()) {
                    declaration = candidate;
                    break;
                }
            }
        }
        if (declaration == null) return null;
        TextEdit edit = deleteLineEdit(current, declaration);
        return action("Remove unused import '" + declaration.getName().getFullyQualifiedName() + "'",
                current.path(), List.of(edit));
    }

    private static void typeMismatch(Map<String, CodeAction> actions, ASTNode node, Analysis current) {
        Expression expression = expression(node);
        if (expression == null) return;
        ITypeBinding source = expression.resolveTypeBinding();
        ITypeBinding target = expectedType(expression);
        if (source == null || target == null) return;
        if (AstUtil.qualifiedTypeName(source).equals(AstUtil.qualifiedTypeName(target))) return;

        String sourceName = AstUtil.typeName(source);
        Type targetTypeNode = declaredType(expression);
        if (targetTypeNode != null && targetTypeNode.getStartPosition() >= 0) {
            TextEdit edit = new TextEdit(range(current, targetTypeNode), sourceName);
            add(actions, action("Change type to '" + sourceName + "'", current.path(), List.of(edit)));
        }

        if (source.isCastCompatible(target)) {
            String expressionText = text(current, expression);
            String targetName = AstUtil.typeName(target);
            TextEdit edit = new TextEdit(range(current, expression),
                    "(" + targetName + ") (" + expressionText + ")");
            add(actions, action("Cast to '" + targetName + "'", current.path(), List.of(edit)));
        }
    }

    private static Type declaredType(Expression expression) {
        ASTNode current = expression;
        while (current != null) {
            ASTNode parent = current.getParent();
            if (parent instanceof ParenthesizedExpression) {
                current = parent;
                continue;
            }
            if (parent instanceof VariableDeclarationFragment fragment
                    && fragment.getInitializer() == current) {
                ASTNode declaration = fragment.getParent();
                if (declaration instanceof VariableDeclarationStatement statement) {
                    return statement.getType();
                }
                if (declaration instanceof FieldDeclaration field) {
                    return field.getType();
                }
            }
            if (parent instanceof SingleVariableDeclaration declaration
                    && declaration.getInitializer() == current) {
                return declaration.getType();
            }
            if (parent instanceof ReturnStatement) {
                ASTNode method = parent;
                while (method != null && !(method instanceof MethodDeclaration)) {
                    method = method.getParent();
                }
                if (method instanceof MethodDeclaration declaration) return declaration.getReturnType();
            }
            break;
        }
        return null;
    }

    private static CodeAction removeUnusedVariable(ASTNode node, Analysis current) {
        VariableDeclarationFragment fragment = enclosing(node, VariableDeclarationFragment.class);
        if (fragment == null) return null;
        ASTNode parent = fragment.getParent();
        String name = fragment.getName().getIdentifier();
        if (parent instanceof VariableDeclarationStatement statement) {
            List<?> fragments = statement.fragments();
            if (fragments.size() == 1) {
                TextEdit edit = deleteLineEdit(current, statement);
                return action("Remove unused variable '" + name + "'", current.path(), List.of(edit));
            }
            int index = fragments.indexOf(fragment);
            int start;
            int end;
            if (index == 0) {
                start = fragment.getStartPosition();
                end = ((VariableDeclarationFragment) fragments.get(1)).getStartPosition();
            } else {
                VariableDeclarationFragment previous =
                        (VariableDeclarationFragment) fragments.get(index - 1);
                start = previous.getStartPosition() + previous.getLength();
                end = fragment.getStartPosition() + fragment.getLength();
            }
            TextEdit edit = new TextEdit(range(current, start, end), "");
            return action("Remove unused variable '" + name + "'", current.path(), List.of(edit));
        }
        if (parent instanceof SingleVariableDeclaration declaration) {
            TextEdit edit = new TextEdit(range(current, declaration), "");
            return action("Remove unused variable '" + name + "'", current.path(), List.of(edit));
        }
        return null;
    }

    private static void unusedField(Map<String, CodeAction> actions, ASTNode node, Analysis current) {
        VariableDeclarationFragment fragment = enclosing(node, VariableDeclarationFragment.class);
        if (fragment == null || !(fragment.getParent() instanceof FieldDeclaration field)) return;
        String name = fragment.getName().getIdentifier();

        int lineStart = lineStart(current.text(), field.getStartPosition());
        String indent = indent(current.text(), field.getStartPosition());
        TextEdit suppress = new TextEdit(range(current, lineStart, lineStart),
                indent + "@SuppressWarnings(\"unused\")\n");
        add(actions, action("Add @SuppressWarnings(\"unused\") to field '" + name + "'",
                current.path(), List.of(suppress)));

        IVariableBinding binding = fragment.resolveBinding();
        if (binding == null) return;

        List<TextEdit> edits = new ArrayList<>();
        edits.add(deleteLineEdit(current, field));
        boolean[] safe = {true};
        current.unit().accept(new ASTVisitor() {
            @Override
            public boolean visit(Assignment assignment) {
                if (writesToField(assignment.getLeftHandSide(), binding)) {
                    if (assignment.getParent() instanceof ExpressionStatement statement) {
                        edits.add(deleteLineEdit(current, statement));
                    } else {
                        safe[0] = false;
                    }
                }
                return true;
            }

            @Override
            public boolean visit(PrefixExpression expression) {
                if (writesToField(expression.getOperand(), binding)) {
                    if (expression.getParent() instanceof ExpressionStatement statement) {
                        edits.add(deleteLineEdit(current, statement));
                    } else {
                        safe[0] = false;
                    }
                }
                return true;
            }

            @Override
            public boolean visit(PostfixExpression expression) {
                if (writesToField(expression.getOperand(), binding)) {
                    if (expression.getParent() instanceof ExpressionStatement statement) {
                        edits.add(deleteLineEdit(current, statement));
                    } else {
                        safe[0] = false;
                    }
                }
                return true;
            }
        });
        if (safe[0] && edits.size() > 0) {
            add(actions, action("Remove unused field '" + name + "'", current.path(), edits));
        }
    }

    private static boolean writesToField(Expression expression, IVariableBinding field) {
        IBinding binding = AstUtil.binding(expression);
        if (!(binding instanceof IVariableBinding variable) || !variable.isField()) return false;
        if (!variable.getName().equals(field.getName())) return false;
        ITypeBinding left = variable.getDeclaringClass();
        ITypeBinding right = field.getDeclaringClass();
        if (left == null || right == null) return true;
        return AstUtil.qualifiedTypeName(left).equals(AstUtil.qualifiedTypeName(right));
    }

    private static CodeAction insertMemberAction(String title, Target target, String member) {
        TextEdit edit = insertMemberEdit(target, member);
        return action(title, target.analysis().path(), List.of(edit));
    }

    private static TextEdit insertMemberEdit(Target target, String member) {
        AbstractTypeDeclaration declaration = target.declaration();
        String text = target.analysis().text();
        int close = declaration.getStartPosition() + declaration.getLength() - 1;
        if (close < 0) close = text.length();
        int lineStart = text.lastIndexOf('\n', close - 1) + 1;
        boolean ownLine = lineStart <= close && text.substring(lineStart, close).isBlank();
        int offset = ownLine ? lineStart : close;
        String newText = ownLine ? member : "\n" + member;
        return new TextEdit(range(target.analysis(), offset, offset), newText);
    }

    private static Target target(ITypeBinding receiver, SourceIndex index,
                                 Function<Path, Analysis> parser) {
        if (receiver == null) return null;
        String qualifiedType = AstUtil.qualifiedTypeName(receiver);
        SourceIndex.TypeLocation location = index.type(qualifiedType);
        if (location == null) return null;
        Analysis target = parser.apply(location.path());
        if (target == null) return null;
        AbstractTypeDeclaration declaration = findType(target.unit(), qualifiedType, location);
        if (declaration == null) return null;
        return new Target(target, declaration);
    }

    private static void parameterTypes(List<?> arguments, List<String> types, List<String> names) {
        Set<String> used = new LinkedHashSet<>();
        for (int i = 0; i < arguments.size(); i++) {
            Expression argument = (Expression) arguments.get(i);
            ITypeBinding type = argument.resolveTypeBinding();
            types.add(typeName(type));

            String name = argument instanceof SimpleName simple ? simple.getIdentifier() : "arg" + i;
            if (!validIdentifier(name) || !used.add(name)) {
                name = "arg" + i;
                while (!used.add(name)) name += "_";
            }
            names.add(name);
        }
    }

    private static String returnType(MethodInvocation invocation) {
        ASTNode node = invocation;
        ASTNode parent = node.getParent();
        while (parent instanceof ParenthesizedExpression) {
            node = parent;
            parent = parent.getParent();
        }
        if (parent instanceof ExpressionStatement) return "void";
        if (parent instanceof VariableDeclarationFragment fragment
                && fragment.getParent() instanceof VariableDeclarationStatement statement) {
            return statement.getType().toString();
        }
        if (parent instanceof Assignment assignment) {
            ITypeBinding type = assignment.getLeftHandSide().resolveTypeBinding();
            return typeName(type);
        }
        if (parent instanceof ReturnStatement) {
            ASTNode current = parent;
            while (current != null && !(current instanceof MethodDeclaration)) current = current.getParent();
            if (current instanceof MethodDeclaration method && method.getReturnType() != null) {
                return method.getReturnType().toString();
            }
        }
        if (parent instanceof MethodInvocation outer) {
            IMethodBinding method = outer.resolveMethodBinding();
            if (method != null) {
                int index = outer.arguments().indexOf(node);
                ITypeBinding[] parameters = method.getParameterTypes();
                if (index >= 0 && index < parameters.length) return typeName(parameters[index]);
            }
        }
        if (parent instanceof ClassInstanceCreation outer) {
            IMethodBinding constructor = outer.resolveConstructorBinding();
            if (constructor != null) {
                int index = outer.arguments().indexOf(node);
                ITypeBinding[] parameters = constructor.getParameterTypes();
                if (index >= 0 && index < parameters.length) return typeName(parameters[index]);
            }
        }
        if (parent instanceof CastExpression cast) {
            return cast.getType().toString();
        }
        return "void";
    }

    private static ITypeBinding expectedType(ASTNode node) {
        ASTNode current = node;
        while (current != null) {
            ASTNode parent = current.getParent();
            if (parent instanceof ParenthesizedExpression) {
                current = parent;
                continue;
            }
            if (parent instanceof Assignment assignment) {
                if (assignment.getRightHandSide() == current) {
                    return assignment.getLeftHandSide().resolveTypeBinding();
                }
                if (assignment.getLeftHandSide() == current) {
                    return assignment.getRightHandSide().resolveTypeBinding();
                }
            }
            if (parent instanceof VariableDeclarationFragment fragment
                    && fragment.getInitializer() == current) {
                ASTNode declaration = fragment.getParent();
                if (declaration instanceof VariableDeclarationStatement statement) {
                    return statement.getType().resolveBinding();
                }
                if (declaration instanceof FieldDeclaration field) {
                    return field.getType().resolveBinding();
                }
            }
            if (parent instanceof SingleVariableDeclaration declaration
                    && declaration.getInitializer() == current) {
                return declaration.getType().resolveBinding();
            }
            if (parent instanceof ReturnStatement) {
                ASTNode method = parent;
                while (method != null && !(method instanceof MethodDeclaration)) {
                    method = method.getParent();
                }
                if (method instanceof MethodDeclaration declaration) {
                    return declaration.getReturnType() == null
                            ? null : declaration.getReturnType().resolveBinding();
                }
            }
            if (parent instanceof CastExpression cast) {
                return cast.getType().resolveBinding();
            }
            if (parent instanceof MethodInvocation invocation) {
                IMethodBinding method = invocation.resolveMethodBinding();
                if (method != null) {
                    List<?> arguments = invocation.arguments();
                    int index = arguments.indexOf(current);
                    ITypeBinding[] parameters = method.getParameterTypes();
                    if (index >= 0 && index < parameters.length) return parameters[index];
                }
            }
            if (parent instanceof ClassInstanceCreation creation) {
                IMethodBinding constructor = creation.resolveConstructorBinding();
                if (constructor != null) {
                    List<?> arguments = creation.arguments();
                    int index = arguments.indexOf(current);
                    ITypeBinding[] parameters = constructor.getParameterTypes();
                    if (index >= 0 && index < parameters.length) return parameters[index];
                }
            }
            break;
        }
        return null;
    }

    private static ASTNode outerExpression(ASTNode node) {
        ASTNode current = node;
        while (current != null) {
            ASTNode parent = current.getParent();
            if (parent instanceof FieldAccess access
                    && (access.getName() == current || access.getExpression() == current)) {
                current = parent;
                continue;
            }
            if (parent instanceof QualifiedName qualified
                    && (qualified.getName() == current || qualified.getQualifier() == current)) {
                current = parent;
                continue;
            }
            if (parent instanceof ParenthesizedExpression) {
                current = parent;
                continue;
            }
            break;
        }
        return current;
    }

    private static Expression expression(ASTNode node) {
        ASTNode current = node;
        while (current != null && !(current instanceof Expression)) current = current.getParent();
        return current instanceof Expression expression ? expression : null;
    }

    private static IVariableBinding findField(ITypeBinding type, String name) {
        Set<String> seen = new LinkedHashSet<>();
        ITypeBinding current = type;
        while (current != null && seen.add(AstUtil.qualifiedTypeName(current))) {
            for (IVariableBinding field : current.getDeclaredFields()) {
                if (field.getName().equals(name)) return field;
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static AbstractTypeDeclaration findType(CompilationUnit unit, String qualifiedName,
                                                    SourceIndex.TypeLocation location) {
        AbstractTypeDeclaration[] found = new AbstractTypeDeclaration[1];
        for (Object item : unit.types()) {
            if (!(item instanceof AbstractTypeDeclaration declaration)) continue;
            findType(declaration, qualifiedName, location, found);
            if (found[0] != null) break;
        }
        return found[0];
    }

    private static void findType(AbstractTypeDeclaration declaration, String qualifiedName,
                                 SourceIndex.TypeLocation location, AbstractTypeDeclaration[] found) {
        if (found[0] != null) return;
        String name = declaration.getName().getIdentifier();
        String simple = location.qualifiedName();
        int dot = simple.lastIndexOf('.');
        if (dot >= 0) simple = simple.substring(dot + 1);
        String qualified = AstUtil.qualifiedTypeName(declaration.resolveBinding());
        int start = declaration.getStartPosition();
        int end = start + declaration.getLength();
        if (qualifiedName.equals(qualified)
                || (name.equals(simple) && start <= location.start() && end >= location.end())) {
            found[0] = declaration;
            return;
        }
        for (Object item : declaration.bodyDeclarations()) {
            if (item instanceof AbstractTypeDeclaration child) {
                findType(child, qualifiedName, location, found);
                if (found[0] != null) return;
            }
        }
    }

    private static CodeAction action(String title, Path path, List<TextEdit> edits) {
        CodeAction action = new CodeAction(title);
        action.setKind(CodeActionKind.QuickFix);
        action.setEdit(new WorkspaceEdit(Map.of(path.toUri().toString(), edits)));
        return action;
    }

    private static TextEdit deleteLineEdit(Analysis analysis, ASTNode node) {
        String text = analysis.text();
        int start = node.getStartPosition();
        int end = start + node.getLength();
        if (start < 0) return new TextEdit(range(analysis, 0, 0), "");
        int lineStart = lineStart(text, start);
        int lineEnd = text.indexOf('\n', end);
        if (lineEnd < 0) lineEnd = text.length();
        else lineEnd += 1;
        boolean before = text.substring(lineStart, start).isBlank();
        boolean after = end <= text.length() && text.substring(end, Math.min(lineEnd, text.length())).isBlank();
        if (before && after) return new TextEdit(range(analysis, lineStart, lineEnd), "");
        return new TextEdit(range(analysis, start, end), "");
    }

    private static int lineStart(String text, int offset) {
        return text.lastIndexOf('\n', Math.max(0, offset - 1)) + 1;
    }

    private static Range range(Analysis analysis, ASTNode node) {
        return range(analysis, node.getStartPosition(), node.getStartPosition() + node.getLength());
    }

    private static Range range(Analysis analysis, int start, int end) {
        int length = analysis.text().length();
        int from = Math.max(0, Math.min(start, length));
        int to = Math.max(from, Math.min(end, length));
        return new Range(AstUtil.position(analysis.unit(), from), AstUtil.position(analysis.unit(), to));
    }

    private static String text(Analysis analysis, ASTNode node) {
        int start = Math.max(0, node.getStartPosition());
        int end = Math.min(analysis.text().length(), start + node.getLength());
        if (end < start) return "";
        return analysis.text().substring(start, end);
    }

    private static String indent(String text, int offset) {
        if (offset < 0 || offset > text.length()) return "";
        int lineStart = text.lastIndexOf('\n', offset - 1) + 1;
        int end = lineStart;
        while (end < offset && (text.charAt(end) == ' ' || text.charAt(end) == '\t')) end++;
        return text.substring(lineStart, end);
    }

    private static String packageName(CompilationUnit unit) {
        return unit.getPackage() == null ? "" : unit.getPackage().getName().getFullyQualifiedName();
    }

    private static boolean declarationIsInterface(AbstractTypeDeclaration declaration) {
        return declaration instanceof AnnotationTypeDeclaration
                || (declaration.resolveBinding() != null && declaration.resolveBinding().isInterface());
    }

    private static String typeName(ITypeBinding type) {
        return type == null ? "Object" : AstUtil.typeName(type);
    }

    private static String defaultValue(ITypeBinding type) {
        if (type == null) return "null";
        if (type.isPrimitive()) {
            String name = type.getName();
            if ("boolean".equals(name)) return "false";
            if ("char".equals(name)) return "'\\0'";
            if ("byte".equals(name) || "short".equals(name) || "int".equals(name) || "long".equals(name)) {
                return "0";
            }
            if ("float".equals(name)) return "0.0f";
            if ("double".equals(name)) return "0.0";
            return "0";
        }
        return "null";
    }

    private static boolean isKnownJdkType(String name) {
        return Set.of("String", "Object", "Integer", "Long", "Double", "Float", "Boolean", "Character",
                "Byte", "Short", "Math", "System", "Exception", "RuntimeException", "Error",
                "StringBuilder", "Thread", "Class", "Void", "Iterable", "Comparable", "Number",
                "Override", "Deprecated", "SuppressWarnings", "FunctionalInterface").contains(name);
    }

    private static String decapitalize(String name) {
        if (name == null || name.isEmpty()) return name;
        if (name.length() > 1 && Character.isUpperCase(name.charAt(0))
                && Character.isUpperCase(name.charAt(1))) {
            return name;
        }
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    private static boolean validIdentifier(String name) {
        if (name == null || name.isBlank()) return false;
        if (!Character.isJavaIdentifierStart(name.charAt(0))) return false;
        for (int i = 1; i < name.length(); i++) {
            if (!Character.isJavaIdentifierPart(name.charAt(i))) return false;
        }
        return !javax.lang.model.SourceVersion.isKeyword(name);
    }

    private static <T extends ASTNode> T enclosing(ASTNode node, Class<T> type) {
        ASTNode current = node;
        while (current != null) {
            if (type.isInstance(current)) return type.cast(current);
            current = current.getParent();
        }
        return null;
    }

    private record Target(Analysis analysis, AbstractTypeDeclaration declaration) {
    }
}
