package net.jangaroo.ide.idea.jps;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import net.jangaroo.jooc.StdOutCompileLog;
import net.jangaroo.jooc.api.CompilationResult;
import net.jangaroo.jooc.api.Jooc;
import net.jangaroo.jooc.config.DebugMode;
import net.jangaroo.jooc.config.JoocConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.builders.FileProcessor;
import org.jetbrains.jps.builders.java.JavaBuilderUtil;
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor;
import org.jetbrains.jps.builders.logging.ProjectBuilderLogger;
import org.jetbrains.jps.incremental.BuilderCategory;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.ModuleBuildTarget;
import org.jetbrains.jps.incremental.ModuleLevelBuilder;
import org.jetbrains.jps.incremental.ProjectBuildException;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.model.java.JavaSourceRootProperties;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsOrderRootType;
import org.jetbrains.jps.model.module.JpsDependencyElement;
import org.jetbrains.jps.model.module.JpsLibraryDependency;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsTypedModuleSourceRoot;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Jangaroo analog of {@link org.jetbrains.jps.incremental.java.JavaBuilder}.
 */
public class JangarooBuilder extends ModuleLevelBuilder {

  private static final String JOOC_BUILDER_NAME = "jooc";
  
  public static final FileFilter AS_SOURCES_FILTER =
    SystemInfo.isFileSystemCaseSensitive?
    new FileFilter() {
      public boolean accept(File file) {
        return file.getPath().endsWith(Jooc.AS_SUFFIX);
      }
    } :
    new FileFilter() {
      public boolean accept(File file) {
        return StringUtil.endsWithIgnoreCase(file.getPath(), Jooc.AS_SUFFIX);
      }
    };

  public JangarooBuilder() {
    super(BuilderCategory.TRANSLATOR);
  }

  @Override
  public List<String> getCompilableFileExtensions() {
    return Collections.singletonList(Jooc.AS_SUFFIX_NO_DOT);
  }

  @Override
  public ExitCode build(CompileContext context, ModuleChunk chunk,
                        DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dirtyFilesHolder,
                        OutputConsumer outputConsumer) throws ProjectBuildException, IOException {
    final List<File> filesToCompile = new ArrayList<File>();

    dirtyFilesHolder.processDirtyFiles(new FileProcessor<JavaSourceRootDescriptor, ModuleBuildTarget>() {
      public boolean apply(ModuleBuildTarget target, File file, JavaSourceRootDescriptor descriptor) throws IOException {
        if (AS_SOURCES_FILTER.accept(file)) {
          filesToCompile.add(file);
        }
        return true;
      }
    });

    if (JavaBuilderUtil.isCompileJavaIncrementally(context)) {
      final ProjectBuilderLogger logger = context.getLoggingManager().getProjectBuilderLogger();
      if (logger.isEnabled()) {
        if (filesToCompile.size() > 0) {
          logger.logCompiledFiles(filesToCompile, JOOC_BUILDER_NAME, "Compiling files:");
        }
      }
    }

    if (!filesToCompile.isEmpty()) {
      Map<ModuleBuildTarget, String> finalOutputs = getCanonicalModuleOutputs(context, chunk);
      if (finalOutputs == null) {
        return ExitCode.ABORT;
      }

      Jooc jooc = new net.jangaroo.jooc.Jooc(); // TODO: instantiate Jooc from Jangaroo SDK (CompilerLoader)!
      for (ModuleBuildTarget moduleBuildTarget : finalOutputs.keySet()) {
        JoocConfiguration joocConfiguration = new JoocConfiguration();
        JpsModule module = moduleBuildTarget.getModule();
        List<File> sourcePath = new ArrayList<File>();
        for (JpsTypedModuleSourceRoot<JavaSourceRootProperties> sourceRoot : module.getSourceRoots(JavaSourceRootType.SOURCE)) {
          sourcePath.add(sourceRoot.getFile());
        }
        joocConfiguration.setSourcePath(sourcePath);

        ArrayList<File> classPath = new ArrayList<File>();
        List<JpsDependencyElement> dependencies = module.getDependenciesList().getDependencies();
        for (JpsDependencyElement dependency : dependencies) {
          if (dependency instanceof JpsLibraryDependency) {
            JpsLibrary library = ((JpsLibraryDependency)dependency).getLibrary();
            if (library != null) {
              classPath.addAll(library.getFiles(JpsOrderRootType.COMPILED));
            }
          }
        }
        joocConfiguration.setClassPath(classPath);
        joocConfiguration.setSourceFiles(filesToCompile);
        joocConfiguration.setOutputDirectory(moduleBuildTarget.getOutputDir());
        joocConfiguration.setDebugMode(DebugMode.SOURCE);
        jooc.setConfig(joocConfiguration);
        jooc.setLog(new StdOutCompileLog()); // TODO: implement Jangaroo CompileLog to delegate to JPS CompileContext!

        // TODO: hand over Facet configuration to JPS and configure it here!

        CompilationResult compilationResult = jooc.run();
        if (compilationResult.getResultCode() == CompilationResult.RESULT_CODE_COMPILATION_FAILED) {
          // TODO: add error (maybe the logger already did that)?
          return ExitCode.ABORT;
        }
        if (compilationResult.getResultCode() != CompilationResult.RESULT_CODE_OK) {
          // TODO: we used the compiler incorrectly. log or throw internal error?
          return ExitCode.ABORT;
        }
      }
      return ExitCode.OK;
    }

    return ExitCode.NOTHING_DONE;
  }

  @Nullable
  private Map<ModuleBuildTarget, String> getCanonicalModuleOutputs(CompileContext context, ModuleChunk chunk) {
    Map<ModuleBuildTarget, String> finalOutputs = new HashMap<ModuleBuildTarget, String>();
    for (ModuleBuildTarget target : chunk.getTargets()) {
      File moduleOutputDir = target.getOutputDir();
      if (moduleOutputDir == null) {
        context.processMessage(new CompilerMessage(JOOC_BUILDER_NAME, BuildMessage.Kind.ERROR, "Output directory not specified for module " + target.getModule().getName()));
        return null;
      }
      String moduleOutputPath = FileUtil.toCanonicalPath(moduleOutputDir.getPath());
      assert moduleOutputPath != null;
      finalOutputs.put(target, moduleOutputPath.endsWith("/") ? moduleOutputPath : moduleOutputPath + "/");
    }
    return finalOutputs;
  }
  
  @NotNull
  @Override
  public String getPresentableName() {
    return "Jangaroo Compiler";
  }
}
