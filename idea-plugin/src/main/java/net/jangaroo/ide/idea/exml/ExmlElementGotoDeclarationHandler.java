package net.jangaroo.ide.idea.exml;

import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.lang.javascript.psi.JSFunction;
import com.intellij.lang.javascript.psi.ecmal4.JSAttribute;
import com.intellij.lang.javascript.psi.ecmal4.JSAttributeList;
import com.intellij.lang.javascript.psi.ecmal4.JSClass;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import net.jangaroo.exml.api.Exmlc;
import net.jangaroo.exml.utils.ExmlUtils;
import net.jangaroo.utils.CompilerUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Frank Wienberg
 */
public class ExmlElementGotoDeclarationHandler implements GotoDeclarationHandler {

  @Nullable
  @Override
  public PsiElement[] getGotoDeclarationTargets(PsiElement element, int offset, Editor editor) {
    if (element instanceof XmlToken && element.getContainingFile().getName().endsWith(Exmlc.EXML_SUFFIX)) {
      PsiReference reference = TargetElementUtilBase.findReference(editor, offset);
      if (reference != null) {
        PsiElement resolvedElement = reference.resolve();
        if (resolvedElement instanceof XmlTag) {
          return getGotoDeclarationTargets((XmlTag)resolvedElement);
        }
      }
    }
    return null;
  }

  @Nullable
  public PsiElement[] getGotoDeclarationTargets(XmlTag resolvedTag) {
    String[] targetName = getGotoDeclarationTargetName(resolvedTag);
    if (targetName == null) {
      return null;
    }
    String configClassName = targetName[0];
    String configPropertyName = targetName.length > 1 ? targetName[1] : null;
    JSClass configClass = ExmlLanguageInjector.getASClass(resolvedTag, configClassName);
    if (configClass != null) {
      // found config class that should have an [ExtConfig(target="...")] annotation:
      String targetClassName = findTargetClassName(configClass);
      if (targetClassName != null) {
        // always prefer EXML file:
        PsiElement exmlDeclarationTarget = getGotoDeclarationTargetInExmlFile(resolvedTag, targetClassName, configPropertyName);
        if (exmlDeclarationTarget != null) {
          return new PsiElement[]{exmlDeclarationTarget};
        }
      }
      if (configPropertyName != null) {
        // find ActionScript property setter public function set <attributeValue>:
        JSFunction attributeSetter = configClass.findFunctionByNameAndKind(configPropertyName, JSFunction.FunctionKind.SETTER);
        if (attributeSetter != null) {
          return new PsiElement[]{attributeSetter};
        }
      }
      if (targetClassName != null) {
        JSClass targetClass = ExmlLanguageInjector.getASClass(resolvedTag, targetClassName);
        if (targetClass != null) {
          return new PsiElement[]{configClass, targetClass};
        }
      }
      return new PsiElement[]{configClass};
    }
    return null;
  }

  private static PsiElement getGotoDeclarationTargetInExmlFile(XmlTag resolvedTag, String targetClassName, String configPropertyName) {
    PsiElement exmlDeclarationTarget = null;
    XmlFile exmlPsiFile = findExmlPsiFile(resolvedTag.getProject(), targetClassName);
    if (exmlPsiFile != null) {
      exmlDeclarationTarget = exmlPsiFile;
      if (configPropertyName != null) {
        // find element <exml:cfg name="<configPropertyName>">:
        XmlTag rootTag = exmlPsiFile.getRootTag();
        if (rootTag != null) {
          XmlTag[] topLevelElements = rootTag.getSubTags();
          for (XmlTag topLevelElement : topLevelElements) {
            if (Exmlc.EXML_NAMESPACE_URI.equals(topLevelElement.getNamespace())
              && Exmlc.EXML_CFG_NODE_NAME.equals(topLevelElement.getLocalName())
              && configPropertyName.equals(topLevelElement.getAttributeValue(Exmlc.EXML_CFG_NAME_ATTRIBUTE))) {
              exmlDeclarationTarget = topLevelElement;
            }
          }
        }
      }
    }
    return exmlDeclarationTarget;
  }

  @Nullable
  private static String[] getGotoDeclarationTargetName(XmlTag resolvedTag) {
    String resolvedTagName = resolvedTag.getLocalName();
    if ("element".equals(resolvedTagName)) {
      return getGotoDeclarationTargetNameForElement(resolvedTag);
    } else if ("attribute".equals(resolvedTagName)) {
      return getGotoDeclarationTargetNameForAttribute(resolvedTag);
    }
    return null;
  }

  private static String[] getGotoDeclarationTargetNameForElement(XmlTag resolvedTag) {
    String qualifiedType = resolvedTag.getAttributeValue("type");
    if (qualifiedType != null) {
      String[] parts = qualifiedType.split(":");
      if (parts.length == 2) {
        return new String[]{parts[1]};
      }
    }
    // an element can also be a config option:
    return getGotoDeclarationTargetNameForAttribute(resolvedTag);
  }

  @Nullable
  private static String[] getGotoDeclarationTargetNameForAttribute(XmlTag resolvedTag) {
    String configPropertyName = resolvedTag.getAttributeValue("name");
    if (configPropertyName != null) {
      // An XML attribute definition in an XSD generated by exmlc.
      // Find the defining type node:
      XmlTag current = resolvedTag.getParentTag();
      while (current != null) {
        if ("complexType".equals(current.getLocalName())) {
          String configClassName = current.getAttributeValue("name");
          if (configClassName != null) {
            return new String[]{configClassName, configPropertyName};
          }
        }
        current = current.getParentTag();
      }
    }
    return null;
  }

  @Nullable
  public static String findTargetClassName(XmlTag xmlTag) {
    JSClass actionScriptClass = getASClass(xmlTag);
    return actionScriptClass == null ? null : findTargetClassName(actionScriptClass);
  }

  @Nullable
  private static String findTargetClassName(JSClass asClass) {
    JSAttributeList attributeList = asClass.getAttributeList();
    if (attributeList != null) {
      for (JSAttribute attribute : attributeList.getAttributes()) {
        if ("ExtConfig".equals(attribute.getName())) {
          return attribute.getValueByName("target").getSimpleValue();
        }
      }
    }
    return null;
  }

  private static JSClass getASClass(XmlTag xmlTag) {
    String packageName = ExmlUtils.parsePackageFromNamespace(xmlTag.getNamespace());
    if (packageName != null) {
      String className = CompilerUtils.qName(packageName, xmlTag.getLocalName());
      JSClass asClass = ExmlLanguageInjector.getASClass(xmlTag, className);
      if (asClass != null) {
        return asClass;
      }
    }
    return null;
  }


  public String getActionText(DataContext paramDataContext) {
    return null;
  }

  @Nullable
  private static XmlFile findExmlPsiFile(Project project, String targetClassName) {
    VirtualFile exmlFile = findExmlFile(project, targetClassName);
    if (exmlFile != null) {
      PsiFile file = PsiManager.getInstance(project).findFile(exmlFile);
      if (file instanceof XmlFile && file.isValid()) {
        return (XmlFile)file;
      }
    }
    return null;
  }

  @Nullable
  private static VirtualFile findExmlFile(Project project, String className) {
    String exmlFileName = className.replace('.', '/') + Exmlc.EXML_SUFFIX;
    Module[] modules = ModuleManager.getInstance(project).getModules();
    for (Module module : modules) {
      VirtualFile exmlFile = findExmlFile(ModuleRootManager.getInstance(module).getSourceRoots(), exmlFileName);
      if (exmlFile != null) {
        return exmlFile;
      }
    }
    LibraryTable table = LibraryTablesRegistrar.getInstance().getLibraryTableByLevel(LibraryTablesRegistrar.PROJECT_LEVEL, project);
    if (table != null) {
      for (Library library : table.getLibraries()) {
        VirtualFile exmlFile = findExmlFile(library.getFiles(OrderRootType.SOURCES), exmlFileName);
        if (exmlFile != null) {
          return exmlFile;
        }
      }
    }
    return null;
  }

  @Nullable
  private static VirtualFile findExmlFile(@NotNull VirtualFile[] sourceRoots, @NotNull String exmlFileName) {
    for (VirtualFile contentRoot : sourceRoots) {
      VirtualFile exmlFile = contentRoot.findFileByRelativePath(exmlFileName);
      if (exmlFile != null) {
        return exmlFile;
      }
    }
    return null;
  }

}
