package mini.lsp.workspace;

import java.nio.file.Path;

import org.eclipse.jdt.core.dom.CompilationUnit;

public record Analysis(Path path, String text, CompilationUnit unit) {
}
