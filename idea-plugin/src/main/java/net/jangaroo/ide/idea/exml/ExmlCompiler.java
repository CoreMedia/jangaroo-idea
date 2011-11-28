package net.jangaroo.ide.idea.exml;

import com.intellij.compiler.impl.CompilerUtil;
import com.intellij.compiler.make.MakeUtil;
import com.intellij.facet.FacetManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.compiler.SourceGeneratingCompiler;
import com.intellij.openapi.compiler.ValidityState;
import com.intellij.openapi.compiler.ex.CompileContextEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import net.jangaroo.exml.api.Exmlc;
import net.jangaroo.exml.api.ExmlcException;
import net.jangaroo.exml.config.ExmlConfiguration;
import net.jangaroo.ide.idea.AbstractCompiler;
import net.jangaroo.ide.idea.JangarooCompiler;
import net.jangaroo.ide.idea.JoocConfigurationBean;
import net.jangaroo.ide.idea.util.CompilerLoader;
import net.jangaroo.properties.api.Propc;
import net.jangaroo.properties.api.PropcHelper;
import net.jangaroo.utils.CompilerUtils;
import net.jangaroo.utils.FileLocations;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static net.jangaroo.ide.idea.util.IdeaFileUtils.toPath;

/**
 *
 */
public class ExmlCompiler extends AbstractCompiler implements SourceGeneratingCompiler {
  private static final GenerationItem[] EMPTY_GENERATION_ITEM_ARRAY = {};

  public ExmlCompiler() {
    super();
  }

  @NotNull
  public String getDescription() {
    return "EXML Compiler";
  }

  static boolean hasExmlFacet(Module module) {
    return ExmlFacet.ofModule(module) != null;
  }

  public static ExmlcConfigurationBean getExmlConfig(Module module) {
    ExmlFacet exmlFacet = FacetManager.getInstance(module).getFacetByType(ExmlFacetType.ID);
    return exmlFacet == null ? null : exmlFacet.getConfiguration().getState();
  }

  static String getXsdFilename(Module module) {
    if (module != null) {
      ExmlcConfigurationBean exmlcConfig = getExmlConfig(module);
      if (exmlcConfig != null) {
        return exmlcConfig.getXsdFilename();
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

  public VirtualFile getPresentableFile(CompileContext context, Module module, VirtualFile outputRoot, VirtualFile generatedFile) {
    return null; // TODO: return EXML/properties file for AS compile errors? Why is this not called?
  }

  public ValidityState createValidityState(DataInput in) throws IOException {
    return null;
  }

  public GenerationItem[] getGenerationItems(CompileContext context) {
    return ApplicationManager.getApplication().runReadAction(new PrepareAction(context));
  }

  public GenerationItem[] generate(CompileContext context, GenerationItem[] items, VirtualFile outputRootDirectory) {
    if (items.length == 0) {
      return EMPTY_GENERATION_ITEM_ARRAY;
    }
    Module module = items[0].getModule(); // TODO: careful: do we ever get items from different modules? Then this won't work!
    ExmlcConfigurationBean exmlcConfigurationBean = getExmlConfig(module);
    JoocConfigurationBean joocConfigurationBean = JangarooCompiler.getJoocConfigurationBean(module);
    ExmlConfiguration exmlConfiguration = new ExmlConfiguration();
    List<VirtualFile> files = new ArrayList<VirtualFile>(items.length);
    for (GenerationItem item : items) {
      files.add(((JooGenerationItem)item).getSourceFile());
    }
    updateFileLocations(exmlConfiguration, module, files);
    String generatedSourcesDirectory = exmlcConfigurationBean.getGeneratedSourcesDirectory();
    exmlConfiguration.setOutputDirectory(new File(toPath(generatedSourcesDirectory)));
    exmlConfiguration.setResourceOutputDirectory(new File(exmlcConfigurationBean.getGeneratedResourcesDirectory()));
    exmlConfiguration.setConfigClassPackage(exmlcConfigurationBean.getConfigClassPackage());
    List<GenerationItem> successfullyGeneratedItems = new ArrayList<GenerationItem>(items.length);
    Exmlc exmlc = getExmlc(joocConfigurationBean.jangarooSdkName,
      exmlConfiguration, context);
    if (exmlc == null) {
      return new GenerationItem[0];
    }
    Propc propertyClassGenerator = getPropc(joocConfigurationBean.jangarooSdkName, exmlConfiguration, context);
    for (GenerationItem generationItem : items) {
      JooGenerationItem jooGenerationItem = (JooGenerationItem)generationItem;
      VirtualFile virtualSourceFile = jooGenerationItem.getSourceFile();
      File sourceFile = VfsUtil.virtualToIoFile(virtualSourceFile);
      try {
        switch (jooGenerationItem.getType()) {
          case CONFIG: exmlc.generateConfigClass(sourceFile); break;
          case COMPONENT: exmlc.generateComponentClass(sourceFile); break;
          case PROPERTIES: propertyClassGenerator.generate(sourceFile); break;
        }
        successfullyGeneratedItems.add(generationItem);
      } catch (ExmlcException e) {
        addMessageForExmlcException(context, e);
      }
    }
    for (GenerationItem item : successfullyGeneratedItems) {
      CompilerUtil.refreshIOFile(new File(exmlConfiguration.getOutputDirectory(), item.getPath()));
    }
    if (context instanceof CompileContextEx) {
      List<VirtualFile> generatedVFiles = new ArrayList<VirtualFile>();
      for (GenerationItem item : successfullyGeneratedItems) {
        VirtualFile generatedVFile = LocalFileSystem.getInstance().findFileByPath(exmlConfiguration.getOutputDirectory().getPath() + "/" + item.getPath());
        if (generatedVFile != null) {
          generatedVFiles.add(generatedVFile);
        }
      }
      ((CompileContextEx)context).markGenerated(generatedVFiles);
    }
    return successfullyGeneratedItems.toArray(new GenerationItem[successfullyGeneratedItems.size()]);
  }

  static void addMessageForExmlcException(CompileContext context, ExmlcException e) {
    VirtualFile file = LocalFileSystem.getInstance().findFileByIoFile(e.getFile());
    context.addMessage(CompilerMessageCategory.ERROR, e.getLocalizedMessage(), file==null ? null : file.getUrl(), e.getLine(), e.getColumn());
  }

  Exmlc getExmlc(String jangarooSdkName, ExmlConfiguration exmlConfiguration, CompileContext context) {
    Exmlc exmlc = null;
    String joocJarFileName = JangarooCompiler.findCompilerJar(jangarooSdkName, "jangaroo-compiler");
    String exmlcJarFileName = JangarooCompiler.findCompilerJar(jangarooSdkName, "exml-compiler");
    try {
      exmlc = CompilerLoader.loadExmlc(toPath(exmlcJarFileName), toPath(joocJarFileName));
      exmlc.setConfig(exmlConfiguration);
    } catch (FileNotFoundException e) {
      context.addMessage(CompilerMessageCategory.ERROR, e.getMessage(), null, -1, -1);
    } catch (Exception e) {
      context.addMessage(CompilerMessageCategory.ERROR, "EXML Compiler version " +
        exmlcJarFileName + " not compatible with this Jangaroo IDEA plugin: " + e.getMessage(),
        null, -1, -1);
    }
    return exmlc;
  }

  private Propc getPropc(String jangarooSdkName, FileLocations compilerConfiguration, CompileContext context) {
    Propc propc = null;
    String propertiesCompilerJarFileName = JangarooCompiler.findCompilerJar(jangarooSdkName, "properties-compiler");
    try {
      propc = CompilerLoader.loadPropc(propertiesCompilerJarFileName);
      propc.setConfig(compilerConfiguration);
    } catch (FileNotFoundException e) {
      context.addMessage(CompilerMessageCategory.ERROR, e.getMessage(), null, -1, -1);
    } catch (Exception e) {
      context.addMessage(CompilerMessageCategory.ERROR, "Properties Compiler version " +
        propertiesCompilerJarFileName + " not compatible with this Jangaroo IDEA plugin: " + e.getMessage(),
        null, -1, -1);
    }
    return propc;
  }

  static Logger getLog() {
    return Logger.getInstance("ExmlCompiler");
  }

  protected List<VirtualFile> getCompilableFiles(CompileContext context) {
    FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    VirtualFile[] files = context.getProjectCompileScope().getFiles(fileTypeManager.getFileTypeByExtension("xml"), true);
    List<VirtualFile> compilableFiles = new ArrayList<VirtualFile>(files.length);
    for (VirtualFile file : files) {
      if (Exmlc.EXML_SUFFIX.equals("." + file.getExtension()) && hasExmlFacet(context.getModuleByFile(file))) {
        compilableFiles.add(file);
      }
    }
    VirtualFile[] propertiesFiles = context.getProjectCompileScope().getFiles(fileTypeManager.getFileTypeByExtension("properties"), true);
    for (VirtualFile file : propertiesFiles) {
      Module module = context.getModuleByFile(file);
      if (hasExmlFacet(module)) {
        VirtualFile sourceRoot = MakeUtil.getSourceRoot(context, module, file);
        if (!(sourceRoot != null && sourceRoot.getPath().endsWith("/webapp"))) { // hack: skip all files under .../webapp
          compilableFiles.add(file);
        }
      }
    }
    return compilableFiles;
  }

  private final class PrepareAction implements Computable<GenerationItem[]> {
    private final CompileContext context;

    public PrepareAction(CompileContext context) {
      this.context = context;
    }

    public GenerationItem[] compute() {
      if (context.getProject().isDisposed()) {
        return EMPTY_GENERATION_ITEM_ARRAY;
      }
      List<VirtualFile> compilableFiles = getCompilableFiles(context);
      List<GenerationItem> items = new ArrayList<GenerationItem>(compilableFiles.size());
      final Map<Module, List<VirtualFile>> filesByModule = CompilerUtil.buildModuleToFilesMap(context, compilableFiles.toArray(new VirtualFile[compilableFiles.size()]));
      for (Map.Entry<Module,List<VirtualFile>> entry : filesByModule.entrySet()) {
        Module module = entry.getKey();
        ExmlcConfigurationBean exmlcConfigurationBean = getExmlConfig(module);
        ExmlConfiguration exmlConfiguration = new ExmlConfiguration();
        updateFileLocations(exmlConfiguration, module, entry.getValue());
        String generatedSourcesDirectory = exmlcConfigurationBean.getGeneratedSourcesDirectory();
        exmlConfiguration.setOutputDirectory(new File(toPath(generatedSourcesDirectory)));
        exmlConfiguration.setResourceOutputDirectory(new File(toPath(exmlcConfigurationBean.getGeneratedResourcesDirectory())));
        exmlConfiguration.setConfigClassPackage(exmlcConfigurationBean.getConfigClassPackage());
        for (VirtualFile file : entry.getValue()) {
          try {
            File ioFile = VfsUtil.virtualToIoFile(file);
            if ("properties".equals(file.getExtension())) {
              File generatedPropertiesClassFile = PropcHelper.computeGeneratedPropertiesClassFile(exmlConfiguration, ioFile);
              addItem(file, generatedPropertiesClassFile, JooGenerationItemType.PROPERTIES, module, exmlConfiguration, items);
            } else {
              File generatedConfigClassFile = exmlConfiguration.computeGeneratedConfigClassFile(ioFile);
              addItem(file, generatedConfigClassFile, JooGenerationItemType.CONFIG, module, exmlConfiguration, items);
  
              File generatedComponentClassFile = exmlConfiguration.computeGeneratedComponentClassFile(ioFile);
              addItem(file, generatedComponentClassFile, JooGenerationItemType.COMPONENT, module, exmlConfiguration, items);
            }
          } catch (IOException e) {
            e.printStackTrace();
            // TODO
          }
        }
      }
      return items.toArray(new GenerationItem[items.size()]);
    }

    private void addItem(VirtualFile file,
                         File generatedFile, JooGenerationItemType type, Module module, ExmlConfiguration exmlConfiguration,
                         List<GenerationItem> items) throws IOException {
      ProjectFileIndex fileIndex = ProjectRootManager.getInstance(module.getProject()).getFileIndex();
      String generatedFilePath = CompilerUtils.getRelativePath(exmlConfiguration.getOutputDirectory(), generatedFile).replace(File.separatorChar, '/');
      JooGenerationItem generationItem =
        new JooGenerationItem(module, file, type, fileIndex.isInTestSourceContent(file), generatedFilePath);
      if (context.isMake()) {
        if (generatedFile == null || !generatedFile.exists() || generatedFile.lastModified() <= file.getModificationCount()) {
          // TODO: createSourceRootIfNotExist(sourceRootPath, module);
          items.add(generationItem);
        }
      } else {
        // TODO: createSourceRootIfNotExist(sourceRootPath, module);
        items.add(generationItem);
      }
    }
  }

  private enum JooGenerationItemType {
    PROPERTIES, CONFIG, COMPONENT
  }

  private final static class JooGenerationItem implements GenerationItem {
    private final Module module;
    private final VirtualFile file;
    private final boolean testSource;
    private final String generatedFilePath;
    private final JooGenerationItemType type;

    public JooGenerationItem(@NotNull Module module,
                             @NotNull VirtualFile file,
                             JooGenerationItemType type,
                             boolean testSource,
                             @NotNull String generatedFilePath) {
      this.module = module;
      this.file = file;
      this.type = type;
      this.testSource = testSource;
      this.generatedFilePath = generatedFilePath;
    }

    public VirtualFile getSourceFile() {
      return file;
    }

    @Nullable
    public String getPath() {
      return generatedFilePath;
    }

    public JooGenerationItemType getType() {
      return type;
    }

    @Nullable
    public ValidityState getValidityState() {
      return null;
    }

    public Module getModule() {
      return module;
    }

    public boolean isTestSource() {
      return testSource;
    }
  }
  
}
