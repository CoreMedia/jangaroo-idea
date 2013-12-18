package net.jangaroo.ide.idea.jps;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import net.jangaroo.ide.idea.jps.util.CompilerLoader;
import net.jangaroo.ide.idea.jps.util.JpsCompileLog;
import net.jangaroo.jooc.api.CompilationResult;
import net.jangaroo.jooc.api.CompileLog;
import net.jangaroo.jooc.api.Jooc;
import net.jangaroo.jooc.config.DebugMode;
import net.jangaroo.jooc.config.JoocConfiguration;
import net.jangaroo.utils.FileLocations;
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
import org.jetbrains.jps.incremental.MessageHandler;
import org.jetbrains.jps.incremental.ModuleBuildTarget;
import org.jetbrains.jps.incremental.ModuleLevelBuilder;
import org.jetbrains.jps.incremental.ProjectBuildException;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.model.java.JavaSourceRootProperties;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsOrderRootType;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.model.module.JpsDependencyElement;
import org.jetbrains.jps.model.module.JpsLibraryDependency;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsTypedModuleSourceRoot;

import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
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

    final Map<JpsModule, List<File>> filesToCompile = getFilesToCompile(
      JOOC_BUILDER_NAME,
      AS_SOURCES_FILTER,
      context,
      dirtyFilesHolder
    );

    if (!filesToCompile.isEmpty()) {
      Map<ModuleBuildTarget, String> finalOutputs = getCanonicalModuleOutputs(context, chunk);
      if (finalOutputs == null) {
        return ExitCode.ABORT;
      }

      JpsCompileLog compileLog = new JpsCompileLog(JOOC_BUILDER_NAME, context);
      for (ModuleBuildTarget moduleBuildTarget : finalOutputs.keySet()) {
        JpsModule module = moduleBuildTarget.getModule();
        JpsSdk sdk = module.getSdk(JpsJangarooSdkType.INSTANCE);
        if (sdk == null) {
          context.processMessage(new CompilerMessage(JOOC_BUILDER_NAME, BuildMessage.Kind.WARNING,
            String.format("Jangaroo module %s does not have a Jangaroo SDK.", module.getName())));
          continue;
        }
        List<String> jarPaths = JpsJangarooSdkType.getSdkJarPaths(sdk);
        JoocConfiguration joocConfiguration = getJoocConfiguration(module, filesToCompile.get(module), false);
        Jooc jooc = getJooc(context, jarPaths, joocConfiguration, compileLog);
        if (jooc == null) {
          return ExitCode.ABORT;
        }
        CompilationResult compilationResult = compile(moduleBuildTarget, jooc, outputConsumer);
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

  private CompilationResult compile(ModuleBuildTarget moduleBuildTarget, Jooc jooc,
                                    OutputConsumer outputConsumer) throws IOException {
    CompilationResult compilationResult = jooc.run();
    for (Map.Entry<File, File> sourceToTarget : compilationResult.getOutputFileMap().entrySet()) {
      outputConsumer.registerOutputFile(moduleBuildTarget, sourceToTarget.getValue(),
        Collections.singleton(sourceToTarget.getKey().getPath()));
    }
    return compilationResult;
  }

  public static Map<JpsModule, List<File>> getFilesToCompile(String builderName, final FileFilter sourcesFilter,
                                                             CompileContext context,
                                                             DirtyFilesHolder<JavaSourceRootDescriptor,
                                                               ModuleBuildTarget> dirtyFilesHolder) throws IOException {
    final Map<JpsModule, List<File>> filesToCompile = new HashMap<JpsModule, List<File>>();

    dirtyFilesHolder.processDirtyFiles(new FileProcessor<JavaSourceRootDescriptor, ModuleBuildTarget>() {
      public boolean apply(ModuleBuildTarget target, File file, JavaSourceRootDescriptor descriptor) throws IOException {
        if (sourcesFilter.accept(file)) {
          JpsModule module = target.getModule();
          if (!filesToCompile.containsKey(module)) {
            filesToCompile.put(module, new ArrayList<File>());
          }
          filesToCompile.get(module).add(file);
        }
        return true;
      }
    });

    if (JavaBuilderUtil.isCompileJavaIncrementally(context)) {
      final ProjectBuilderLogger logger = context.getLoggingManager().getProjectBuilderLogger();
      if (logger.isEnabled()) {
        for (List<File> files : filesToCompile.values()) {
          if (files.size() > 0) {
            logger.logCompiledFiles(files, builderName, "Compiling files:");
          }
        }
      }
    }
    return filesToCompile;
  }

  protected JoocConfiguration getJoocConfiguration(JpsModule module, List<File> sourceFiles, boolean forTests) {
    JoocConfigurationBean joocConfigurationBean = JangarooModelSerializerExtension.getJoocSettings(module);
    JoocConfiguration joocConfig = new JoocConfiguration();
    joocConfig.setVerbose(joocConfigurationBean.verbose);
    joocConfig.setDebugMode(joocConfigurationBean.isDebug() ? joocConfigurationBean.isDebugSource() ? DebugMode.SOURCE : DebugMode.LINES : null);
    joocConfig.setAllowDuplicateLocalVariables(joocConfigurationBean.allowDuplicateLocalVariables);
    joocConfig.setEnableAssertions(joocConfigurationBean.enableAssertions);
    joocConfig.setApiOutputDirectory(forTests ? null : joocConfigurationBean.getApiOutputDirectory());
    updateFileLocations(joocConfig, module, sourceFiles, forTests);
    joocConfig.setMergeOutput(false); // no longer supported: joocConfigurationBean.mergeOutput;
    joocConfig.setOutputDirectory(forTests ? joocConfigurationBean.getTestOutputDirectory() : joocConfigurationBean.getOutputDirectory());
    joocConfig.setPublicApiViolationsMode(joocConfigurationBean.publicApiViolationsMode);
    return joocConfig;
  }

  protected void updateFileLocations(FileLocations fileLocations, JpsModule module, List<File> sourceFiles, boolean forTests) {
    Collection<File> classPath = new LinkedHashSet<File>();
    Collection<File> sourcePath = new LinkedHashSet<File>();
    addToClassOrSourcePath(module, classPath, sourcePath, forTests);
    fileLocations.setClassPath(new ArrayList<File>(classPath));
    try {
      fileLocations.setSourcePath(new ArrayList<File>(sourcePath));
    } catch (IOException e) {
      throw new RuntimeException("while constructing Jangaroo source path", e);
    }
    fileLocations.setSourceFiles(sourceFiles);
  }

  public static void addToClassOrSourcePath(JpsModule module, Collection<File> classPath, Collection<File> sourcePath, boolean forTests) {
    for (JpsTypedModuleSourceRoot<JavaSourceRootProperties> sourceRoot : module.getSourceRoots(JavaSourceRootType.SOURCE)) {
      sourcePath.add(sourceRoot.getFile());
    }
    List<JpsDependencyElement> dependencies = module.getDependenciesList().getDependencies();
    for (JpsDependencyElement dependency : dependencies) {
      if (dependency instanceof JpsLibraryDependency) {
        JpsLibrary library = ((JpsLibraryDependency)dependency).getLibrary();
        if (library != null) {
          classPath.addAll(library.getFiles(JpsOrderRootType.COMPILED));
        }
      }
    }
  }

  public static Jooc getJooc(MessageHandler messageHandler, List<String> jarPaths, JoocConfiguration configuration, CompileLog log) {
    Jooc jooc;
    try {
      jooc = CompilerLoader.loadJooc(jarPaths);
    } catch (FileNotFoundException e) {
      messageHandler.processMessage(new CompilerMessage(JOOC_BUILDER_NAME, e));
      return null;
    } catch (Exception e) {
      // Jangaroo SDK not correctly set up or not compatible with this Jangaroo IDEA plugin: 
      messageHandler.processMessage(new CompilerMessage(JOOC_BUILDER_NAME, e));
      return null;
    }
    jooc.setConfig(configuration);
    jooc.setLog(log);
    return jooc;
  }

  @Nullable
  public static Map<ModuleBuildTarget, String> getCanonicalModuleOutputs(CompileContext context, ModuleChunk chunk) {
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
