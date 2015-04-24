package net.jangaroo.ide.idea.debugger;

import com.intellij.lang.javascript.JavaScriptSupportLoader;
import com.intellij.lang.javascript.dialects.JSDialectSpecificHandlersFactory;
import com.intellij.lang.javascript.psi.ecmal4.JSClass;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Url;
import com.intellij.util.Urls;
import com.jetbrains.javascript.debugger.FileUrlMapper;
import net.jangaroo.jooc.api.Jooc;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaSourceRootType;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/**
 * Map Source Map URLs to local source files in some module.
 */
public class JangarooFileUrlMapper extends FileUrlMapper {

  public static final String AS3_SOURCE_URL_PATH_PREFIX = "/amd/as3/";

  @NotNull
  @Override
  public List<Url> getUrls(@NotNull VirtualFile sourceFile, @NotNull Project project, String authority) {
    Module module = ProjectFileIndex.SERVICE.getInstance(project).getModuleForFile(sourceFile);
    if (module != null) {
      List<VirtualFile> sourceRoots = ModuleRootManager.getInstance(module).getSourceRoots(new HashSet<JavaSourceRootType>(Arrays.asList(JavaSourceRootType.SOURCE, JavaSourceRootType.TEST_SOURCE)));
      for (VirtualFile sourceRoot : sourceRoots) {
        if (VfsUtil.isAncestor(sourceRoot, sourceFile, true)) {
          String relativePath = VfsUtil.getRelativePath(sourceFile, sourceRoot);
          return Collections.singletonList(Urls.newUrl("http", authority, AS3_SOURCE_URL_PATH_PREFIX + relativePath));
        }
      }
    }
    return Collections.emptyList();
  }

  @Nullable
  @Override
  public VirtualFile getFile(@NotNull Url sourceUrl, @NotNull Project project, Url pageUrl) {
    String path = sourceUrl.getPath();
    if (path.endsWith(Jooc.AS_SUFFIX) && path.startsWith(AS3_SOURCE_URL_PATH_PREFIX)) {
      String fullyQualifiedName = path.substring(AS3_SOURCE_URL_PATH_PREFIX.length(), path.length() - Jooc.AS_SUFFIX.length()).replace('/', '.');
      System.out.println(fullyQualifiedName);
      PsiElement asClass = JSDialectSpecificHandlersFactory.forLanguage(JavaScriptSupportLoader.ECMA_SCRIPT_L4).getClassResolver().findClassByQName(fullyQualifiedName, GlobalSearchScope.projectScope(project));
      if (asClass instanceof JSClass && asClass.isValid()) {
        return asClass.getContainingFile().getVirtualFile();
      }
    
    }
    return null;
  }
}
