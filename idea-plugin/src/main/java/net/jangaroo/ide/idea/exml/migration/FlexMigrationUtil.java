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

import com.intellij.javascript.flex.mxml.schema.MxmlTagNameReference;
import com.intellij.lang.javascript.JavaScriptSupportLoader;
import com.intellij.lang.javascript.psi.JSElement;
import com.intellij.lang.javascript.psi.JSFunction;
import com.intellij.lang.javascript.psi.JSFunctionExpression;
import com.intellij.lang.javascript.psi.JSNamedElement;
import com.intellij.lang.javascript.psi.JSParameter;
import com.intellij.lang.javascript.psi.JSReferenceExpression;
import com.intellij.lang.javascript.psi.JSStatement;
import com.intellij.lang.javascript.psi.JSType;
import com.intellij.lang.javascript.psi.ecmal4.JSClass;
import com.intellij.lang.javascript.psi.ecmal4.JSQualifiedNamedElement;
import com.intellij.lang.javascript.psi.impl.JSChangeUtil;
import com.intellij.lang.javascript.psi.impl.JSTextReference;
import com.intellij.lang.javascript.search.JSFunctionsSearch;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
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
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.impl.source.xml.XmlAttributeReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.refactoring.migration.MigrationMapEntry;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Query;
import net.jangaroo.ide.idea.Utils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

public class FlexMigrationUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.migration.MigrationUtil");

  private FlexMigrationUtil() {
  }

  public static UsageInfo[] findPackageUsages(Project project, PsiMigration migration, String qName) {
    PsiPackage aPackage = findOrCreatePackage(project, migration, qName);
    if (aPackage == null) {
      LOG.warn("Migration map contains unknown package: " + qName);
      return new UsageInfo[0];
    }
    return findRefs(project, aPackage, true);
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
      if (reference instanceof JSTextReference || reference instanceof MxmlTagNameReference || reference instanceof XmlAttributeReference) {
        if (reference.getRangeInElement().equals(range)) {
          if (bindTo == null) {
            PsiElement referenceElement = reference.getElement();
            PsiElement maybeWhitespace = referenceElement.getPrevSibling();
            if (maybeWhitespace instanceof PsiWhiteSpace) {
              maybeWhitespace.getParent().deleteChildRange(maybeWhitespace, referenceElement);
            } else {
              referenceElement.delete();
            }
          } else {
            if (reference instanceof XmlAttributeReference) {
              reference.handleElementRename(((JSNamedElement)bindTo).getName());
            } else if (reference instanceof MxmlTagNameReference) {
              reference.bindToElement(bindTo.getContainingFile());
            } else {
              reference.bindToElement(bindTo);
            }
          }
          break;
        }
      }
    }
  }

  public static UsageInfo[] findClassOrMemberUsages(Project project, String qName) {
    PsiElement psiElement = findClassOrMember(project, qName, true);
    if (psiElement == null) {
      LOG.warn("Migration map contains unknown class or member: " + qName);
      return new UsageInfo[0];
    }
    return findRefs(project, psiElement, true);
  }

  public static PsiElement findClassOrMember(Project project, String qName, boolean searchInOldLibrary) {
    return findClassOrMember(project, qName, null, searchInOldLibrary);
  }

  public static PsiElement findClassOrMember(Project project, String qName, JSFunction.FunctionKind functionKind,
                                             boolean searchInOldLibrary) {
    String[] parts = qName.split("#", 2);
    String className = parts[0];
    String member = parts.length == 2 ? parts[1] : null;
    JSQualifiedNamedElement aClass = findJSQualifiedNamedElement(project, className, searchInOldLibrary);
    if (aClass instanceof JSClass && member != null) {
      return findMember((JSClass)aClass, member, functionKind);
    }
    return aClass;
  }

  public static JSFunction findMember(JSClass aClass, String member, JSFunction.FunctionKind functionKind) {
    while (aClass != null) {
      JSFunction method = functionKind == null
        ? aClass.findFunctionByName(member)
        : aClass.findFunctionByNameAndKind(member, functionKind);
      if (method != null) {
        return method;
      }
      JSClass[] superClasses = aClass.getSuperClasses();
      if (superClasses.length == 0) {
        break;
      }
      aClass = superClasses[0];
    }
    return null;
  }

  private static UsageInfo[] findRefs(final Project project, @NotNull final PsiElement psiElement,
                                      boolean findRefsOfSetter) {
    final ArrayList<UsageInfo> results = new ArrayList<UsageInfo>();
    GlobalSearchScope projectScope = GlobalSearchScope.projectScope(project);
    for (PsiReference usage : ReferencesSearch.search(psiElement, projectScope, false)) {
      if (!usage.getElement().getContainingFile().getName().endsWith(".exml")) {
        results.add(new UsageInfo(usage));
      }
    }

    if (psiElement instanceof JSFunction) {
      if (findRefsOfSetter) {
        JSFunction setter = findSetter((JSFunction)psiElement);
        if (setter != null) {
          results.addAll(Arrays.asList(findRefs(project, setter, false)));
        }
      }
      Query<JSFunction> jsFunctions = JSFunctionsSearch.searchOverridingFunctions((JSFunction)psiElement, true);
      for (JSFunction jsFunction : jsFunctions) {
        if (jsFunction.isValid()) {
          if (jsFunction.getContainingFile().getVirtualFile().isWritable()) {
            results.add(new UsageInfo(jsFunction));
          } else {
            // make sure to find usages of overridden functions as well, ReferencesSearch did not return these
            results.addAll(Arrays.asList(findRefs(project, jsFunction, false)));
          }
        }
      }
    }
    return results.toArray(new UsageInfo[results.size()]);
  }

  private static JSFunction findSetter(JSFunction getter) {
    if (getter.getKind() == JSFunction.FunctionKind.GETTER) {
      PsiElement parent = getter.getParent();
      if (parent instanceof JSClass) {
        return findMember((JSClass)parent, getter.getName(), JSFunction.FunctionKind.SETTER);
      }
    }
    return null;
  }

  public static void doClassMigration(Project project, MigrationMapEntry migrationMapEntry, UsageInfo[] usages) {
    String oldQName = migrationMapEntry.getOldName();
    String newQName = migrationMapEntry.getNewName();
    try {
      PsiElement classOrMember = null;
      if (!newQName.isEmpty()) {
        classOrMember = findClassOrMember(project, newQName, false);
        if (classOrMember == null) {
          LOG.warn("Migration map contains unknown new class or member: " + newQName);
          return;
        }
      }
      JSFunction setter = classOrMember instanceof JSFunction ? findSetter((JSFunction)classOrMember) : null;

      // migrate import usages first in order to avoid unnecessary fully-qualified names
      Arrays.sort(usages, ImportUsageFirstComparator.INSTANCE);

      // rename all references
      for (UsageInfo usage : usages) {
        if (usage instanceof FlexMigrationProcessor.MigrationUsageInfo) {
          final FlexMigrationProcessor.MigrationUsageInfo usageInfo = (FlexMigrationProcessor.MigrationUsageInfo)usage;
          if (Comparing.equal(oldQName, usageInfo.mapEntry.getOldName())) {
            PsiElement element = usage.getElement();
            if (element == null || !element.isValid()) continue;
            if (element instanceof JSReferenceExpression) {
              final JSReferenceExpression referenceElement = (JSReferenceExpression)element;

              PsiElement resolvedElement = referenceElement.resolve();
              PsiElement currentClassOrMember = classOrMember;
              if (setter != null &&
                resolvedElement instanceof JSFunction &&
                ((JSFunction)resolvedElement).getKind() == JSFunction.FunctionKind.SETTER) {
                currentClassOrMember = setter;
              }

              try {
                if (currentClassOrMember == null) {
                  PsiElement current = referenceElement;
                  while (current instanceof JSElement) {
                    if (current instanceof JSStatement) {
                      replaceByComment(project, "EXT6_GONE:" + oldQName, current);
                      break;
                    }
                    current = current.getParent();
                  }
                } else {
                  referenceElement.bindToElement(currentClassOrMember);
                }
              } catch (Throwable t) {
                t.printStackTrace();
              }
            } else if (classOrMember instanceof JSFunction && element instanceof JSFunction) {
              adjustOverriddenMethodSignature(project, (JSFunction)classOrMember, (JSFunction)element);
            } else {
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

  public static PsiElement replaceByComment(Project project, String todoComment, PsiElement element) {
    String escapedBlockComments = element.getText().replaceAll("/[*]", "/!*").replaceAll("[*]/", "*!/");
    String commentText = String.format("/* %s %s*/", todoComment, escapedBlockComments);
    return element.replace((PsiElement)JSChangeUtil.createJSTreeFromText(project, commentText));
  }

  private static void adjustOverriddenMethodSignature(Project project, JSFunction referenceFunction, JSFunction functionToMigrate) {
    JSParameter[] parameters = functionToMigrate.getParameters();
    JSParameter[] referenceParameters = referenceFunction.getParameters();
    for (int i = 0; i < Math.max(parameters.length, referenceParameters.length); i++) {
      JSParameter parameter = i < parameters.length ? parameters[i] : null;
      JSParameter referenceParameter = i < referenceParameters.length ? referenceParameters[i] : null;
      if (referenceParameter == null) {
        assert parameter != null;
        parameter.delete();
      } else if (parameter == null) {
        functionToMigrate.getParameterList().add(referenceParameter);
      } else {
        // correct type of parameter according to reference parameter, but keep using the same name:
        // TODO: also correct initializers (optional!) and ...rest parameter!
        // TODO: better try to map old parameters to new one's, not only based on index but also based on type / name!
        JSType referenceParameterType = referenceParameter.getType();
        if (referenceParameterType != null) {
          JSFunctionExpression functionExpression = (JSFunctionExpression)JSChangeUtil.createExpressionFromText(project, String.format("function(%s:%s){}", parameter.getName(), referenceParameterType.getResolvedTypeText()), JavaScriptSupportLoader.ECMA_SCRIPT_L4).getPsi();
          JSParameter correctedParameter = functionExpression.getParameters()[0];
          parameter.replace(correctedParameter);
        }
      }
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

  static JSQualifiedNamedElement findJSQualifiedNamedElement(Project project, final String qName, boolean searchInOldLibrary) {
    //Library library = LibraryTablesRegistrar.getInstance().getLibraryTable(project).getLibraryByName("Maven: net.jangaroo:ext-as:2.0.15-SNAPSHOT-joo");
    ModuleManager moduleManager = ModuleManager.getInstance(project);
    Module extAs6Module = moduleManager.findModuleByName("jangaroo-ext-as"); // todo use "external library" (dependency) instead of module
    GlobalSearchScope scope;
    if (extAs6Module == null) {
      scope = GlobalSearchScope.allScope(project);
    } else {
      GlobalSearchScope extAs6ModuleScope = GlobalSearchScope.moduleScope(extAs6Module);
      scope = searchInOldLibrary
        ? GlobalSearchScope.notScope(extAs6ModuleScope)
        : extAs6ModuleScope;
    }
    Iterator<JSQualifiedNamedElement> jsQualifiedNamedElements = Utils.getActionScriptClassResolver().findElementsByQName(qName, scope).iterator();
    // use the last occurrence, as the source occurrences come first and do not return any usages:
    JSQualifiedNamedElement jsElement = null;
    while (jsQualifiedNamedElements.hasNext()) {
      JSQualifiedNamedElement next = jsQualifiedNamedElements.next();
      if (next.isValid()) {
        jsElement = next;
      }
    }
    return jsElement;
  }
}
