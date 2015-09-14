package net.jangaroo.ide.idea;

import com.intellij.lang.javascript.JavaScriptSupportLoader;
import com.intellij.lang.javascript.dialects.JSDialectSpecificHandlersFactory;
import com.intellij.lang.javascript.psi.ecmal4.JSClass;
import com.intellij.lang.javascript.psi.resolve.JSClassResolver;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaSourceRootType;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/**
 * Common static utility methods.
 */
public class Utils {

  public static String getModuleRelativeSourcePath(Project project, VirtualFile file, char separator) {
    final Module module = getModuleForFile(project, file);
    if (module != null) {
      List<VirtualFile> sourceRoots = ModuleRootManager.getInstance(module).getSourceRoots(new HashSet<JavaSourceRootType>(Arrays.asList(JavaSourceRootType.SOURCE, JavaSourceRootType.TEST_SOURCE)));
      for (VirtualFile sourceRoot : sourceRoots) {
        if (VfsUtil.isAncestor(sourceRoot, file, false)) {
          return VfsUtil.getRelativePath(file, sourceRoot, separator);
        }
      }
    }
    return "";
  }

  public static Module getModuleForFile(Project project, VirtualFile file) {
    return ProjectRootManager.getInstance(project).getFileIndex().getModuleForFile(file);
  }

  @NotNull
  public static JSClassResolver getActionScriptClassResolver() {
    return JSDialectSpecificHandlersFactory.forLanguage(JavaScriptSupportLoader.ECMA_SCRIPT_L4).getClassResolver();
  }

  public static boolean isValidActionScriptClass(PsiElement asClass) {
    return asClass instanceof JSClass && asClass.isValid();
  }

  public static JSClass getActionScriptClass(PsiElement context, String className) {
    PsiElement asClass = getActionScriptClassResolver().findClassByQName(className, context);
    return isValidActionScriptClass(asClass) ? (JSClass)asClass : null;
  }

  public static VirtualFile getActionScriptFile(@NotNull Project project, String fullyQualifiedName) {
    PsiElement asClass = getActionScriptClassResolver().findClassByQName(fullyQualifiedName, GlobalSearchScope.projectScope(project));
    return isValidActionScriptClass(asClass) ? asClass.getContainingFile().getVirtualFile() : null;
  }
}
