/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.jangaroo.ide.idea.exml;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
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
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import net.jangaroo.exml.api.Exmlc;
import net.jangaroo.exml.utils.ExmlUtils;
import net.jangaroo.utils.CompilerUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class ExmlElementGotoDeclarationHandler implements GotoDeclarationHandler {

  @Nullable
  @Override
  public PsiElement[] getGotoDeclarationTargets(PsiElement element, int i, Editor editor) {
    List<PsiElement> result = new ArrayList<PsiElement>(3);
    if (element instanceof XmlToken && element.getContainingFile().getName().endsWith(Exmlc.EXML_SUFFIX)
      && element.getParent() instanceof XmlTag) {
      XmlTag xmlTag = (XmlTag)element.getParent();
      JSClass asClass = getASClass(xmlTag);
      if (asClass != null) {
        // found ActionScript class.
        // could be a config class with an [ExtConfig(target="...")] annotation:
        String targetClassName = findTargetClassName(asClass);
        if (targetClassName != null) {
          // always prefer EXML file:
          Project project = xmlTag.getProject();
          VirtualFile exmlFile = findExmlFile(project, targetClassName);
          if (exmlFile != null) {
            PsiFile file = PsiManager.getInstance(project).findFile(exmlFile);
            if (file instanceof XmlFile && file.isValid()) {
              result.add(file);
              return result.toArray(new PsiElement[result.size()]);
            }
          }
          JSClass targetAsClass = ExmlLanguageInjector.getASClass(xmlTag, targetClassName);
          if (targetAsClass != null) {
            result.add(targetAsClass);
          }
        }
        result.add(0, asClass);
      }
    }

    return result.toArray(new PsiElement[result.size()]);
  }

  public static String findTargetClassName(XmlTag xmlTag) {
    JSClass actionScriptClass = getASClass(xmlTag);
    return actionScriptClass == null ? null : findTargetClassName(actionScriptClass);
  }

  private static String findTargetClassName(JSClass asClass) {
    String targetClassName = null;
    JSAttributeList attributeList = asClass.getAttributeList();
    if (attributeList != null) {
      for (JSAttribute attribute : attributeList.getAttributes()) {
        if ("ExtConfig".equals(attribute.getName())) {
          targetClassName = attribute.getValueByName("target").getSimpleValue();
          break;
        }
      }
    }
    return targetClassName;
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

  private static
  @Nullable
  VirtualFile findExmlFile(Project project, String className) {
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

  private static
  @Nullable
  VirtualFile findExmlFile(@NotNull VirtualFile[] sourceRoots, @NotNull String exmlFileName) {
    for (VirtualFile contentRoot : sourceRoots) {
      VirtualFile exmlFile = contentRoot.findFileByRelativePath(exmlFileName);
      if (exmlFile != null) {
        return exmlFile;
      }
    }
    return null;
  }

}
