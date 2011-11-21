package net.jangaroo.ide.idea;

import com.intellij.openapi.compiler.*;
import com.intellij.openapi.compiler.Compiler;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

/**
 * Factory for Jangaroo AS3->JS Compiler "jooc".
 * Because jooc is a translating compiler, we cannot use IDEA's usual Compiler extension point.
 * Instead, we implement a CompilerFactory to get hold of the compiler manager, but do not
 * actually return anything, but register the translating compiler directly.
 */
public class JoocCompilerFactory implements CompilerFactory {

  public Compiler[] createCompilers(@NotNull CompilerManager compilerManager) {
    FileType actionscript = FileTypeManager.getInstance().getFileTypeByExtension("as");
    FileType javascript = FileTypeManager.getInstance().getFileTypeByExtension("js");

    compilerManager.addCompilableFileType(actionscript);
    compilerManager.addTranslatingCompiler(new JangarooCompiler(),
      Collections.<FileType>singleton(actionscript),
      Collections.<FileType>singleton(javascript));
    return new Compiler[0]; // already registered the compiler ourselves
  }

}
