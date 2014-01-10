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
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor;
import org.jetbrains.jps.incremental.BuilderCategory;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.MessageHandler;
import org.jetbrains.jps.incremental.ModuleBuildTarget;
import org.jetbrains.jps.incremental.ModuleLevelBuilder;
import org.jetbrains.jps.incremental.ProjectBuildException;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.model.java.JavaResourceRootType;
import org.jetbrains.jps.model.java.JavaSourceRootProperties;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.java.JpsJavaDependencyExtension;
import org.jetbrains.jps.model.java.JpsJavaDependencyScope;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsOrderRootType;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.model.module.JpsDependencyElement;
import org.jetbrains.jps.model.module.JpsLibraryDependency;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleDependency;
import org.jetbrains.jps.model.module.JpsModuleSourceRoot;
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
import java.util.Set;

/**
 * Jangaroo analog of {@link org.jetbrains.jps.incremental.java.JavaBuilder}.
 */
public class JangarooBuilder extends ModuleLevelBuilder {

  private static final String JOOC_BUILDER_NAME = "jooc";
  
  public static final FileFilter AS_SOURCES_FILTER = createSuffixFileFilter(Jooc.AS_SUFFIX);

  public static FileFilter createSuffixFileFilter(final String suffix) {
    return SystemInfo.isFileSystemCaseSensitive?
    new FileFilter() {
      public boolean accept(File file) {
        return file.getPath().endsWith(suffix);
      }
    } :
    new FileFilter() {
      public boolean accept(File file) {
        return StringUtil.endsWithIgnoreCase(file.getPath(), suffix);
      }
    };
  }

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

    final Map<ModuleBuildTarget, List<File>> filesToCompile = getFilesToCompile(
      AS_SOURCES_FILTER,
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
        ExitCode result = compile(context, outputConsumer, filesToCompile.get(moduleBuildTarget), compileLog,
          moduleBuildTarget, module, jarPaths);
        if (result != null) {
          return result;
        }
      }
      return ExitCode.OK;
    }

    return ExitCode.NOTHING_DONE;
  }

  private ExitCode compile(CompileContext context, OutputConsumer outputConsumer, List<File> filesToCompile,
                           JpsCompileLog compileLog, ModuleBuildTarget moduleBuildTarget,
                           JpsModule module, List<String> jarPaths) throws IOException {
    JoocConfiguration joocConfiguration = getJoocConfiguration(module, filesToCompile, moduleBuildTarget.isTests());
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
    return null;
  }

  private CompilationResult compile(ModuleBuildTarget moduleBuildTarget, Jooc jooc,
                                    OutputConsumer outputConsumer) throws IOException {
    CompilationResult compilationResult = jooc.run();
    for (Map.Entry<File, File> sourceToTarget : compilationResult.getOutputFileMap().entrySet()) {
      if (sourceToTarget.getValue() != null) { // only non-native classes!
        outputConsumer.registerOutputFile(moduleBuildTarget, sourceToTarget.getValue(), toSingletonPath(sourceToTarget.getKey()));
      }
    }
    return compilationResult;
  }

  public static Set<String> toSingletonPath(File file) {
    return Collections.singleton(file.getPath());
  }

  public static Map<ModuleBuildTarget, List<File>> getFilesToCompile(final FileFilter sourcesFilter,
                                                                     DirtyFilesHolder<JavaSourceRootDescriptor,
                                                                       ModuleBuildTarget> dirtyFilesHolder) throws IOException {
    // a map of files, grouped by module first, then by test sources (true) versus non-test sources (false)
    final Map<ModuleBuildTarget, List<File>> filesToCompile = new HashMap<ModuleBuildTarget, List<File>>();

    dirtyFilesHolder.processDirtyFiles(new FileProcessor<JavaSourceRootDescriptor, ModuleBuildTarget>() {
      public boolean apply(ModuleBuildTarget target, File file, JavaSourceRootDescriptor descriptor) throws IOException {
        if (sourcesFilter.accept(file)) {
          if (!filesToCompile.containsKey(target)) {
            filesToCompile.put(target, new ArrayList<File>());
          }
          filesToCompile.get(target).add(file);
        }
        return true;
      }
    });
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
    updateFileLocations(joocConfig, module, forTests);
    joocConfig.setSourceFiles(sourceFiles);
    joocConfig.setMergeOutput(false); // no longer supported: joocConfigurationBean.mergeOutput;
    joocConfig.setOutputDirectory(forTests ? joocConfigurationBean.getTestOutputDirectory() : joocConfigurationBean.getOutputDirectory());
    joocConfig.setPublicApiViolationsMode(joocConfigurationBean.publicApiViolationsMode);
    return joocConfig;
  }

  public static void updateFileLocations(FileLocations fileLocations, JpsModule module, boolean forTests) {
    Collection<File> classPath = new LinkedHashSet<File>();
    Collection<File> sourcePath = new LinkedHashSet<File>();
    addToClassOrSourcePath(module, classPath, sourcePath, forTests);
    fileLocations.setClassPath(new ArrayList<File>(classPath));
    try {
      fileLocations.setSourcePath(new ArrayList<File>(sourcePath));
    } catch (IOException e) {
      throw new RuntimeException("while constructing Jangaroo source path", e);
    }
  }

  public static void addToClassOrSourcePath(JpsModule module, Collection<File> classPath, Collection<File> sourcePath, boolean forTests) {
    JavaSourceRootType sourceRootType = forTests ? JavaSourceRootType.TEST_SOURCE : JavaSourceRootType.SOURCE;
    for (JpsTypedModuleSourceRoot<JavaSourceRootProperties> sourceRoot : module.getSourceRoots(sourceRootType)) {
      sourcePath.add(sourceRoot.getFile());
    }
    // special case: the deprecated src/main/joo-api directory is still used, but in IDEA 13 it is no longer a source,
    // but a resource directory!
    for (JpsTypedModuleSourceRoot resourceRoot : module.getSourceRoots(JavaResourceRootType.RESOURCE)) {
      File resourceRootFile = resourceRoot.getFile();
      if ("joo-api".equals(resourceRootFile.getName())) {
        classPath.add(resourceRootFile);
      }
    }

    if (forTests) {
      for (JpsTypedModuleSourceRoot<JavaSourceRootProperties> sourceRoot : module.getSourceRoots(JavaSourceRootType.SOURCE)) {
        classPath.add(sourceRoot.getFile());
      }
    }
    JpsJavaExtensionService javaExtensionService = JpsJavaExtensionService.getInstance();
    List<JpsDependencyElement> dependencies = module.getDependenciesList().getDependencies();
    for (JpsDependencyElement dependency : dependencies) {
      JpsJavaDependencyExtension dependencyExtension = javaExtensionService.getDependencyExtension(dependency);
      if (dependencyExtension == null || dependencyExtension.getScope() == JpsJavaDependencyScope.COMPILE || 
        dependencyExtension.getScope() == JpsJavaDependencyScope.TEST && forTests) {
        addToClassPath(classPath, dependency);
      }
    }
  }

  private static void addToClassPath(Collection<File> classPath, JpsDependencyElement dependency) {
    if (dependency instanceof JpsLibraryDependency) {
      JpsLibrary library = ((JpsLibraryDependency)dependency).getLibrary();
      if (library != null) {
        classPath.addAll(library.getFiles(JpsOrderRootType.COMPILED));
      }
    } else if (dependency instanceof JpsModuleDependency) {
      JpsModule otherModule = ((JpsModuleDependency)dependency).getModule();
      if (otherModule != null) {
        for (JpsModuleSourceRoot sourceRoot : otherModule.getSourceRoots(JavaSourceRootType.SOURCE)) {
          classPath.add(sourceRoot.getFile());
        }
        for (JpsModuleSourceRoot sourceRoot : otherModule.getSourceRoots(JavaResourceRootType.RESOURCE)) {
          classPath.add(sourceRoot.getFile());
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
