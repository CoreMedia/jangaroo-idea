package net.jangaroo.ide.idea.exml.migration;

import com.intellij.javascript.flex.mxml.schema.MxmlTagNameReference;
import com.intellij.lang.ASTNode;
import com.intellij.lang.javascript.JavaScriptSupportLoader;
import com.intellij.lang.javascript.psi.JSArgumentList;
import com.intellij.lang.javascript.psi.JSCallExpression;
import com.intellij.lang.javascript.psi.JSElement;
import com.intellij.lang.javascript.psi.JSExpression;
import com.intellij.lang.javascript.psi.JSFunction;
import com.intellij.lang.javascript.psi.JSFunctionExpression;
import com.intellij.lang.javascript.psi.JSNewExpression;
import com.intellij.lang.javascript.psi.JSObjectLiteralExpression;
import com.intellij.lang.javascript.psi.JSParameter;
import com.intellij.lang.javascript.psi.JSParameterList;
import com.intellij.lang.javascript.psi.JSReferenceExpression;
import com.intellij.lang.javascript.psi.JSStatement;
import com.intellij.lang.javascript.psi.JSType;
import com.intellij.lang.javascript.psi.JSVariable;
import com.intellij.lang.javascript.psi.ecmal4.JSAttributeList;
import com.intellij.lang.javascript.psi.ecmal4.JSClass;
import com.intellij.lang.javascript.psi.ecmal4.JSImportStatement;
import com.intellij.lang.javascript.psi.ecmal4.JSQualifiedNamedElement;
import com.intellij.lang.javascript.psi.impl.JSChangeUtil;
import com.intellij.lang.javascript.psi.impl.JSTextReference;
import com.intellij.lang.javascript.psi.resolve.JSClassResolver;
import com.intellij.lang.javascript.search.JSFunctionsSearch;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.JavaProjectRootsUtil;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.impl.source.xml.XmlAttributeReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Query;
import com.intellij.util.containers.HashSet;
import net.jangaroo.ide.idea.Utils;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Set;

public class FlexMigrationUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.migration.MigrationUtil");

  private FlexMigrationUtil() {
  }

  private static void bindNonJavaReference(PsiElement bindTo, PsiElement element, UsageInfo usage) {
    final TextRange range = usage.getRangeInElement();
    for (PsiReference reference : element.getReferences()) {
      if (reference instanceof JSTextReference) {
        // e.g. references in @see api documentation
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
            reference.bindToElement(bindTo);
          }
          break;
        }
      }
    }
  }

  public static UsageInfo[] findClassOrMemberUsages(Project project, String qName) {
    Collection<PsiElement> psiElements = findClassesOrMembers(project, qName);
    if (psiElements.isEmpty()) {
      Notifications.Bus.notify(new Notification("jangaroo", "EXT AS 6 migration",
        "Migration map contains source entry that does not exist in Ext AS 3.4 or project: " + qName,
        NotificationType.WARNING));
      return new UsageInfo[0];
    }
    return findRefs(project, psiElements, true);
  }

  public static PsiElement findClassOrMember(Project project, String qName) {
    Collection<PsiElement> collection = findClassesOrMembers(project, qName);
    return collection.isEmpty() ? null : collection.iterator().next();
  }

  public static Collection<PsiElement> findClassesOrMembers(Project project, String qName) {
    String[] parts = qName.split("#", 2);
    String className = parts[0];
    String member = parts.length == 2 ? parts[1] : null;
    Collection<PsiElement> classes = findJSQualifiedNamedElements(project, className);
    if (member == null) {
      return classes;
    }
    Collection<PsiElement> result = new HashSet<PsiElement>();
    for (PsiElement aClass : classes) {
      if (aClass instanceof JSClass) {
        JSFunction foundMember = findMember((JSClass)aClass, member, null);
        if (foundMember != null) {
          result.add(foundMember);
        }
      }
    }
    return result;
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

  private static UsageInfo[] findRefs(final Project project, @NotNull final Collection<PsiElement> psiElements,
                                      boolean findRefsOfSetter) {
    final Set<UsageInfo> results = new HashSet<UsageInfo>();
    GlobalSearchScope projectScope = GlobalSearchScope.projectScope(project);
    GlobalSearchScope scope = JavaProjectRootsUtil.getScopeWithoutGeneratedSources(projectScope, project);
    for (PsiElement psiElement : psiElements) {
      addRefs(results, scope, psiElement, findRefsOfSetter);
    }
    return results.toArray(new UsageInfo[results.size()]);
  }

  private static void addRefs(Set<? super UsageInfo> results, GlobalSearchScope scope, PsiElement psiElement, boolean findRefsOfSetter) {
    for (PsiReference usage : ReferencesSearch.search(psiElement, scope, false)) {
      if (!(usage instanceof MxmlTagNameReference) && !(usage instanceof XmlAttributeReference)
        && !usage.getElement().getContainingFile().getName().endsWith(".exml")) {
        results.add(new UsageInfo(usage));
      }
    }

    if (psiElement instanceof JSFunction) {
      if (findRefsOfSetter) {
        JSFunction setter = findSetter((JSFunction)psiElement);
        if (setter != null) {
          addRefs(results, scope, setter, false);
        }
      }
      Query<JSFunction> jsFunctions = JSFunctionsSearch.searchOverridingFunctions((JSFunction)psiElement, true);
      for (JSFunction jsFunction : jsFunctions) {
        if (jsFunction.isValid()) {
          if (jsFunction.getContainingFile().getVirtualFile().isWritable()) {
            results.add(new UsageInfo(jsFunction));
          } else {
            // make sure to find usages of overridden functions as well, ReferencesSearch did not return these
            addRefs(results, scope, jsFunction, false);
          }
        }
      }
    }
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

  public static void doClassMigration(Project project,
                                      MigrationMapEntry migrationMapEntry, Collection<UsageInfo> usages) {
    String oldQName = migrationMapEntry.getOldName();
    String newQName = migrationMapEntry.getNewName();
    try {
      PsiElement classOrMember = null;
      JSFunction setter = null;

      // rename all references
      for (UsageInfo usage : usages) {
        if (usage instanceof FlexMigrationProcessor.MigrationUsageInfo) {
          final FlexMigrationProcessor.MigrationUsageInfo usageInfo = (FlexMigrationProcessor.MigrationUsageInfo)usage;
          if (Comparing.equal(oldQName, usageInfo.mapEntry.getOldName())) {
            // resolve the new name from a migration map entry lazily when processing the first usage of the old name

            if (classOrMember == null && !newQName.isEmpty()) {
              int paramsIndex = newQName.indexOf('(');
              String classOrMemberName = paramsIndex >= 0 ? newQName.substring(0, paramsIndex) : newQName;

              classOrMember = findClassOrMember(project, classOrMemberName);
              if (classOrMember == null) {
                Notifications.Bus.notify(new Notification("jangaroo", "EXT AS 6 migration",
                  "Migration map contains target entry that does not exist in Ext AS 6 or project: "
                    + classOrMemberName, NotificationType.WARNING));
                return;
              }
              setter = classOrMember instanceof JSFunction ? findSetter((JSFunction)classOrMember) : null;
            }

            PsiElement element = usage.getElement();
            if (element == null || !element.isValid()) continue;
            if (element instanceof JSReferenceExpression) {
              final JSReferenceExpression referenceElement = (JSReferenceExpression)element;

              PsiElement currentClassOrMember = classOrMember;
              if (setter != null) {
                PsiElement resolvedElement = referenceElement.resolve();
                if (resolvedElement instanceof JSFunction &&
                  ((JSFunction)resolvedElement).getKind() == JSFunction.FunctionKind.SETTER) {
                  currentClassOrMember = setter;
                }
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
                  PsiReference variableReference = getVariableReference(referenceElement);
                  if (variableReference != null && isStaticFunction(currentClassOrMember)) {
                    variableReference.bindToElement(currentClassOrMember.getParent());
                  } else if (referenceElement.getParent() instanceof JSNewExpression && migrationMapEntry.isMappingOfConfigClass()) {
                    migrateConfigClassConstructorUsage(project, oldQName, referenceElement, currentClassOrMember);
                  } else {
                    referenceElement.bindToElement(currentClassOrMember);
                    setCallParameters(project, referenceElement, newQName);
                  }
                }
              } catch (Throwable t) {
                String path = referenceElement.isValid()
                  ? referenceElement.getContainingFile().getVirtualFile().getPath()
                  : "<INVALID>";
                LOG.error("Error during migration of " + referenceElement
                  + " (" + referenceElement.getCanonicalText() + ") in " + path, t);
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

  /**
   * Sets fixed parameter values for a function call to the given element. If the element is not yet a function call,
   * the parameter list is just appended, e.g. for empty parameters: "foo" -> "foo()".
   *
   * <p>This method does nothing if the given targetName does not have a parameter list.
   *
   * @param project project
   * @param element function call element to add parameters to
   * @param targetName target name including parameter specification, including brackets, e.g "(true)" or "()"
   */
  @SuppressWarnings("ConstantConditions")
  private static void setCallParameters(Project project, JSReferenceExpression element, String targetName) {
    int paramsIndex = targetName.indexOf('(');
    if (paramsIndex < 0) {
      return;
    }
    String parameters = targetName.substring(paramsIndex);

    ASTNode expression = JSChangeUtil.createExpressionFromText(project, 'a' + parameters);
    JSArgumentList args = ((JSCallExpression)expression.getPsi()).getArgumentList();
    PsiElement parent = element.getParent();
    if (parent instanceof JSCallExpression) {
      ((JSCallExpression)parent).getArgumentList().replace(args);
    } else {
      parent.addAfter(args, element);
    }
  }

  private static void migrateConfigClassConstructorUsage(Project project,
                                                         String oldQName,
                                                         JSReferenceExpression referenceElement,
                                                         PsiElement newTargetClass) {
    // migrating config class constructor call to a cast of an JSON object to the new class
    JSNewExpression newExpression = (JSNewExpression)referenceElement.getParent();
    JSExpression[] constructorArguments = newExpression.getArguments();
    if (constructorArguments.length > 1) {
      Notifications.Bus.notify(new Notification("jangaroo", "EXT AS 6 migration",
        "Cannot migrate config class constructor call with " + constructorArguments.length
          + " arguments to " + oldQName + " in "
          + referenceElement.getContainingFile().getVirtualFile().getPath(), NotificationType.WARNING));
    } else {
      // for example: new button() => Button({})
      PsiElement castPrototype = JSChangeUtil.createExpressionFromText(project, "a({})").getPsi();
      JSCallExpression castExpression = (JSCallExpression) newExpression.replace(castPrototype);
      // replace "a" with correct target class
      ((JSReferenceExpression)castExpression.getMethodExpression()).bindToElement(newTargetClass);

      if (constructorArguments.length == 1) {
        JSExpression constructorArgument = constructorArguments[0];
        JSObjectLiteralExpression empty = (JSObjectLiteralExpression)castExpression.getArguments()[0];
        if (constructorArgument instanceof JSObjectLiteralExpression) {
          // for example: new button({text:"foo"}) => Button({text:"foo"})
          empty.replace(constructorArgument);
        } else {
          // for example: new button(config) => Button(Ext.apply({}, config))
          PsiElement applyProtoType = JSChangeUtil.createExpressionFromText(project, "Ext.apply({}, c)").getPsi();
          JSCallExpression extApplyCall = (JSCallExpression)empty.replace(applyProtoType);
          // replace "Ext" with ext.Ext to get correct import
          JSReferenceExpression extApply = (JSReferenceExpression)extApplyCall.getMethodExpression();
          JSReferenceExpression ext = ((JSReferenceExpression)extApply.getQualifier());
          PsiElement extClass = findClassOrMember(project, "ext.Ext");
          if (ext != null && extClass != null) {
            ext.bindToElement(extClass);
          }
          // replace "c" with original constructor parameter
          extApplyCall.getArguments()[1].replace(constructorArgument);
        }
      }
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
        JSParameterList parameterList = functionToMigrate.getParameterList();
        PsiElement closingBracket = parameterList.getLastChild();
        parameterList.addBefore(referenceParameter, closingBracket);
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

  static Collection<PsiElement> findJSQualifiedNamedElements(Project project, final String qName) {
    Set<PsiElement> result = new HashSet<PsiElement>();

    JSClassResolver classResolver = Utils.getActionScriptClassResolver();
    GlobalSearchScope searchScope = ProjectScope.getAllScope(project);
    Collection<JSQualifiedNamedElement> elementsByQName = classResolver.findElementsByQName(qName, searchScope);

    for (JSQualifiedNamedElement next : elementsByQName) {
      if (next.isValid()) {
        result.add(next);
      }
    }

    // MXML classes are not returned by #findElementsByQName, try #findClassesByQName
    for (JSClass jsClass : classResolver.findClassesByQName(qName, searchScope)) {
      if (jsClass.isValid()) {
        result.add(jsClass);
      }
    }

    return result;
  }

  private static PsiReference getVariableReference(JSReferenceExpression referenceElement) {
    JSExpression qualifier = referenceElement.getQualifier();
    return qualifier instanceof PsiReference && ((PsiReference)qualifier).resolve() instanceof JSVariable
      ? (PsiReference)qualifier : null;
  }

  private static boolean isStaticFunction(PsiElement element) {
    if (!(element instanceof JSFunction)) {
      return false;
    }
    JSAttributeList attributeList = ((JSFunction) element).getAttributeList();
    return attributeList != null && attributeList.hasModifier(JSAttributeList.ModifierType.STATIC);
  }

  static boolean isImport(UsageInfo o1) {
    PsiElement element = o1.getElement();
    return element != null && element.getParent() instanceof JSImportStatement;
  }
}
