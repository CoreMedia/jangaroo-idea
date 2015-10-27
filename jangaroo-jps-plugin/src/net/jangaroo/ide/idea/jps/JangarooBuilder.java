package net.jangaroo.ide.idea.jps;

import com.intellij.openapi.diagnostic.Logger;
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
import org.jetbrains.jps.builders.BuildOutputConsumer;
import org.jetbrains.jps.builders.BuildRootDescriptor;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.builders.FileProcessor;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.MessageHandler;
import org.jetbrains.jps.incremental.ProjectBuildException;
import org.jetbrains.jps.incremental.TargetBuilder;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.CustomBuilderMessage;
import org.jetbrains.jps.model.JpsDummyElement;
import org.jetbrains.jps.model.java.JavaResourceRootType;
import org.jetbrains.jps.model.java.JavaSourceRootProperties;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.java.JpsJavaDependencyExtension;
import org.jetbrains.jps.model.java.JpsJavaDependencyScope;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsOrderRootType;
import org.jetbrains.jps.model.library.JpsTypedLibrary;
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
 * However, it seems that non-Java-languages need to implement TargetBuilder, not ModuleLevelBuilder,
 * since the latter does not seem to be work for Flex build configurations.
 */
public class JangarooBuilder extends TargetBuilder<BuildRootDescriptor, JangarooBuildTarget> {

  public static final String BUILDER_NAME = "jooc";
  
  public static final FileFilter AS_SOURCES_FILTER = createJangarooSourceFileFilter();
  public static final String FILE_INVALIDATION_BUILDER_MESSAGE = "FILE_INVALIDATION";
  private final Logger log = Logger.getInstance(JangarooBuilder.class);

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

  private static FileFilter createJangarooSourceFileFilter() {
    return SystemInfo.isFileSystemCaseSensitive?
      new FileFilter() {
        public boolean accept(File file) {
          String path = file.getPath();
          return path.endsWith(Jooc.AS_SUFFIX) || path.endsWith(Jooc.MXML_SUFFIX);
        }
      } :
      new FileFilter() {
        public boolean accept(File file) {
          String path = file.getPath();
          return StringUtil.endsWithIgnoreCase(path, Jooc.AS_SUFFIX) || StringUtil.endsWithIgnoreCase(path, Jooc.MXML_SUFFIX);
        }
      };
  }

  public JangarooBuilder() {
    super(Collections.singletonList(JangarooBuildTargetType.INSTANCE));
  }

  @Override
  public void build(@NotNull JangarooBuildTarget target, @NotNull DirtyFilesHolder<BuildRootDescriptor, JangarooBuildTarget> dirtyFilesHolder, @NotNull BuildOutputConsumer outputConsumer, @NotNull CompileContext context) throws ProjectBuildException, IOException {

    final Map<JangarooBuildTarget, List<File>> filesToCompile = getFilesToCompile(
      AS_SOURCES_FILTER,
      dirtyFilesHolder
    );

    if (!filesToCompile.isEmpty()) {
      Map<JangarooBuildTarget, String> finalOutputs = getCanonicalModuleOutputs(context, filesToCompile.keySet());
      if (finalOutputs == null) {
        return;
      }

      JpsCompileLog compileLog = new JpsCompileLog(BUILDER_NAME, context);
      for (JangarooBuildTarget moduleBuildTarget : finalOutputs.keySet()) {
        compile(context, outputConsumer, filesToCompile.get(moduleBuildTarget), compileLog,
          moduleBuildTarget);
      }
    }
  }

  private boolean compile(CompileContext context, BuildOutputConsumer outputConsumer, List<File> filesToCompile,
                           JpsCompileLog compileLog, JangarooBuildTarget moduleBuildTarget) throws IOException {
    JpsModule module = moduleBuildTarget.getBC().getModule();
    JoocConfigurationBean joocConfigurationBean = JangarooModelSerializerExtension.getJoocSettings(module);
    if (joocConfigurationBean == null) {
      return true; // no Jangaroo Facet in this module: skip silently!
    }
    List<String> jarPaths = getJangarooSdkJarPath(joocConfigurationBean, module);
    if (jarPaths == null) {
      context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.WARNING,
        String.format("Jangaroo module %s does not have a valid Jangaroo SDK. Compilation skipped.", module.getName())));
      return false;
    }
    JoocConfiguration joocConfiguration = getJoocConfiguration(joocConfigurationBean, module, filesToCompile, false /*moduleBuildTarget.isTests()*/);
    log.info(String.format("Compiling module %s...", module.getName()));
    if (log.isDebugEnabled()) {
      log.debug(String.format("  module %s classpath=%s, sourcepath=%s, sourcefiles=%s", module.getName(),
        joocConfiguration.getClassPath(), joocConfiguration.getSourcePath(), joocConfiguration.getSourceFiles()));
    }
    Jooc jooc = getJooc(context, jarPaths, joocConfiguration, compileLog);
    if (jooc == null) {
      log.warn(String.format("No Jangaroo build configuration found in module %s.", module.getName()));
      return false;
    }
    CompilationResult compilationResult = compile(jooc, outputConsumer);
    if (compilationResult.getResultCode() == CompilationResult.RESULT_CODE_COMPILATION_FAILED) {
      log.info(String.format("Compilation failed in module %s.", module.getName()));
      return false;
    }
    if (compilationResult.getResultCode() != CompilationResult.RESULT_CODE_OK) {
      log.error(String.format("Unexpected compilation reulst %s in module %s.", compilationResult.getResultCode(), module.getName()));
      return false;
    }
    log.info(String.format("Compilation of module %s completed successfully.", module.getName()));
    return true;
  }

  @Nullable
  public static List<String> getJangarooSdkJarPath(JoocConfigurationBean joocConfigurationBean, JpsModule module) {
    JpsTypedLibrary<JpsSdk<JpsDummyElement>> jangarooSdkLibrary = module.getProject().getModel().getGlobal().getLibraryCollection()
      .findLibrary(joocConfigurationBean.jangarooSdkName, JpsJangarooSdkType.INSTANCE);
    if (jangarooSdkLibrary == null) {
      return null;
    }
    JpsSdk<JpsDummyElement> sdk = jangarooSdkLibrary.getProperties();
    return JpsJangarooSdkType.getSdkJarPaths(sdk);
  }

  private CompilationResult compile(Jooc jooc,
                                    BuildOutputConsumer outputConsumer) throws IOException {
    CompilationResult compilationResult = jooc.run();
    for (Map.Entry<File, File> sourceToTarget : compilationResult.getOutputFileMap().entrySet()) {
      if (sourceToTarget.getValue() != null) { // only non-native classes!
        outputConsumer.registerOutputFile(sourceToTarget.getValue(), toSingletonPath(sourceToTarget.getKey()));
      }
    }
    return compilationResult;
  }

  public static Set<String> toSingletonPath(File file) {
    return Collections.singleton(file.getPath());
  }

  public static Map<JangarooBuildTarget, List<File>> getFilesToCompile(final FileFilter sourcesFilter,
                                                                     DirtyFilesHolder<BuildRootDescriptor,
                                                                       JangarooBuildTarget> dirtyFilesHolder) throws IOException {
    // a map of files, grouped by module first, then by test sources (true) versus non-test sources (false)
    final Map<JangarooBuildTarget, List<File>> filesToCompile = new HashMap<JangarooBuildTarget, List<File>>();

    dirtyFilesHolder.processDirtyFiles(new FileProcessor<BuildRootDescriptor, JangarooBuildTarget>() {
      public boolean apply(JangarooBuildTarget target, File file, BuildRootDescriptor descriptor) throws IOException {
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

  protected JoocConfiguration getJoocConfiguration(JoocConfigurationBean joocConfigurationBean, JpsModule module, List<File> sourceFiles, boolean forTests) {
    JoocConfiguration joocConfig = new JoocConfiguration();
    joocConfig.setVerbose(joocConfigurationBean.verbose);
    joocConfig.setDebugMode(joocConfigurationBean.isDebug() ? joocConfigurationBean.isDebugSource() ? DebugMode.SOURCE : DebugMode.LINES : null);
    joocConfig.setAllowDuplicateLocalVariables(joocConfigurationBean.allowDuplicateLocalVariables);
    joocConfig.setEnableAssertions(joocConfigurationBean.enableAssertions);
    joocConfig.setApiOutputDirectory(forTests ? null : joocConfigurationBean.getApiOutputDirectory());
    updateFileLocations(joocConfig, module, forTests, true);
    joocConfig.setSourceFiles(sourceFiles);
    joocConfig.setMergeOutput(false); // no longer supported: joocConfigurationBean.mergeOutput;
    joocConfig.setOutputDirectory(forTests ? joocConfigurationBean.getTestOutputDirectory() : joocConfigurationBean.getOutputDirectory());
    joocConfig.setPublicApiViolationsMode(joocConfigurationBean.publicApiViolationsMode);
    return joocConfig;
  }

  public static void updateFileLocations(FileLocations fileLocations, JpsModule module,
                                         boolean forTests, boolean compileGeneratedSources) {
    Collection<File> classPath = new LinkedHashSet<File>();
    Collection<File> sourcePath = new LinkedHashSet<File>();
    addToClassOrSourcePath(module, classPath, sourcePath, forTests, compileGeneratedSources);
    fileLocations.setClassPath(new ArrayList<File>(classPath));
    try {
      fileLocations.setSourcePath(new ArrayList<File>(sourcePath));
    } catch (IOException e) {
      throw new RuntimeException("while constructing Jangaroo source path", e);
    }
  }

  public static void addToClassOrSourcePath(JpsModule module, Collection<File> classPath, Collection<File> sourcePath,
                                            boolean forTests, boolean compileGeneratedSources) {
    JavaSourceRootType sourceRootType = forTests ? JavaSourceRootType.TEST_SOURCE : JavaSourceRootType.SOURCE;
    for (JpsTypedModuleSourceRoot<JavaSourceRootProperties> sourceRoot : module.getSourceRoots(sourceRootType)) {
      if (compileGeneratedSources || !sourceRoot.getProperties().isForGeneratedSources()) {
        sourcePath.add(sourceRoot.getFile());
      } else {
        classPath.add(sourceRoot.getFile());
      }
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
      messageHandler.processMessage(new CompilerMessage(BUILDER_NAME, e));
      return null;
    } catch (Exception e) {
      // Jangaroo SDK not correctly set up or not compatible with this Jangaroo IDEA plugin: 
      messageHandler.processMessage(new CompilerMessage(BUILDER_NAME, e));
      return null;
    }
    jooc.setConfig(configuration);
    jooc.setLog(log);
    return jooc;
  }

  @Nullable
  public static Map<JangarooBuildTarget, String> getCanonicalModuleOutputs(CompileContext context, Collection<JangarooBuildTarget> flexBuildTargets) {
    Map<JangarooBuildTarget, String> finalOutputs = new HashMap<JangarooBuildTarget, String>();
    for (JangarooBuildTarget target : flexBuildTargets) {
      String moduleOutputDir = target.getBC().getOutputFolder();
      if (moduleOutputDir.isEmpty()) {
        context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR, "Output directory not specified for module " + target.getBC().getModule().getName()));
        return null;
      }
      String moduleOutputPath = FileUtil.toCanonicalPath(moduleOutputDir);
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

  public static void processFileInvalidationMessage(CompileContext context, File file) {
    context.processMessage(new CustomBuilderMessage(BUILDER_NAME, FILE_INVALIDATION_BUILDER_MESSAGE, file.getPath()));
  }
}
