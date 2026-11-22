package mini.lsp.diagnostics;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import mini.compiler.JdtEnvironment;
import mini.lsp.workspace.AstUtil;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Range;

public final class JdtDiagnostics {

    private JdtDiagnostics() {
    }

    public static List<Diagnostic> analyze(Path corelib, List<Path> sourcepath,
                                           List<Path> classpath, Path path, String text) {
        ASTParser parser = JdtEnvironment.parser(corelib, sourcepath, classpath, path.toString());
        parser.setSource(text.toCharArray());
        CompilationUnit unit = (CompilationUnit) parser.createAST(null);
        return analyze(unit, text);
    }

    public static List<Diagnostic> analyze(CompilationUnit unit, String text) {
        List<Diagnostic> out = new ArrayList<>();
        for (IProblem problem : unit.getProblems()) {
            int start = clamp(problem.getSourceStart(), text.length());
            int end = clamp(problem.getSourceEnd() + 1, text.length());
            if (end < start) end = start;

            Range range = new Range(AstUtil.position(unit, start), AstUtil.position(unit, end));
            Diagnostic d = new Diagnostic(range, problem.getMessage(), severity(problem), "mini-jdt");
            d.setCode(Integer.toString(problem.getID()));
            out.add(d);
        }
        return out;
    }

    private static DiagnosticSeverity severity(IProblem problem) {
        if (problem.isError()) return DiagnosticSeverity.Error;
        if (problem.isWarning()) return DiagnosticSeverity.Warning;
        return DiagnosticSeverity.Information;
    }

    private static int clamp(int offset, int length) {
        if (offset < 0) return 0;
        return Math.min(offset, length);
    }
}
