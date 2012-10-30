package net.jangaroo.ide.idea;

import com.intellij.openapi.compiler.*;
import com.intellij.openapi.compiler.Compiler;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import net.jangaroo.ide.idea.exml.ExmlCompiler;
import net.jangaroo.ide.idea.properties.PropertiesCompiler;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

/**
 * Factory for all Jangaroo compilers:
 * <ul>
 *   <li>AS3->JS Compiler "jooc"</li>
 *   <li>EXML->AS3 Compiler "exmlc"</li>
 *   <li>properties->AS3 Compiler "propc"</li>
 * </ul>
 * Because they are translating compilers, we cannot use IDEA's usual Compiler extension point.
 * Instead, we implement a CompilerFactory to get hold of the compiler manager, but do not
 * actually return anything, but register the translating compilers directly.
 */
public class JoocCompilerFactory implements CompilerFactory {

  public Compiler[] createCompilers(@NotNull CompilerManager compilerManager) {
    registerCompiler(compilerManager, new JangarooCompiler());
    registerCompiler(compilerManager, new ExmlCompiler());
    registerCompiler(compilerManager, new PropertiesCompiler());
    return new Compiler[0]; // already registered the compilers ourselves
  }

  private static void registerCompiler(CompilerManager compilerManager, AbstractCompiler compiler) {
    FileType inputFileType = FileTypeManager.getInstance().getFileTypeByExtension(compiler.getInputFileSuffix());
    FileType outputFileType = FileTypeManager.getInstance().getFileTypeByExtension(compiler.getOutputFileSuffix());
    compilerManager.addCompilableFileType(inputFileType);
    compilerManager.addTranslatingCompiler(compiler,
      Collections.<FileType>singleton(inputFileType),
      Collections.<FileType>singleton(outputFileType));
  }

}
