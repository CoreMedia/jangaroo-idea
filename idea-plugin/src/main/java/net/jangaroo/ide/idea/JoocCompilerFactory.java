package net.jangaroo.ide.idea;

import com.intellij.openapi.compiler.*;
import com.intellij.openapi.compiler.Compiler;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import net.jangaroo.jooc.api.Jooc;
import org.jetbrains.annotations.NotNull;

/**
 * Since IDEA 13, compilation is done by "external build" (JPS), so we just use the
 * compiler manager to register compilable file extensions for <code>jooc</code>:
 * <ul>
 *   <li>as</li>
 *   <li>mxml</li>
 *   <li>properties</li>
 * </ul>
 */
public class JoocCompilerFactory implements CompilerFactory {

  public Compiler[] createCompilers(@NotNull CompilerManager compilerManager) {
    addCompilableFileType(compilerManager, Jooc.AS_SUFFIX_NO_DOT);
    addCompilableFileType(compilerManager, Jooc.MXML_SUFFIX_NO_DOT);
    // TODO addCompilableFileType(compilerManager, "properties");
    return new Compiler[0]; // already registered the compilers ourselves
  }

  private static void addCompilableFileType(CompilerManager compilerManager, String inputFileSuffix) {
    FileType inputFileType = FileTypeManager.getInstance().getFileTypeByExtension(inputFileSuffix);
    compilerManager.addCompilableFileType(inputFileType);
  }

}
