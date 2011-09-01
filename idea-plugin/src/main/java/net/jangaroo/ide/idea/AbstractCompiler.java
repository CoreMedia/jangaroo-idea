package net.jangaroo.ide.idea;

import com.intellij.compiler.make.MakeUtil;
import com.intellij.facet.FacetManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleSourceOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import net.jangaroo.ide.idea.util.OutputSinkItem;
import net.jangaroo.jooc.CompileLog;
import net.jangaroo.jooc.JooSymbol;
import net.jangaroo.jooc.config.DebugMode;
import net.jangaroo.jooc.config.JoocConfiguration;
import net.jangaroo.utils.FileLocations;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by IntelliJ IDEA. User: fwienber Date: 23.08.11 Time: 14:12 To change this template use File | Settings |
 * File Templates.
 */
public abstract class AbstractCompiler implements com.intellij.openapi.compiler.Compiler {
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
      VirtualFile vfParentFolder = getOrCreateVirtualFile(file.getParent());
      virtualFile = localFileSystem.createChildDirectory(null, vfParentFolder, file.getName());
    }
    return virtualFile;
  }

  @NotNull
  public abstract String getDescription();

  public boolean validateConfiguration(CompileScope scope) {
    return true;
  }

  protected JoocConfiguration getJoocConfiguration(Module module, List<VirtualFile> virtualSourceFiles) {
    JangarooFacet jangarooFacet = FacetManager.getInstance(module).getFacetByType(JangarooFacetType.ID);
    if (jangarooFacet==null) {
      return null;
    }
    JoocConfigurationBean joocConfigurationBean = jangarooFacet.getConfiguration().getState();
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
    return joocConfig;
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

  protected @NotNull File computeOutputFile(CompileContext context, Module module, final String outputDirectory, final VirtualFile file) {
    VirtualFile sourceRoot = MakeUtil.getSourceRoot(context, module, file);
    if (sourceRoot==null) {
      throw new IllegalStateException("File not under any source root: '" + file.getPath() + "'.");
    }
    String filePath = file.getPath();
    String relativePath = filePath.substring(sourceRoot.getPath().length(), filePath.lastIndexOf('.'));
    String outputFilePath = outputDirectory + relativePath + getOutputFileSuffix();
    return new File(outputFilePath);
  }

  protected abstract String getOutputFileSuffix();

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

    private VirtualFile addMessage(CompilerMessageCategory compilerMessageCategory, String msg, JooSymbol sym) {
      VirtualFile file = LocalFileSystem.getInstance().findFileByPath(sym.getFileName());
      String fileUrl = file==null ? null : file.getUrl();
      compileContext.addMessage(compilerMessageCategory, msg, fileUrl, sym.getLine(), sym.getColumn()-1);
      return file;
    }

    public void error(JooSymbol sym, String msg) {
      filesWithErrors.add(addMessage(CompilerMessageCategory.ERROR, msg, sym));
      getLog().debug(sym.getFileName() + ": Jangaroo Compile Error: " + msg);
    }

    public void error(String msg) {
      compileContext.addMessage(CompilerMessageCategory.ERROR, msg, null, -1, -1);
      getLog().debug("Jangaroo Compile Error: " + msg);
    }

    public void warning(JooSymbol sym, String msg) {
      addMessage(CompilerMessageCategory.WARNING, msg, sym);
      getLog().debug(sym.getFileName() + ": Jangaroo Compile Warning: " + msg);
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
