package net.jangaroo.ide.idea.jps.exml;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import net.jangaroo.exml.api.Exmlc;
import net.jangaroo.exml.api.ExmlcException;
import net.jangaroo.exml.config.ExmlConfiguration;
import net.jangaroo.ide.idea.jps.JangarooBuilder;
import net.jangaroo.ide.idea.jps.JangarooModelSerializerExtension;
import net.jangaroo.ide.idea.jps.JpsJangarooSdkType;
import net.jangaroo.ide.idea.jps.util.CompilerLoader;
import net.jangaroo.ide.idea.jps.util.JpsCompileLog;
import net.jangaroo.jooc.api.FilePosition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor;
import org.jetbrains.jps.incremental.BuilderCategory;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.MessageHandler;
import org.jetbrains.jps.incremental.ModuleBuildTarget;
import org.jetbrains.jps.incremental.ModuleLevelBuilder;
import org.jetbrains.jps.incremental.ProjectBuildException;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.model.module.JpsModule;
import org.xml.sax.SAXParseException;

import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static net.jangaroo.ide.idea.jps.util.IdeaFileUtils.toPath;

/**
 * Jangaroo analog of {@link org.jetbrains.jps.incremental.java.JavaBuilder}.
 */
public class ExmlBuilder extends ModuleLevelBuilder {

  private static final String EXML_BUILDER_NAME = "exmlc";
  public static final FileFilter EXMLC_SOURCES_FILTER =
    SystemInfo.isFileSystemCaseSensitive?
    new FileFilter() {
      public boolean accept(File file) {
        String path = file.getPath();
        return path.endsWith(Exmlc.EXML_SUFFIX);
      }
    } :
    new FileFilter() {
      public boolean accept(File file) {
        String path = file.getPath();
        return StringUtil.endsWithIgnoreCase(path, Exmlc.EXML_SUFFIX);
      }
    };

  public ExmlBuilder() {
    super(BuilderCategory.SOURCE_GENERATOR);
  }

  public static void copyFromBeanToConfiguration(ExmlcConfigurationBean exmlcConfigurationBean, ExmlConfiguration exmlConfiguration, boolean forTests) {
    exmlConfiguration.setOutputDirectory(new File(toPath(forTests ? exmlcConfigurationBean.getGeneratedTestSourcesDirectory() : exmlcConfigurationBean.getGeneratedSourcesDirectory())));
    exmlConfiguration.setResourceOutputDirectory(new File(toPath(exmlcConfigurationBean.getGeneratedResourcesDirectory())));
    exmlConfiguration.setConfigClassPackage(exmlcConfigurationBean.getConfigClassPackage());
    exmlConfiguration.setValidationMode(exmlcConfigurationBean.validationMode);
  }

  public static FilePosition extractFilePosition(@NotNull ExmlcException e) {
    ExmlcException result = e;
    for (Throwable current = e; current instanceof ExmlcException; current = current.getCause()) {
      ExmlcException exmlcException = (ExmlcException)current;
      if (exmlcException.getFile() != null && result.getFile() == null) {
        result = exmlcException;
      }
      if (exmlcException.getLine() > 0) {
        result = exmlcException;
        break;
      }
    }
    if (result.getLine() <= 0 && result.getCause() instanceof SAXParseException) {
      // Exmlc forgot to transfer line and column from SAXParseException! :-(
      SAXParseException saxParseException = (SAXParseException)result.getCause();
      result.setLine(saxParseException.getLineNumber());
      result.setColumn(saxParseException.getColumnNumber());
    }
    return result;
  }

  @Override
  public List<String> getCompilableFileExtensions() {
    return Arrays.asList(Exmlc.EXML_SUFFIX.substring(1));
  }

  @Override
  public ExitCode build(CompileContext context, ModuleChunk chunk,
                        DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dirtyFilesHolder,
                        OutputConsumer outputConsumer) throws ProjectBuildException, IOException {
    final Map<JpsModule, Map<Boolean, List<File>>> filesToCompile = JangarooBuilder.getFilesToCompile(
      EXMLC_SOURCES_FILTER, dirtyFilesHolder);

    if (!filesToCompile.isEmpty()) {
      for (ModuleBuildTarget moduleBuildTarget : chunk.getTargets()) {
        JpsModule module = moduleBuildTarget.getModule();
        JpsSdk sdk = module.getSdk(JpsJangarooSdkType.INSTANCE);
        if (sdk == null) {
          context.processMessage(new CompilerMessage(EXML_BUILDER_NAME, BuildMessage.Kind.WARNING,
            String.format("Jangaroo module %s does not have a Jangaroo SDK.", module.getName())));
          continue;
        }
        List<String> jarPaths = JpsJangarooSdkType.getSdkJarPaths(sdk);
        ExmlConfiguration exmlcConfiguration = getExmlcConfiguration(module, filesToCompile.get(module), false);
        if (exmlcConfiguration != null) {
          exmlcConfiguration.setLog(new JpsCompileLog(EXML_BUILDER_NAME, context));
          Exmlc exmlc = loadExmlc(context, jarPaths, exmlcConfiguration);
          compile(moduleBuildTarget, exmlc, context, outputConsumer);
        }
      }
      return ExitCode.OK;
    }

    return ExitCode.NOTHING_DONE;
  }

  private void compile(ModuleBuildTarget moduleBuildTarget, Exmlc exmlc, MessageHandler messageHandler,
                       OutputConsumer outputConsumer) throws IOException {
    List<File> sourceFiles = exmlc.getConfig().getSourceFiles();
    if (!sourceFiles.isEmpty()) {
      ArrayList<String> allCompiledSourceFiles = new ArrayList<String>();
      for (File sourceFile : sourceFiles) {
        try {
          File generatedConfigClass = exmlc.generateConfigClass(sourceFile);
          File generatedTargetClass = exmlc.generateComponentClass(sourceFile);
          // getLog().info("exml->as (config): " + fileUrl + " -> " + generatedConfigClass.getPath());
          outputConsumer.registerOutputFile(moduleBuildTarget, generatedConfigClass, Collections.singleton(sourceFile.getPath()));
          if (generatedTargetClass != null) {
            // getLog().info("exml->as (target): " + fileUrl + " -> " + generatedTargetClass.getPath());
            outputConsumer.registerOutputFile(moduleBuildTarget, generatedTargetClass, Collections.singleton(sourceFile.getPath()));
          }
          allCompiledSourceFiles.add(sourceFile.getPath());
        } catch (ExmlcException e) {
          processExmlcException(messageHandler, sourceFile, e);
        }
      }
      try {
        File xsdFile = exmlc.generateXsd();
        if (xsdFile == null || exmlc.getConfig().getLog().hasErrors()) {
          return;
        }
        outputConsumer.registerOutputFile(moduleBuildTarget, xsdFile, allCompiledSourceFiles);
      } catch (ExmlcException e) {
        processExmlcException(messageHandler, null, e);
      }
    }
  }

  private static void processExmlcException(MessageHandler messageHandler, File sourceFile, ExmlcException e) {
    FilePosition filePosition = extractFilePosition(e);
    CompilerMessage compilerMessage = filePosition.getFileName() == null
      ? sourceFile == null
        ? new CompilerMessage(EXML_BUILDER_NAME, e)
        : new CompilerMessage(EXML_BUILDER_NAME, BuildMessage.Kind.ERROR, sourceFile.getPath())
      : new CompilerMessage(EXML_BUILDER_NAME, BuildMessage.Kind.ERROR, e.getMessage(),
        filePosition.getFileName(), 0L, 0L, 0L, filePosition.getLine(), filePosition.getColumn());
    messageHandler.processMessage(compilerMessage);
  }

  protected ExmlConfiguration getExmlcConfiguration(JpsModule module, Map<Boolean, List<File>> sourceFiles, boolean forTests) {
    ExmlcConfigurationBean exmlcConfigurationBean = JangarooModelSerializerExtension.getExmlcSettings(module);
    ExmlConfiguration exmlcConfig = new ExmlConfiguration();
    JangarooBuilder.updateFileLocations(exmlcConfig, module, sourceFiles, forTests);
    copyFromBeanToConfiguration(exmlcConfigurationBean, exmlcConfig, forTests);
    return exmlcConfig;
  }

  public static Exmlc loadExmlc(MessageHandler messageHandler, List<String> jarPaths, ExmlConfiguration configuration) {
    Exmlc exmlc;
    try {
      exmlc = CompilerLoader.loadExmlc(jarPaths);
    } catch (FileNotFoundException e) {
      messageHandler.processMessage(new CompilerMessage(EXML_BUILDER_NAME, e));
      return null;
    } catch (Exception e) {
      // Jangaroo SDK not correctly set up or not compatible with this Jangaroo IDEA plugin: 
      messageHandler.processMessage(new CompilerMessage(EXML_BUILDER_NAME, e));
      return null;
    }
    exmlc.setConfig(configuration);
    return exmlc;
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return "EXML Compiler";
  }

}
