package net.jangaroo.ide.idea.jps.exml;

import com.intellij.flex.model.bc.JpsFlexBuildConfiguration;
import net.jangaroo.exml.api.Exmlc;
import net.jangaroo.exml.api.ExmlcException;
import net.jangaroo.exml.config.ExmlConfiguration;
import net.jangaroo.ide.idea.jps.JangarooBuildTarget;
import net.jangaroo.ide.idea.jps.JangarooBuildTargetType;
import net.jangaroo.ide.idea.jps.JangarooBuilder;
import net.jangaroo.ide.idea.jps.JangarooModelSerializerExtension;
import net.jangaroo.ide.idea.jps.JoocConfigurationBean;
import net.jangaroo.ide.idea.jps.util.CompilerLoader;
import net.jangaroo.ide.idea.jps.util.JpsCompileLog;
import net.jangaroo.jooc.api.FilePosition;
import net.jangaroo.properties.api.Propc;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.BuildOutputConsumer;
import org.jetbrains.jps.builders.BuildRootDescriptor;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.FSOperations;
import org.jetbrains.jps.incremental.MessageHandler;
import org.jetbrains.jps.incremental.ProjectBuildException;
import org.jetbrains.jps.incremental.TargetBuilder;
import org.jetbrains.jps.incremental.fs.CompilationRound;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.model.module.JpsModule;
import org.xml.sax.SAXParseException;

import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static net.jangaroo.ide.idea.jps.util.IdeaFileUtils.toPath;

/**
 * Jangaroo analog of {@link org.jetbrains.jps.incremental.java.JavaBuilder}.
 */
public class ExmlBuilder extends TargetBuilder<BuildRootDescriptor, JangarooBuildTarget> {

  private static final String EXML_BUILDER_NAME = "exmlc";
  private static final FileFilter EXMLC_SOURCES_FILTER = JangarooBuilder.createSuffixFileFilter(Exmlc.EXML_SUFFIX);

  private static final String PROPERTIES_BUILDER_NAME = "propc";
  private static final String PROPERTIES_SUFFIX = ".properties";
  private static final FileFilter PROPC_SOURCES_FILTER = JangarooBuilder.createSuffixFileFilter(PROPERTIES_SUFFIX);

  public ExmlBuilder() {
    super(Collections.singletonList(JangarooBuildTargetType.INSTANCE));
  }

  public static void copyFromBeanToConfiguration(@NotNull ExmlcConfigurationBean exmlcConfigurationBean,
                                                 @NotNull ExmlConfiguration exmlConfiguration, boolean forTests) {
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
  public void build(@NotNull JangarooBuildTarget jangarooBuildTarget, @NotNull DirtyFilesHolder<BuildRootDescriptor, JangarooBuildTarget> dirtyFilesHolder, @NotNull BuildOutputConsumer outputConsumer, @NotNull CompileContext context) throws ProjectBuildException, IOException {
    List<File> propertiesFilesToCompile = JangarooBuilder.getFilesToCompile(
      jangarooBuildTarget, PROPC_SOURCES_FILTER, dirtyFilesHolder);

    List<File> exmlFilesToCompile = JangarooBuilder.getFilesToCompile(
      jangarooBuildTarget, EXMLC_SOURCES_FILTER, dirtyFilesHolder);

    if (!propertiesFilesToCompile.isEmpty() || !exmlFilesToCompile.isEmpty()) {
        build(context, propertiesFilesToCompile, exmlFilesToCompile,
          jangarooBuildTarget, outputConsumer);
    }
  }

  /**
   * Generate AS from properties and EXML for one module.
   */
  private void build(CompileContext context, List<File> propertiesFilesToCompile,
                     List<File> exmlFilesToCompile, JangarooBuildTarget moduleBuildTarget,
                     BuildOutputConsumer outputConsumer) throws IOException {
    JpsFlexBuildConfiguration bc = moduleBuildTarget.getBC();
    JpsModule module = bc.getModule();
    ExmlConfiguration exmlcConfiguration = getExmlcConfiguration(bc, moduleBuildTarget.isTests());
    if (exmlcConfiguration == null) {
      return; // no EXML Facet in this module: skip silently!
    }
    JoocConfigurationBean joocConfigurationBean = JangarooModelSerializerExtension.getJoocSettings(module);
    if (joocConfigurationBean == null) {
      context.processMessage(new CompilerMessage(EXML_BUILDER_NAME, BuildMessage.Kind.ERROR,
        String.format("EXML module %s does not have a Jangaroo Facet.", module.getName())));
      return;
    }

    List<String> jarPaths = JangarooBuilder.getJangarooSdkJarPath(joocConfigurationBean, module);
    if (jarPaths == null) {
      return; // no Jangaroo SDK: JangarooBuilder must already have reported this problem, so skip silently!
    }

    JpsCompileLog log = new JpsCompileLog(EXML_BUILDER_NAME, context);

    exmlcConfiguration.setLog(log);

    // invoke properties compiler for module:
    if (propertiesFilesToCompile != null) {
      exmlcConfiguration.setSourceFiles(propertiesFilesToCompile);
      Propc propc = loadPropc(context, jarPaths, exmlcConfiguration);
      compileProperties(propc, context, outputConsumer);
    }

    // invoke EXML compiler for module:
    if (exmlFilesToCompile != null) {
      exmlcConfiguration.setSourceFiles(exmlFilesToCompile);
      Exmlc exmlc = loadExmlc(context, jarPaths, exmlcConfiguration);
      compileExml(moduleBuildTarget, exmlc, context, outputConsumer);
    }
  }

  private void compileProperties(Propc propc, CompileContext context,
                                 BuildOutputConsumer outputConsumer) {
    List<File> sourceFiles = propc.getConfig().getSourceFiles();
    for (File sourceFile : sourceFiles) {
      try {
        File generatedPropertiesClass = propc.generate(sourceFile);
        // getLog().info("properties->as: " + fileUrl + " -> " + generatedPropertiesClass.getPath());
        registerOutputFile(outputConsumer, context, generatedPropertiesClass,
          JangarooBuilder.toSingletonPath(sourceFile));
      } catch (IOException e) {
        context.processMessage(new CompilerMessage(PROPERTIES_BUILDER_NAME, e));
      }
    }
  }

  private void compileExml(JangarooBuildTarget moduleBuildTarget, Exmlc exmlc, CompileContext context,
                           BuildOutputConsumer outputConsumer) throws IOException {
    List<File> sourceFiles = exmlc.getConfig().getSourceFiles();
    if (!sourceFiles.isEmpty()) {
      List<String> allCompiledSourceFiles = new ArrayList<String>();
      for (File sourceFile : sourceFiles) {
        try {
          File generatedConfigClass = exmlc.generateConfigClass(sourceFile);
          registerOutputFile(outputConsumer, context, generatedConfigClass, JangarooBuilder.toSingletonPath(sourceFile));

          File generatedTargetClass = exmlc.generateComponentClass(sourceFile);
          // getLog().info("exml->as (config): " + fileUrl + " -> " + generatedConfigClass.getPath());
          if (generatedTargetClass != null) {
            // getLog().info("exml->as (target): " + fileUrl + " -> " + generatedTargetClass.getPath());
            registerOutputFile(outputConsumer, context, generatedTargetClass, JangarooBuilder.toSingletonPath(sourceFile));
          }
          allCompiledSourceFiles.add(sourceFile.getPath());
        } catch (ExmlcException e) {
          processExmlcException(context, sourceFile, e);
        }
      }
      if (!moduleBuildTarget.isTests()) {
        try {
          // The config class registry in the current ExmlConfiguration contains too many classes (also from other
          // modules), and thus cannot be reused. We have to create a clean copy.
          // TODO: remove this when bug is fixed in EXML compiler!
          ExmlConfiguration exmlConfiguration = new ExmlConfiguration();
          exmlConfiguration.setLog(exmlc.getConfig().getLog());
          exmlConfiguration.setConfigClassPackage(exmlc.getConfig().getConfigClassPackage());
          exmlConfiguration.setOutputDirectory(exmlc.getConfig().getOutputDirectory());
          exmlConfiguration.setResourceOutputDirectory(exmlc.getConfig().getResourceOutputDirectory());
          exmlConfiguration.setSourcePath(exmlc.getConfig().getSourcePath());
          exmlConfiguration.setClassPath(exmlc.getConfig().getClassPath());
          exmlc.setConfig(exmlConfiguration);

          File xsdFile = exmlc.generateXsd();
          if (xsdFile == null || !xsdFile.exists()) {
            return;
          }
          registerOutputFile(outputConsumer, context, xsdFile, allCompiledSourceFiles);
        } catch (ExmlcException e) {
          processExmlcException(context, null, e);
        }
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

  @Nullable
  protected ExmlConfiguration getExmlcConfiguration(JpsFlexBuildConfiguration bc, boolean forTests) {
    JpsModule module = bc.getModule();
    ExmlcConfigurationBean exmlcConfigurationBean = JangarooModelSerializerExtension.getExmlcSettings(module);
    if (exmlcConfigurationBean == null) {
      return null;
    }
    ExmlConfiguration exmlcConfig = new ExmlConfiguration();
    JangarooBuilder.updateFileLocations(exmlcConfig, bc, forTests, false);
    copyFromBeanToConfiguration(exmlcConfigurationBean, exmlcConfig, forTests);
    return exmlcConfig;
  }

  public static Propc loadPropc(MessageHandler messageHandler, List<String> jarPaths, ExmlConfiguration configuration) {
    Propc propc;
    try {
      propc = CompilerLoader.loadPropc(jarPaths);
    } catch (FileNotFoundException e) {
      messageHandler.processMessage(new CompilerMessage(PROPERTIES_BUILDER_NAME, e));
      return null;
    } catch (Exception e) {
      // Jangaroo SDK not correctly set up or not compatible with this Jangaroo IDEA plugin: 
      messageHandler.processMessage(new CompilerMessage(PROPERTIES_BUILDER_NAME, e));
      return null;
    }
    propc.setConfig(configuration);
    return propc;
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

  private static void registerOutputFile(BuildOutputConsumer outputConsumer,
                                         CompileContext context, File generatedFile, Collection<String> sourcePaths)
    throws IOException {
    outputConsumer.registerOutputFile(generatedFile, sourcePaths);
    FSOperations.markDirty(context, CompilationRound.CURRENT, generatedFile);
    JangarooBuilder.processFileInvalidationMessage(context, generatedFile);
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return "EXML Compiler";
  }

}
