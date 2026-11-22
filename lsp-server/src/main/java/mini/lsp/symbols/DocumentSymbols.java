package mini.lsp.symbols;

import java.util.ArrayList;
import java.util.List;

import mini.lsp.workspace.Analysis;
import mini.lsp.workspace.AstUtil;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.AnnotationTypeMemberDeclaration;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnumConstantDeclaration;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.RecordDeclaration;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;

public final class DocumentSymbols {

    private DocumentSymbols() {
    }

    public static List<DocumentSymbol> symbols(Analysis analysis) {
        CompilationUnit unit = analysis.unit();
        List<DocumentSymbol> out = new ArrayList<>();
        for (Object item : unit.types()) {
            if (item instanceof AbstractTypeDeclaration declaration) {
                out.add(type(unit, analysis.text().length(), declaration));
            }
        }
        return out;
    }

    private static DocumentSymbol type(CompilationUnit unit, int textLength,
                                       AbstractTypeDeclaration declaration) {
        List<DocumentSymbol> children = new ArrayList<>();
        for (Object item : declaration.bodyDeclarations()) {
            if (!(item instanceof BodyDeclaration body)) continue;
            if (body instanceof AbstractTypeDeclaration nested) {
                children.add(type(unit, textLength, nested));
            } else if (body instanceof FieldDeclaration field) {
                for (Object fragmentItem : field.fragments()) {
                    if (fragmentItem instanceof VariableDeclarationFragment fragment) {
                        children.add(variable(unit, textLength, fragment));
                    }
                }
            } else if (body instanceof MethodDeclaration method) {
                children.add(symbol(unit, textLength, method.getName(), method.resolveBinding(),
                        method.isConstructor() ? SymbolKind.Constructor : SymbolKind.Method,
                        AstUtil.signature(method.resolveBinding()), method));
            } else if (body instanceof AnnotationTypeMemberDeclaration member) {
                children.add(symbol(unit, textLength, member.getName(), member.resolveBinding(),
                        SymbolKind.Method, AstUtil.signature(member.resolveBinding()), member));
            } else if (body instanceof EnumConstantDeclaration constant) {
                children.add(symbol(unit, textLength, constant.getName(), constant.resolveVariable(),
                        SymbolKind.EnumMember, AstUtil.signature(constant.resolveVariable()), constant));
            }
        }

        return symbol(unit, textLength, declaration.getName(), declaration.resolveBinding(),
                kind(declaration), AstUtil.signature(declaration.resolveBinding()), declaration, children);
    }

    private static DocumentSymbol variable(CompilationUnit unit, int textLength,
                                           VariableDeclarationFragment fragment) {
        IBinding binding = fragment.resolveBinding();
        return symbol(unit, textLength, fragment.getName(), binding,
                SymbolKind.Field, AstUtil.signature(binding), fragment);
    }

    private static DocumentSymbol symbol(CompilationUnit unit, int textLength, SimpleName name,
                                         IBinding binding, SymbolKind kind, String detail,
                                         org.eclipse.jdt.core.dom.ASTNode node) {
        return symbol(unit, textLength, name, binding, kind, detail, node, new ArrayList<>());
    }

    private static DocumentSymbol symbol(CompilationUnit unit, int textLength, SimpleName name,
                                         IBinding binding, SymbolKind kind, String detail,
                                         org.eclipse.jdt.core.dom.ASTNode node,
                                         List<DocumentSymbol> children) {
        Range range = AstUtil.range(unit, node, textLength);
        Range selection = AstUtil.range(unit, name, textLength);
        String label = binding == null ? name.getIdentifier() : binding.getName();
        if (label == null || label.isBlank()) label = name.getIdentifier();
        DocumentSymbol symbol = new DocumentSymbol(label, kind, range, selection);
        if (detail != null && !detail.isBlank()) symbol.setDetail(detail);
        symbol.setChildren(children);
        return symbol;
    }

    private static SymbolKind kind(AbstractTypeDeclaration declaration) {
        if (declaration instanceof AnnotationTypeDeclaration) return SymbolKind.Interface;
        if (declaration instanceof EnumDeclaration) return SymbolKind.Enum;
        if (declaration instanceof RecordDeclaration) return SymbolKind.Class;
        if (declaration instanceof TypeDeclaration type && type.isInterface()) return SymbolKind.Interface;
        return SymbolKind.Class;
    }
}
