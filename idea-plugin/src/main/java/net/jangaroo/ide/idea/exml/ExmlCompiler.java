package net.jangaroo.ide.idea.exml;

import com.intellij.compiler.impl.javaCompiler.OutputItemImpl;
import com.intellij.facet.FacetManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.compiler.IntermediateOutputCompiler;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleFileIndex;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.VirtualFile;
import net.jangaroo.exml.ExmlConstants;
import net.jangaroo.exml.ExmlcException;
import net.jangaroo.exml.compiler.Exmlc;
import net.jangaroo.exml.config.ExmlConfiguration;
import net.jangaroo.ide.idea.AbstractCompiler;
import net.jangaroo.ide.idea.util.OutputSinkItem;
import net.jangaroo.jooc.Jooc;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 *
 */
public class ExmlCompiler extends AbstractCompiler implements IntermediateOutputCompiler {

  public ExmlCompiler() {
    super();
  }

  @NotNull
  public String getDescription() {
    return "EXML Compiler";
  }

  public boolean isCompilableFile(VirtualFile file, CompileContext context) {
    if (ExmlConstants.EXML_SUFFIX.equals("." + file.getExtension())) {
      Module module = context.getModuleByFile(file);
      if (module != null && FacetManager.getInstance(module).getFacetByType(ExmlFacetType.ID) != null) {
        return true;
      }
    }
    return false;
  }

  public static ExmlcConfigurationBean getExmlConfig(Module module) {
    ExmlFacet exmlFacet = FacetManager.getInstance(module).getFacetByType(ExmlFacetType.ID);
    return exmlFacet == null ? null : exmlFacet.getConfiguration().getState();
  }

  static String getXsdFilename(Module module) {
    if (module != null) {
      ExmlcConfigurationBean exmlcConfig = getExmlConfig(module);
      if (exmlcConfig != null) {
        return exmlcConfig.getGeneratedResourcesDirectory() + "/" + exmlcConfig.getConfigClassPackage() + ".xsd";
      }
    }
    return null;
  }

  static String findDependentModuleZipFileName(OrderEntry orderEntry) throws IOException {
    VirtualFile[] files = orderEntry.getFiles(OrderRootType.CLASSES);
    // check that library is not empty:
    for (VirtualFile file : files) {
      // TODO: make it work for classes, not only for jars!
      String filename = file.getPath();
      if (filename.endsWith("!/")) { // it is a jar:
        return filename.substring(0, filename.length() - "!/".length());
      }
    }
    return null;
  }

  static ZipEntry findXsdZipEntry(ZipFile zipFile) throws IOException {
    // find a *.xsd in jar's root folder:
    Enumeration<? extends ZipEntry> enumeration = zipFile.entries();
    while (enumeration.hasMoreElements()) {
      ZipEntry zipEntry = enumeration.nextElement();
      if (!zipEntry.isDirectory() && zipEntry.getName().indexOf('/') == -1 && zipEntry.getName().endsWith(".xsd")) {
        return zipEntry;
      }
    }
    return null;
  }

  @Override
  protected String getOutputFileSuffix() {
    return Jooc.AS_SUFFIX;
  }

  @Override
  protected OutputSinkItem compile(CompileContext context, Module module, List<VirtualFile> files) {
    ExmlcConfigurationBean exmlcConfigurationBean = getExmlConfig(module);
    ExmlConfiguration exmlConfiguration = new ExmlConfiguration();
    updateFileLocations(exmlConfiguration, module, files);
    String generatedSourcesDirectory = exmlcConfigurationBean.getGeneratedSourcesDirectory();
    exmlConfiguration.setOutputDirectory(new File(generatedSourcesDirectory));
    exmlConfiguration.setResourceOutputDirectory(new File(exmlcConfigurationBean.getGeneratedResourcesDirectory()));
    exmlConfiguration.setConfigClassPackage(exmlcConfigurationBean.getConfigClassPackage());
    Exmlc exmlc = new Exmlc(exmlConfiguration);
    OutputSinkItem outputSinkItem = null;
    for (final VirtualFile file : files) {
      if (outputSinkItem == null) {
        ModuleFileIndex moduleFileIndex = ModuleRootManager.getInstance(module).getFileIndex();
        if (!moduleFileIndex.isInSourceContent(file) || moduleFileIndex.isInTestSourceContent(file)) {
          // prevent NPE in EXML generator when <file> is not under non-test source root:
          continue;
        }
        outputSinkItem = createGeneratedSourcesOutputSinkItem(context, generatedSourcesDirectory);
        File exmlSourceFile = new File(file.getPath());
        File componentClassOutputFile = null;
        File configClassOutputFile = null;
        try {
          componentClassOutputFile = exmlc.generateComponentClass(exmlSourceFile);
          // TODO: compiler errors!
          if (componentClassOutputFile != null) {
            // TODO: the next commented line raises warning in idea.log. Still needed?
            // LocalFileSystem.getInstance().refreshIoFiles(Arrays.asList(componentClassOutputFile));
            outputSinkItem.addOutputItem(file, componentClassOutputFile);

            configClassOutputFile = exmlc.generateConfigClass(exmlSourceFile);
            if (configClassOutputFile != null) {
              outputSinkItem.addOutputItem(file, configClassOutputFile);

              OutputItem outputItem = new OutputItemImpl(componentClassOutputFile.getPath().replace(File.separatorChar, '/'), file);
              if (exmlcConfigurationBean.isShowCompilerInfoMessages()) {
                context.addMessage(CompilerMessageCategory.INFORMATION, "exml->as (" + outputItem.getOutputPath() + ")", file.getUrl(), -1, -1);
              }
              getLog().info("exml->as: " + file.getUrl() + " -> " + outputItem.getOutputPath());
            }
          }
        } catch (ExmlcException e) {
          context.addMessage(CompilerMessageCategory.ERROR, e.getLocalizedMessage(), file.getUrl(), e.getLine(), e.getColumn());
        }
        if (componentClassOutputFile == null || configClassOutputFile == null) {
          //context.addMessage(CompilerMessageCategory.INFORMATION, "exml->as compilation failed.", file.getUrl(), -1, -1);
          outputSinkItem.addFileToRecompile(file);
        }
      }
    }
    return outputSinkItem;
  }

  static Logger getLog() {
    return Logger.getInstance("ExmlCompiler");
  }
}
