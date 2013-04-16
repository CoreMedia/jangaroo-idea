package net.jangaroo.ide.idea;

import com.intellij.openapi.compiler.*;
import com.intellij.openapi.compiler.Compiler;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import net.jangaroo.ide.idea.exml.ExmlCompiler;
import net.jangaroo.ide.idea.properties.PropertiesCompiler;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Factory for all Jangaroo compilers:
 * <ul>
 *   <li>*.as/*.mxml -> *.js: Compiler "jooc"</li>
 *   <li>*.exml -> *.as: Compiler "exmlc"</li>
 *   <li>*.properties -> *.as: Compiler "propc"</li>
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
    FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    Set<String> inputFileSuffixes = compiler.getInputFileSuffixes();
    Set<FileType> inputFileTypes = new LinkedHashSet<FileType>(2);
    for (String inputFileSuffix : inputFileSuffixes) {
      FileType inputFileType = fileTypeManager.getFileTypeByExtension(inputFileSuffix);
      compilerManager.addCompilableFileType(inputFileType);
      inputFileTypes.add(inputFileType);
    }
    FileType outputFileType = fileTypeManager.getFileTypeByExtension(compiler.getOutputFileSuffix());
    compilerManager.addTranslatingCompiler(compiler,
      inputFileTypes,
      Collections.singleton(outputFileType));
  }

}
