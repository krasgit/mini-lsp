package mini.lsp.hover;

import mini.lsp.workspace.Analysis;
import mini.lsp.workspace.AstUtil;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

public final class HoverProvider {

    private HoverProvider() {
    }

    public static Hover hover(Analysis analysis, Position position) {
        ASTNode node = AstUtil.nodeAt(analysis.unit(), position, analysis.text().length());
        IBinding binding = AstUtil.binding(node);
        if (binding == null) return null;

        String signature = AstUtil.signature(binding);
        if (signature == null || signature.isBlank()) return null;

        Range range = AstUtil.range(analysis.unit(), node, analysis.text().length());
        return new Hover(new MarkupContent("markdown", "```java\n" + signature + "\n```"), range);
    }
}
