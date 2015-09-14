/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package net.jangaroo.ide.idea.exml.migration;

import com.intellij.lang.javascript.psi.JSFunction;
import com.intellij.lang.javascript.psi.JSReferenceExpression;
import com.intellij.lang.javascript.psi.ecmal4.JSClass;
import com.intellij.lang.javascript.psi.impl.JSTextReference;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiMigration;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.refactoring.RefactoringFactory;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import net.jangaroo.ide.idea.Utils;

import java.util.ArrayList;

public class FlexMigrationUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.migration.MigrationUtil");

  private FlexMigrationUtil() {
  }

  public static UsageInfo[] findPackageUsages(Project project, PsiMigration migration, String qName) {
    PsiPackage aPackage = findOrCreatePackage(project, migration, qName);

    return findRefs(project, aPackage);
  }

  public static void doPackageMigration(Project project, PsiMigration migration, String newQName, UsageInfo[] usages) {
    try {
      PsiPackage aPackage = findOrCreatePackage(project, migration, newQName);

      // rename all references
      for (UsageInfo usage : usages) {
        if (usage instanceof FlexMigrationProcessor.MigrationUsageInfo) {
          final FlexMigrationProcessor.MigrationUsageInfo usageInfo = (FlexMigrationProcessor.MigrationUsageInfo)usage;
          if (Comparing.equal(newQName, usageInfo.mapEntry.getNewName())) {
            PsiElement element = usage.getElement();
            if (element == null || !element.isValid()) continue;
            if (element instanceof PsiJavaCodeReferenceElement) {
              ((PsiJavaCodeReferenceElement)element).bindToElement(aPackage);
            }
            else {
              bindNonJavaReference(aPackage, element, usage);
            }
          }
        }
      }
    }
    catch (IncorrectOperationException e) {
      // should not happen!
      LOG.error(e);
    }
  }

  private static void bindNonJavaReference(PsiElement bindTo, PsiElement element, UsageInfo usage) {
    final TextRange range = usage.getRangeInElement();
    for (PsiReference reference : element.getReferences()) {
      if (reference instanceof JSTextReference) {
        final JSTextReference classReference = (JSTextReference)reference;
        if (classReference.getRangeInElement().equals(range)) {
          classReference.bindToElement(bindTo);
          break;
        }
      }
    }
  }

  public static UsageInfo[] findClassUsages(Project project, String qName) {
    PsiElement psiElement = findClassOrMember(project, qName, true);
    return findRefs(project, psiElement);
  }

  public static PsiElement findClassOrMember(Project project, String qName, boolean searchInOldLibrary) {
    String[] parts = qName.split("\\$", 2);
    String className = parts[0];
    String member = parts.length == 2 ? parts[1] : null;
    JSClass aClass = findClass(project, className, searchInOldLibrary);
    if (member != null) {
      while (aClass != null) {
        JSFunction method = aClass.findFunctionByName(member);
        if (method != null) {
          return method;
        }
        JSClass[] superClasses = aClass.getSuperClasses();
        if (superClasses.length == 0) {
          break;
        }
        aClass = findClass(project, superClasses[0].getQualifiedName(), searchInOldLibrary);
      }
      return null;
    }
    return aClass;
  }

  private static UsageInfo[] findRefs(final Project project, final PsiElement psiElement) {
    final ArrayList<UsageInfo> results = new ArrayList<UsageInfo>();
    GlobalSearchScope projectScope = GlobalSearchScope.projectScope(project);
    for (PsiReference usage : ReferencesSearch.search(psiElement, projectScope, false)) {
      if (!usage.getElement().getContainingFile().getName().endsWith(".exml")) {
        results.add(new UsageInfo(usage));
      }
    }

    return results.toArray(new UsageInfo[results.size()]);
  }

  public static void doClassMigration(Project project, String newQName, UsageInfo[] usages) {
    try {
      PsiElement classOrMember = findClassOrMember(project, newQName, false);

      // rename all references
      for (UsageInfo usage : usages) {
        if (usage instanceof FlexMigrationProcessor.MigrationUsageInfo) {
          final FlexMigrationProcessor.MigrationUsageInfo usageInfo = (FlexMigrationProcessor.MigrationUsageInfo)usage;
          if (Comparing.equal(newQName, usageInfo.mapEntry.getNewName())) {
            PsiElement element = usage.getElement();
            if (element == null || !element.isValid()) continue;
            if (element instanceof JSReferenceExpression) {
              final JSReferenceExpression referenceElement = (JSReferenceExpression)element;
              try {
                referenceElement.bindToElement(classOrMember);
              } catch (Throwable t) {
                t.printStackTrace();
              }
            }
            else {
              bindNonJavaReference(classOrMember, element, usage);
            }
          }

        }
      }
    }
    catch (IncorrectOperationException e) {
      // should not happen!
      LOG.error(e);
    }
  }

  static PsiPackage findOrCreatePackage(Project project, final PsiMigration migration, final String qName) {
    PsiPackage aPackage = JavaPsiFacade.getInstance(project).findPackage(qName);
    if (aPackage != null) {
      return aPackage;
    }
    else {
      return ApplicationManager.getApplication().runWriteAction(new Computable<PsiPackage>() {
        public PsiPackage compute() {
          return migration.createPackage(qName);
        }
      });
    }
  }

  static JSClass findClass(Project project, final String qName, boolean searchInOldLibrary) {
    //Library library = LibraryTablesRegistrar.getInstance().getLibraryTable(project).getLibraryByName("Maven: net.jangaroo:ext-as:2.0.15-SNAPSHOT-joo");
    GlobalSearchScope scope = searchInOldLibrary
      ? GlobalSearchScope.moduleWithLibrariesScope(ModuleManager.getInstance(project).findModuleByName("portal-exml"))
      : GlobalSearchScope.allScope(project);
    PsiElement jsClass = Utils.getActionScriptClassResolver().findClassByQName(qName, scope);
    if (Utils.isValidActionScriptClass(jsClass)) {
      return (JSClass)jsClass;
    }
    return null;
  }
}
