package net.jangaroo.ide.idea;

import com.intellij.openapi.compiler.*;
import com.intellij.openapi.compiler.Compiler;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import net.jangaroo.exml.api.Exmlc;
import net.jangaroo.jooc.api.Jooc;
import org.jetbrains.annotations.NotNull;

/**
 * Since IDEA 13, compilation is done by "external build" (JPS), so we just use the
 * compiler manager to register compilable file extensions for
 * <ul>
 *   <li>as: "jooc"</li>
 *   <li>exml: "exmlc"</li>
 *   <li>properties: "propc"</li>
 * </ul>
 */
public class JoocCompilerFactory implements CompilerFactory {

  public Compiler[] createCompilers(@NotNull CompilerManager compilerManager) {
    addCompilableFileType(compilerManager, Jooc.AS_SUFFIX_NO_DOT);
    addCompilableFileType(compilerManager, Exmlc.EXML_SUFFIX.substring(1));
    addCompilableFileType(compilerManager, "properties");
    return new Compiler[0]; // already registered the compilers ourselves
  }

  private static void addCompilableFileType(CompilerManager compilerManager, String inputFileSuffix) {
    FileType inputFileType = FileTypeManager.getInstance().getFileTypeByExtension(inputFileSuffix);
    compilerManager.addCompilableFileType(inputFileType);
  }

}
