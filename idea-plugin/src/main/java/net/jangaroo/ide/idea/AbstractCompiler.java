package net.jangaroo.ide.idea;

import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
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

  @NotNull
  public abstract String getDescription();

  public boolean validateConfiguration(CompileScope scope) {
    for (Module module : scope.getAffectedModules()) {
      JoocConfigurationBean joocConfigurationBean = getJoocConfigurationBean(module);
      if (joocConfigurationBean != null) {
        if (joocConfigurationBean.jangarooSdkName == null) {
          return false;
        }
        Sdk jangarooSdk = ProjectJdkTable.getInstance().findJdk(joocConfigurationBean.jangarooSdkName);
        if (jangarooSdk == null) {
          return false;
        }
      }
    }
    return true;
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
