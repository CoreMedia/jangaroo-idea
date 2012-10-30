package net.jangaroo.ide.idea;

import com.intellij.compiler.impl.CompilerUtil;
import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.facet.FacetTypeId;
import com.intellij.lang.javascript.index.JavaScriptIndex;
import com.intellij.lang.javascript.psi.ecmal4.JSClass;
import com.intellij.lang.javascript.psi.resolve.JSResolveUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.compiler.TranslatingCompiler;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleSourceOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.util.Chunk;
import net.jangaroo.ide.idea.util.OutputSinkItem;
import net.jangaroo.jooc.api.CompileLog;
import net.jangaroo.jooc.config.DebugMode;
import net.jangaroo.jooc.api.FilePosition;
import net.jangaroo.jooc.config.JoocConfiguration;
import net.jangaroo.utils.FileLocations;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An abstraction of all three Jangaroo compilers.
 */
public abstract class AbstractCompiler implements TranslatingCompiler {
  private static List<File> virtualToIoFiles(List<VirtualFile> virtualFiles) {
    List<File> ioFiles = new ArrayList<File>(virtualFiles.size());
    for (VirtualFile virtualSourceFile : virtualFiles) {
      ioFiles.add(VfsUtil.virtualToIoFile(virtualSourceFile));
    }
    return ioFiles;
  }

  static Logger getLog() {
    return Logger.getInstance("net.jangaroo.ide.idea.JangarooCompiler");
  }

  protected AbstractCompiler() {
    getLog().debug("AbstractCompiler constructor");
  }

  private static @NotNull VirtualFile getOrCreateVirtualFile(@NotNull final String path) throws IOException {
    LocalFileSystem localFileSystem = LocalFileSystem.getInstance();
    VirtualFile virtualFile = localFileSystem.findFileByPath(path);
    if (virtualFile == null) {
      File file = new File(path);
      String parent = file.getParent();
      if (parent == null) {
        throw new IOException("Path not found: " + path);
      }
      VirtualFile vfParentFolder = getOrCreateVirtualFile(parent);
      virtualFile = localFileSystem.createChildDirectory(null, vfParentFolder, file.getName());
    }
    return virtualFile;
  }

  public static List<String> getJarFileNames(String jangarooSdkName) {
    Sdk jangarooSdk = ProjectJdkTable.getInstance().findJdk(jangarooSdkName, "");
    if (jangarooSdk == null) {
      throw new IllegalStateException("Jangaroo SDK '" + jangarooSdkName + "' not found.");
    }
    VirtualFile[] files = jangarooSdk.getRootProvider().getFiles(OrderRootType.CLASSES);
    List<String> jarFileNames = new ArrayList<String>(files.length);
    for (VirtualFile file : files) {
      String filename = file.getPath();
      if (filename.endsWith("!/")) {
        filename = filename.substring(0, filename.length() - "!/".length());
      }
      jarFileNames.add(filename);
    }
    return jarFileNames;
  }

  public static JSClass getASClass(Project project, String className) {
    PsiElement asClass = JSResolveUtil.findClassByQName(className, JavaScriptIndex.getInstance(project), null);
    return asClass instanceof JSClass ? (JSClass)asClass : null;
  }

  @NotNull
  public abstract String getDescription();

  public boolean validateConfiguration(CompileScope scope) {
    // as the user does not get any feedback if we return false here, we refrain from doing so.
    return true;
  }

  protected abstract String getInputFileSuffix();

  protected abstract String getOutputFileSuffix();

  protected abstract FacetTypeId<? extends Facet> getFacetType();

  public boolean isCompilableFile(VirtualFile file, CompileContext context) {
    Module module = context.getModuleByFile(file);
    return getInputFileSuffix().equals(file.getExtension())
      && !file.getPath().contains("/joo-api/") // hack: skip all files under .../joo-api
      && (module == null || FacetManager.getInstance(module).getFacetByType(getFacetType()) != null);
  }

  public void compile(final CompileContext context, Chunk<Module> moduleChunk, final VirtualFile[] files, final OutputSink outputSink) {
    if (!validateConfiguration(context)) {
      return;
    }
    final Collection<OutputSinkItem> outputs = new ArrayList<OutputSinkItem>();
    final Map<Module, List<VirtualFile>> filesByModule = CompilerUtil.buildModuleToFilesMap(context, files);

    ApplicationManager.getApplication().runReadAction(new Runnable() {

      public void run() {
        for (Map.Entry<Module, List<VirtualFile>> filesOfModuleEntry : filesByModule.entrySet()) {
          OutputSinkItem outputSinkItem = compile(context, filesOfModuleEntry.getKey(), filesOfModuleEntry.getValue());
          if (outputSinkItem != null) {
            outputs.add(outputSinkItem);
          }
        }
      }

    });
    for (OutputSinkItem outputSinkItem : outputs) {
      outputSinkItem.addTo(outputSink);
    }
  }

  protected abstract OutputSinkItem compile(CompileContext context, Module module, List<VirtualFile> files);

  protected boolean validateConfiguration(CompileContext context) {
    Module invalidModule = findInvalidModule(context.getCompileScope());
    if (invalidModule != null) {
      String[] contentRootUrls = ModuleRootManager.getInstance(invalidModule).getContentRootUrls();
      String invalidModuleUrl = contentRootUrls.length > 0 ? contentRootUrls[0] : null;
      context.addMessage(CompilerMessageCategory.ERROR,
        "Jangaroo SDK not set up correctly. If using Maven, please run 'mvn install', then press 'Reimport All Maven Projects', otherwise correct your Jangaroo / EXML facets ('Project Structure') manually.",
        invalidModuleUrl, -1, -1);
      return false;
    }
    return true;
  }

  protected Module findInvalidModule(CompileScope scope) {
    for (Module module : scope.getAffectedModules()) {
      JoocConfigurationBean joocConfigurationBean = getJoocConfigurationBean(module);
      if (joocConfigurationBean != null) {
        if (joocConfigurationBean.jangarooSdkName == null) {
          return module;
        }
        Sdk jangarooSdk = ProjectJdkTable.getInstance().findJdk(joocConfigurationBean.jangarooSdkName);
        if (jangarooSdk == null) {
          return module;
        }
      }
    }
    return null;
  }

  protected JoocConfiguration getJoocConfiguration(Module module, List<VirtualFile> virtualSourceFiles) {
    JoocConfigurationBean joocConfigurationBean = getJoocConfigurationBean(module);
    if (joocConfigurationBean==null) {
      return null;
    }
    JoocConfiguration joocConfig = new JoocConfiguration();
    joocConfig.setVerbose(joocConfigurationBean.verbose);
    joocConfig.setDebugMode(joocConfigurationBean.isDebug() ? joocConfigurationBean.isDebugSource() ? DebugMode.SOURCE : DebugMode.LINES : null);
    joocConfig.setAllowDuplicateLocalVariables(joocConfigurationBean.allowDuplicateLocalVariables);
    joocConfig.setEnableAssertions(joocConfigurationBean.enableAssertions);
    joocConfig.setApiOutputDirectory(joocConfigurationBean.getApiOutputDirectory());
    updateFileLocations(joocConfig, module, virtualSourceFiles);
    joocConfig.setMergeOutput(false); // no longer supported: joocConfigurationBean.mergeOutput;
    joocConfig.setOutputDirectory(joocConfigurationBean.getOutputDirectory());
    //joocConfig.showCompilerInfoMessages = joocConfigurationBean.showCompilerInfoMessages;
    joocConfig.setPublicApiViolationsMode(joocConfigurationBean.publicApiViolationsMode);
    return joocConfig;
  }

  protected static @Nullable JoocConfigurationBean getJoocConfigurationBean(Module module) {
    JangarooFacet jangarooFacet = JangarooFacet.ofModule(module);
    return jangarooFacet==null ? null : jangarooFacet.getConfiguration().getState();
  }

  protected void updateFileLocations(FileLocations fileLocations, Module module, List<VirtualFile> virtualSourceFiles) {
    Collection<File> classPath = new LinkedHashSet<File>();
    Collection<File> sourcePath = new LinkedHashSet<File>();
    addToClassOrSourcePath(module, classPath, sourcePath);
    fileLocations.setClassPath(new ArrayList<File>(classPath));
    try {
      fileLocations.setSourcePath(new ArrayList<File>(sourcePath));
    } catch (IOException e) {
      getLog().error("while constructing Jangaroo source path", e);
    }
    List<File> sourceFiles = virtualToIoFiles(virtualSourceFiles);
    fileLocations.setSourceFiles(sourceFiles);
  }

  private void addToClassOrSourcePath(Module module, Collection<File> classPath, Collection<File> sourcePath) {
    for (OrderEntry orderEntry : ModuleRootManager.getInstance(module).getOrderEntries()) {
      if (orderEntry instanceof ModuleSourceOrderEntry) {
        // TODO: to filter out test sources, we could use moduleRootManager.getFileIndex().isInTestSourceContent(<virtualFile>)
        sourcePath.addAll(virtualToIoFiles(Arrays.asList(((ModuleSourceOrderEntry)orderEntry).getRootModel().getSourceRoots())));
      } else if (orderEntry instanceof LibraryOrderEntry) {
        classPath.addAll(virtualToIoFiles(Arrays.asList(((LibraryOrderEntry)orderEntry).getRootFiles(OrderRootType.CLASSES_AND_OUTPUT))));
      } else if (orderEntry instanceof ModuleOrderEntry) {
        Module dependentModule = ((ModuleOrderEntry)orderEntry).getModule();
        if (dependentModule != null) {
          addToClassOrSourcePath(dependentModule, classPath, sourcePath);
        }
      }
    }
  }

  protected OutputSinkItem createGeneratedSourcesOutputSinkItem(CompileContext context, String generatedSourcesDirectory) {
    try {
      String generatedAs3RootDir = getOrCreateVirtualFile(generatedSourcesDirectory).getPath();
      return new OutputSinkItem(generatedAs3RootDir);
    } catch (IOException e) {
      context.addMessage(CompilerMessageCategory.ERROR, "Target directory could not be created: " + generatedSourcesDirectory, null, -1, -1);
      getLog().warn("Jangaroo: Generated sources target directory could not be created.", e);
      return null;
    }
  }

  protected static class IdeaCompileLog implements CompileLog {
    private CompileContext compileContext;
    private Set<VirtualFile> filesWithErrors;

    protected IdeaCompileLog(CompileContext compileContext) {
      this.compileContext = compileContext;
      filesWithErrors = new HashSet<VirtualFile>();
    }

    private VirtualFile addMessage(CompilerMessageCategory compilerMessageCategory, String msg, FilePosition position) {
      VirtualFile file = LocalFileSystem.getInstance().findFileByPath(position.getFileName());
      String fileUrl = file==null ? null : file.getUrl();
      compileContext.addMessage(compilerMessageCategory, msg, fileUrl, position.getLine(), position.getColumn()-1);
      return file;
    }

    public void error(FilePosition position, String msg) {
      filesWithErrors.add(addMessage(CompilerMessageCategory.ERROR, msg, position));
      getLog().debug(position.getFileName() + ": Jangaroo Compile Error: " + msg);
    }

    public void error(String msg) {
      compileContext.addMessage(CompilerMessageCategory.ERROR, msg, null, -1, -1);
      getLog().debug("Jangaroo Compile Error: " + msg);
    }

    public void warning(FilePosition position, String msg) {
      addMessage(CompilerMessageCategory.WARNING, msg, position);
      getLog().debug(position.getFileName() + ": Jangaroo Compile Warning: " + msg);
    }

    public void warning(String msg) {
      compileContext.addMessage(CompilerMessageCategory.WARNING, msg, null, -1, -1);
      getLog().debug("Jangaroo Compile Warning: " + msg);
    }

    public boolean hasErrors() {
      return compileContext.getMessageCount(CompilerMessageCategory.ERROR)>0;
    }

    public boolean hasErrors(VirtualFile file) {
      return filesWithErrors.contains(file);
    }
  }
}
