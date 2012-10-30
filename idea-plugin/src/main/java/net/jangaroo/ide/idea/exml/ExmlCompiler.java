package net.jangaroo.ide.idea.exml;

import com.intellij.facet.FacetManager;
import com.intellij.facet.FacetTypeId;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.compiler.IntermediateOutputCompiler;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import net.jangaroo.exml.api.Exmlc;
import net.jangaroo.exml.api.ExmlcException;
import net.jangaroo.exml.config.ExmlConfiguration;
import net.jangaroo.ide.idea.AbstractCompiler;
import net.jangaroo.ide.idea.JoocConfigurationBean;
import net.jangaroo.ide.idea.util.CompilerLoader;
import net.jangaroo.ide.idea.util.OutputSinkItem;
import net.jangaroo.jooc.api.Jooc;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static net.jangaroo.ide.idea.util.IdeaFileUtils.toPath;

/**
 * An IDEA wrapper for Jangaroo's EXML compiler "exmlc".
 */
public class ExmlCompiler extends AbstractCompiler implements IntermediateOutputCompiler {

  public ExmlCompiler() {
    super();
  }

  @NotNull
  public String getDescription() {
    return "EXML Compiler";
  }

  public static ExmlcConfigurationBean getExmlConfig(Module module) {
    if (module != null) {
      ExmlFacet exmlFacet = FacetManager.getInstance(module).getFacetByType(ExmlFacetType.ID);
      if (exmlFacet != null) {
        return exmlFacet.getConfiguration().getState();
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

  static Set<ZipEntry> findXsdZipEntries(ZipFile zipFile) throws IOException {
    // find a *.xsd in jar's root folder:
    Enumeration<? extends ZipEntry> enumeration = zipFile.entries();
    Set<ZipEntry> result = new LinkedHashSet<ZipEntry>();
    while (enumeration.hasMoreElements()) {
      ZipEntry zipEntry = enumeration.nextElement();
      if (!zipEntry.isDirectory() && zipEntry.getName().indexOf('/') == -1 && zipEntry.getName().endsWith(".xsd")) {
        result.add(zipEntry);
      }
    }
    return result;
  }

  @Override
  protected String getInputFileSuffix() {
    return Exmlc.EXML_SUFFIX.substring(1);
  }

  @Override
  protected String getOutputFileSuffix() {
    return Jooc.AS_SUFFIX;
  }

  protected FacetTypeId<ExmlFacet> getFacetType() {
    return ExmlFacetType.ID;
  }

  @Override
  protected OutputSinkItem compile(CompileContext context, Module module, List<VirtualFile> files) {
    ExmlcConfigurationBean exmlcConfigurationBean = getExmlConfig(module);
    JoocConfigurationBean joocConfigurationBean = getJoocConfigurationBean(module);
    ExmlConfiguration exmlConfiguration = new ExmlConfiguration();
    updateFileLocations(exmlConfiguration, module, files);
    copyFromBeanToConfiguration(exmlcConfigurationBean, exmlConfiguration);
    Exmlc exmlc = getExmlc(joocConfigurationBean.jangarooSdkName, exmlConfiguration, context);
    if (exmlc == null) {
      return null;
    }
    if (!files.isEmpty()) {
      OutputSinkItem outputSinkItem = createGeneratedSourcesOutputSinkItem(context, exmlConfiguration.getOutputDirectory().getPath());
      for (VirtualFile virtualSourceFile : files) {
        File sourceFile = VfsUtil.virtualToIoFile(virtualSourceFile);
        try {
          File generatedConfigClass = exmlc.generateConfigClass(sourceFile);
          File generatedTargetClass = exmlc.generateComponentClass(sourceFile);
          String fileUrl = virtualSourceFile.getUrl();
          getLog().info("exml->as (config): " + fileUrl + " -> " + generatedConfigClass.getPath());
          if (generatedTargetClass == null) {
            outputSinkItem.addOutputItem(virtualSourceFile, generatedConfigClass);
          } else {
            getLog().info("exml->as (target): " + fileUrl + " -> " + generatedTargetClass.getPath());
            outputSinkItem.addOutputItem(virtualSourceFile, generatedTargetClass);
            outputSinkItem.addFileToRefresh(generatedConfigClass);
          }
        } catch (ExmlcException e) {
          outputSinkItem.addFileToRecompile(virtualSourceFile);
          addMessageForExmlcException(context, e);
        }
      }
      // if any EXML file has been changed, try re-generate this module's XSD:
      try {
        File generatedXsd = exmlc.generateXsd();
        getLog().info("exml->xsd: " + generatedXsd.getPath());
        outputSinkItem.addFileToRefresh(generatedXsd.getParentFile()); // refresh complete directory for other XSD files!
      } catch (ExmlcException e) {
        ExmlCompiler.addMessageForExmlcException(context, e);
      }
      return outputSinkItem;
    }
    return null;
  }

  private static void addMessageForExmlcException(CompileContext context, ExmlcException e) {
    VirtualFile file = e.getFile() == null ? null : LocalFileSystem.getInstance().findFileByIoFile(e.getFile());
    context.addMessage(CompilerMessageCategory.ERROR, e.getLocalizedMessage(), file==null ? null : file.getUrl(), e.getLine(), e.getColumn());
  }

  private Exmlc getExmlc(String jangarooSdkName, ExmlConfiguration exmlConfiguration, CompileContext context) {
    Exmlc exmlc = null;
    try {
      exmlc = CompilerLoader.loadExmlc(getJarFileNames(jangarooSdkName));
      exmlc.setConfig(exmlConfiguration);
    } catch (FileNotFoundException e) {
      context.addMessage(CompilerMessageCategory.ERROR, e.getMessage(), null, -1, -1);
    } catch (Exception e) {
      context.addMessage(CompilerMessageCategory.ERROR, jangarooSdkName +
        " not correctly set up or not compatible with this Jangaroo IDEA plugin: " + e.getMessage(),
        null, -1, -1);
    }
    return exmlc;
  }

  private static Logger getLog() {
    return Logger.getInstance("ExmlCompiler");
  }

  private static void copyFromBeanToConfiguration(ExmlcConfigurationBean exmlcConfigurationBean, ExmlConfiguration exmlConfiguration) {
    exmlConfiguration.setOutputDirectory(new File(toPath(exmlcConfigurationBean.getGeneratedSourcesDirectory())));
    exmlConfiguration.setResourceOutputDirectory(new File(toPath(exmlcConfigurationBean.getGeneratedResourcesDirectory())));
    exmlConfiguration.setConfigClassPackage(exmlcConfigurationBean.getConfigClassPackage());
  }

}
