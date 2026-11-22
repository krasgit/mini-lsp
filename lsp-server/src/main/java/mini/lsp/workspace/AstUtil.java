package mini.lsp.workspace;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.AnnotationTypeMemberDeclaration;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.EnumConstantDeclaration;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
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
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeParameter;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

public final class AstUtil {

    private AstUtil() {
    }

    public static ASTNode nodeAt(CompilationUnit unit, Position position, int textLength) {
        int offset = offset(unit, position, textLength);
        return NodeFinder.perform(unit, offset, 0);
    }

    public static ASTNode nodeAt(CompilationUnit unit, int offset) {
        return NodeFinder.perform(unit, Math.max(0, offset), 0);
    }

    public static int offset(CompilationUnit unit, Position position, int textLength) {
        int line = position.getLine() + 1;
        int column = position.getCharacter();
        int offset = unit.getPosition(line, column);
        if (offset < 0) {
            int lineStart = unit.getPosition(line, 0);
            offset = lineStart < 0 ? textLength : lineStart + column;
        }
        if (offset < 0) offset = 0;
        return Math.min(offset, textLength);
    }

    public static Position position(CompilationUnit unit, int offset) {
        int line = Math.max(1, unit.getLineNumber(offset));
        int column = Math.max(0, unit.getColumnNumber(offset));
        return new Position(line - 1, column);
    }

    public static Range range(CompilationUnit unit, int start, int length, int textLength) {
        int from = clamp(start, textLength);
        int to = clamp(start + length, textLength);
        if (to < from) to = from;
        return new Range(position(unit, from), position(unit, to));
    }

    public static Range range(CompilationUnit unit, ASTNode node, int textLength) {
        return range(unit, node.getStartPosition(), node.getLength(), textLength);
    }

    public static IBinding binding(ASTNode node) {
        ASTNode current = node;
        for (int depth = 0; current != null && depth < 4; depth++, current = current.getParent()) {
            IBinding binding = directBinding(current);
            if (binding != null) return binding;
        }
        return null;
    }

    private static IBinding directBinding(ASTNode node) {
        if (node instanceof Name name) return name.resolveBinding();
        if (node instanceof MethodInvocation invocation) return invocation.resolveMethodBinding();
        if (node instanceof SuperMethodInvocation invocation) return invocation.resolveMethodBinding();
        if (node instanceof FieldAccess access) return access.resolveFieldBinding();
        if (node instanceof SuperFieldAccess access) return access.resolveFieldBinding();
        if (node instanceof ClassInstanceCreation creation) return creation.resolveConstructorBinding();
        if (node instanceof ConstructorInvocation invocation) return invocation.resolveConstructorBinding();
        if (node instanceof SuperConstructorInvocation invocation) return invocation.resolveConstructorBinding();
        if (node instanceof Type type) return type.resolveBinding();
        if (node instanceof Annotation annotation) return annotation.resolveTypeBinding();
        if (node instanceof Expression expression) return expression.resolveTypeBinding();
        return null;
    }

    public static ITypeBinding enclosingType(ASTNode node) {
        ASTNode current = node;
        while (current != null) {
            if (current instanceof AbstractTypeDeclaration declaration) {
                return declaration.resolveBinding();
            }
            current = current.getParent();
        }
        return null;
    }

    public static boolean isDeclarationName(SimpleName name) {
        ASTNode parent = name.getParent();
        if (parent instanceof MethodDeclaration declaration) return declaration.getName() == name;
        if (parent instanceof VariableDeclarationFragment fragment) return fragment.getName() == name;
        if (parent instanceof SingleVariableDeclaration declaration) return declaration.getName() == name;
        if (parent instanceof AbstractTypeDeclaration declaration) return declaration.getName() == name;
        if (parent instanceof EnumConstantDeclaration declaration) return declaration.getName() == name;
        if (parent instanceof AnnotationTypeMemberDeclaration declaration) return declaration.getName() == name;
        if (parent instanceof TypeParameter parameter) return parameter.getName() == name;
        return false;
    }

    public static boolean isImport(SimpleName name) {
        return hasAncestor(name, ImportDeclaration.class);
    }

    public static boolean isPackage(SimpleName name) {
        return hasAncestor(name, PackageDeclaration.class);
    }

    private static boolean hasAncestor(ASTNode node, Class<? extends ASTNode> type) {
        ASTNode current = node;
        while (current != null) {
            if (type.isInstance(current)) return true;
            current = current.getParent();
        }
        return false;
    }

    public static SimpleName nameNode(ASTNode node) {
        if (node instanceof SimpleName name) return name;
        if (node instanceof QualifiedName name) return name.getName();
        if (node instanceof MethodInvocation invocation) return invocation.getName();
        if (node instanceof SuperMethodInvocation invocation) return invocation.getName();
        if (node instanceof FieldAccess access) return access.getName();
        if (node instanceof SuperFieldAccess access) return access.getName();
        return null;
    }

    public static String typeName(ITypeBinding type) {
        if (type == null) return "?";
        if (type.isArray()) return typeName(type.getComponentType()) + "[]";
        if (type.isPrimitive() || type.isTypeVariable() || type.isWildcardType()) return type.getName();
        String name = type.getName();
        if (name == null || name.isBlank()) name = type.getQualifiedName();
        return name == null || name.isBlank() ? "?" : name;
    }

    public static String qualifiedTypeName(ITypeBinding type) {
        if (type == null) return "?";
        if (type.isArray()) return qualifiedTypeName(type.getComponentType()) + "[]";
        if (type.isPrimitive() || type.isTypeVariable() || type.isWildcardType()) return type.getName();
        String name = type.getErasure().getQualifiedName();
        if (name == null || name.isBlank()) name = type.getErasure().getName();
        return name;
    }

    public static String signature(IBinding binding) {
        if (binding instanceof ITypeBinding type) return typeSignature(type);
        if (binding instanceof IMethodBinding method) return methodSignature(method);
        if (binding instanceof IVariableBinding variable) return variableSignature(variable);
        return binding == null ? "" : binding.getName();
    }

    private static String typeSignature(ITypeBinding type) {
        StringBuilder out = new StringBuilder();
        String modifiers = java.lang.reflect.Modifier.toString(type.getModifiers());
        if (!modifiers.isBlank()) out.append(modifiers).append(' ');
        if (type.isAnnotation()) out.append("@interface ");
        else if (type.isEnum()) out.append("enum ");
        else if (type.isRecord()) out.append("record ");
        else if (type.isInterface()) out.append("interface ");
        else out.append("class ");
        out.append(qualifiedTypeName(type));
        ITypeBinding superclass = type.getSuperclass();
        if (superclass != null && !"java.lang.Object".equals(qualifiedTypeName(superclass))) {
            out.append(" extends ").append(typeName(superclass));
        }
        ITypeBinding[] interfaces = type.getInterfaces();
        if (interfaces.length > 0) {
            out.append(" implements ");
            for (int i = 0; i < interfaces.length; i++) {
                if (i > 0) out.append(", ");
                out.append(typeName(interfaces[i]));
            }
        }
        return out.toString();
    }

    private static String methodSignature(IMethodBinding method) {
        StringBuilder out = new StringBuilder();
        String modifiers = java.lang.reflect.Modifier.toString(method.getModifiers());
        if (!modifiers.isBlank()) out.append(modifiers).append(' ');
        if (method.isConstructor()) {
            out.append(simpleName(method.getDeclaringClass()));
        } else {
            out.append(typeName(method.getReturnType())).append(' ').append(method.getName());
        }
        out.append('(');
        ITypeBinding[] parameters = method.getParameterTypes();
        for (int i = 0; i < parameters.length; i++) {
            if (i > 0) out.append(", ");
            out.append(typeName(parameters[i]));
        }
        out.append(')');
        ITypeBinding[] exceptions = method.getExceptionTypes();
        if (exceptions.length > 0) {
            out.append(" throws ");
            for (int i = 0; i < exceptions.length; i++) {
                if (i > 0) out.append(", ");
                out.append(typeName(exceptions[i]));
            }
        }
        return out.toString();
    }

    private static String variableSignature(IVariableBinding variable) {
        StringBuilder out = new StringBuilder();
        String modifiers = java.lang.reflect.Modifier.toString(variable.getModifiers());
        if (!modifiers.isBlank()) out.append(modifiers).append(' ');
        out.append(typeName(variable.getType())).append(' ').append(variable.getName());
        if (variable.isField() && variable.getDeclaringClass() != null) {
            out.append(" - ").append(qualifiedTypeName(variable.getDeclaringClass()));
        }
        return out.toString();
    }

    public static String simpleName(ITypeBinding type) {
        if (type == null) return "?";
        String name = type.getErasure().getName();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(dot + 1);
    }

    private static int clamp(int offset, int length) {
        if (offset < 0) return 0;
        return Math.min(offset, length);
    }
}
