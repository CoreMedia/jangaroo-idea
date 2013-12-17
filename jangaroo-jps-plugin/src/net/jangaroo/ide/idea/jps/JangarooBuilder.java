package net.jangaroo.ide.idea.jps;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import net.jangaroo.ide.idea.jps.util.CompilerLoader;
import net.jangaroo.jooc.api.CompilationResult;
import net.jangaroo.jooc.api.CompileLog;
import net.jangaroo.jooc.api.FilePosition;
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
import org.jetbrains.jps.model.JpsSimpleElement;
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
import java.util.Arrays;
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

      for (ModuleBuildTarget moduleBuildTarget : finalOutputs.keySet()) {
        JpsModule module = moduleBuildTarget.getModule();
        JpsSdk<JpsSimpleElement<JpsJangarooSdkProperties>> sdk = module.getSdk(JpsJangarooSdkType.INSTANCE);
        // TODO: use SDK to retrieve JARs derived from SDK's home path!
        JoocConfigurationBean settings = JoocConfigurationBean.getSettings(module);
        JoocConfiguration joocConfiguration = getJoocConfiguration(module, filesToCompile, false);
        CompilationResult compilationResult = runJooc(settings.jangarooSdkName, joocConfiguration, new JpsCompileLog(context));
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

  protected JoocConfiguration getJoocConfiguration(JpsModule module, List<File> sourceFiles, boolean forTests) {
    JoocConfigurationBean joocConfigurationBean = JoocConfigurationBean.getSettings(module);
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

  private void addToClassOrSourcePath(JpsModule module, Collection<File> classPath, Collection<File> sourcePath, boolean forTests) {
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

  public static CompilationResult runJooc(String jangarooSdkName, JoocConfiguration configuration, CompileLog log) {
    Jooc jooc;
    try {
      jooc = CompilerLoader.loadJooc(getJarFileNames(jangarooSdkName));
    } catch (FileNotFoundException e) {
      //context.addMessage(CompilerMessageCategory.ERROR, e.getMessage(), null, -1, -1);
      return null;
    } catch (Exception e) {
//      context.addMessage(CompilerMessageCategory.ERROR, jangarooSdkName +
//        " not correctly set up or not compatible with this Jangaroo IDEA plugin: " + e.getMessage(),
//        null, -1, -1);
      return null;
    }
    jooc.setConfig(configuration);
    jooc.setLog(log);
    return jooc.run();
  }

  public static List<String> getJarFileNames(String jangarooSdkName) {
    return Arrays.asList("C:/Users/fwienber/.m2/repository/net/jangaroo/jangaroo-compiler/2.0.8/jangaroo-compiler-2.0.8.jar",
      "C:/Users/fwienber/.m2/repository/net/jangaroo/exml-compiler/2.0.8/exml-compiler-2.0.8.jar",
      "C:/Users/fwienber/.m2/repository/net/jangaroo/properties-compiler/2.0.8/properties-compiler-2.0.8.jar");
//    // TODO: JPS Jangaroo SDK!
//    JpsSdk jangarooSdk = project.getSdkReferencesTable().getSdkReference(JpsJavaSdkType.INSTANCE);
//    if (jangarooSdk == null) {
//      throw new IllegalStateException("Jangaroo SDK '" + jangarooSdkName + "' not found.");
//    }
//    // TODO:
//    File[] files = jangarooSdk.getSdkProperties()...;
//    List<String> jarFileNames = new ArrayList<String>(files.length);
//    for (File file : files) {
//      String filename = file.getPath();
//      if (filename.endsWith("!/")) {
//        filename = filename.substring(0, filename.length() - "!/".length());
//      }
//      jarFileNames.add(filename);
//    }
//    return jarFileNames;
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

  protected static class JpsCompileLog implements CompileLog {
    private MessageHandler messageHandler;
    private boolean hasErrors = false;

    public JpsCompileLog(MessageHandler messageHandler) {
      this.messageHandler = messageHandler;
    }

    private void addMessage(BuildMessage.Kind compilerMessageCategory, String msg, FilePosition position) {
      messageHandler.processMessage(new CompilerMessage(JOOC_BUILDER_NAME, compilerMessageCategory, msg,
        position.getFileName(), 0L, 0L, 0L, (long)position.getLine(), (long)position.getColumn()));
      if (compilerMessageCategory == BuildMessage.Kind.ERROR) {
        hasErrors = true;
      }
    }

    public void error(FilePosition position, String msg) {
      addMessage(BuildMessage.Kind.ERROR, msg, position);
    }

    public void error(String msg) {
      messageHandler.processMessage(new CompilerMessage(JOOC_BUILDER_NAME, BuildMessage.Kind.ERROR, msg));
      hasErrors = true;
    }

    public void warning(FilePosition position, String msg) {
      addMessage(BuildMessage.Kind.WARNING, msg, position);
    }

    public void warning(String msg) {
      messageHandler.processMessage(new CompilerMessage(JOOC_BUILDER_NAME, BuildMessage.Kind.WARNING, msg));
    }

    public boolean hasErrors() {
      return hasErrors;
    }

  }
}
