package net.jangaroo.ide.idea.exml;

import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.lang.javascript.JavascriptLanguage;
import com.intellij.lang.javascript.psi.JSFile;
import com.intellij.lang.javascript.psi.JSFunction;
import com.intellij.lang.javascript.psi.JSSourceElement;
import com.intellij.lang.javascript.psi.JSVariable;
import com.intellij.lang.javascript.psi.ecmal4.JSPackageStatement;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.PropertiesReferenceManager;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import net.jangaroo.utils.CompilerUtils;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author Frank Wienberg
 */
public class PropertyAccessGotoDeclarationHandler implements GotoDeclarationHandler {

  private static final String RESOURCE_BUNDLE_INSTANCE_NAME = "INSTANCE";
  private static final String PROPERTIES_AS_SUFFIX = "_properties.as";

  @Nullable
  @Override
  public PsiElement[] getGotoDeclarationTargets(PsiElement element, int offset, Editor editor) {
    if (element instanceof LeafPsiElement
      && ((LeafPsiElement)element).getElementType().getLanguage() == JavascriptLanguage.INSTANCE) {
      PsiReference reference = TargetElementUtilBase.findReference(editor, offset);
      if (reference != null) {
        return getGotoDeclarationTargetsForPropertyAccess(reference.resolve());
      }
    }
    return null;
  }

  private PsiElement[] getGotoDeclarationTargetsForPropertyAccess(PsiElement resolvedElement) {
    if (resolvedElement instanceof JSFunction
      || resolvedElement instanceof JSVariable && RESOURCE_BUNDLE_INSTANCE_NAME.equals(((JSVariable)resolvedElement).getName())) {
      PsiFile jsFile = resolvedElement.getContainingFile();
      if (jsFile instanceof JSFile && jsFile.getName().endsWith(PROPERTIES_AS_SUFFIX)) {
        Module module = ProjectFileIndex.SERVICE.getInstance(resolvedElement.getProject()).getModuleForFile(jsFile.getVirtualFile());
        String bundleName = getQName((JSFile)jsFile);
        if (module != null && bundleName != null) {
          List<PropertiesFile> propertiesFiles = PropertiesReferenceManager.getInstance(resolvedElement.getProject()).findPropertiesFiles(module, bundleName);
          String propertyName = resolvedElement instanceof JSFunction ? ((JSFunction)resolvedElement).getName() : null;
          return pimpUpResult(propertiesFiles, propertyName);
        }
      }
    }
    return null;
  }

  private static String getQName(JSFile jsFile) {
    String bundleName = null;
    JSSourceElement[] statements = jsFile.getStatements();
    if (statements.length > 0 && statements[0] instanceof JSPackageStatement) {
      bundleName = CompilerUtils.qName(((JSPackageStatement)statements[0]).getQualifiedName(),
        jsFile.getName().substring(0, jsFile.getName().length() - PROPERTIES_AS_SUFFIX.length()));
    }
    return bundleName;
  }

  private PsiElement[] pimpUpResult(List<PropertiesFile> propertiesFiles, String propertyName) {
    Collections.sort(propertiesFiles, new Comparator<PropertiesFile>() {
      @Override
      public int compare(PropertiesFile o1, PropertiesFile o2) {
        return o1.getName().compareTo(o2.getName());
      }
    });
    PsiElement[] result = new PsiElement[propertiesFiles.size()];
    for (int i = 0; i < propertiesFiles.size(); i++) {
      PropertiesFile propertiesFile = propertiesFiles.get(i);
      result[i] = propertiesFile.getContainingFile();
      if (propertyName != null) {
        IProperty property = propertiesFile.findPropertyByKey(propertyName);
        if (property != null) {
          result[i] = property.getPsiElement();
        }
      }
    }
    return result;
  }


  public String getActionText(DataContext paramDataContext) {
    return null;
  }

}
