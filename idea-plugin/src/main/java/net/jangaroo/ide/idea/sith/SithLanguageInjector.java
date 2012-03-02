/*
 * Copyright 2012 CoreMedia AG
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS
 * IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package net.jangaroo.ide.idea.sith;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.javascript.JavascriptLanguage;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.InjectedLanguagePlaces;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.LanguageInjector;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiAnnotationParameterList;
import com.intellij.psi.PsiArrayInitializerMemberValue;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiEllipsisType;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * JavaScript language injection in Java SITH JsProxy subclass source files.
 */
public class SithLanguageInjector implements LanguageInjector {

  private static final String JAVA_SCRIPT_EXPRESSION_ANNOTATION_CLASS_QNAME = "com.coremedia.uitesting.webdriver.access.JavaScriptExpression";
  private static final String JAVA_SCRIPT_EXPRESSION_ANNOTATION_PARAMETERS_NAME = "parameters";

  public void getLanguagesToInject(@NotNull PsiLanguageInjectionHost psiLanguageInjectionHost, @NotNull InjectedLanguagePlaces injectedLanguagePlaces) {
    PsiFile psiFile = psiLanguageInjectionHost.getContainingFile();
    if (JavaFileType.INSTANCE.equals(psiFile.getFileType()) && getStringLiteralExpressionValue(psiLanguageInjectionHost) != null) {
      List<String> parameterNames = getParameterNamesIfIsJavaScriptExpression((PsiLiteralExpression)psiLanguageInjectionHost);
      if (parameterNames != null) {
        injectedLanguagePlaces.addPlace(JavascriptLanguage.INSTANCE,
              TextRange.from(1, psiLanguageInjectionHost.getTextLength() - 2), buildCodePrefix(parameterNames), ";}");
      }
    }
  }

  @Nullable
  private List<String> getParameterNamesIfIsJavaScriptExpression(@NotNull PsiLiteralExpression argumentStringLiteralExpression) {
    PsiMethodCallExpression methodCallExpression = findMethodCallExpression(argumentStringLiteralExpression);
    if (methodCallExpression != null) {
      return getParameterNamesIfMethodCallArgumentIsJsExpression(argumentStringLiteralExpression, methodCallExpression);
    }

    PsiAnnotation annotation = findAnnotation(argumentStringLiteralExpression);
    if (annotation != null) {
      return getParameterNamesIfAnnotationArgumentIsJsExpression(argumentStringLiteralExpression, annotation);
    }

    return null;
  }

  @Nullable
  private static PsiMethodCallExpression findMethodCallExpression(@NotNull PsiLiteralExpression argument) {
    PsiElement argumentListCandidate = argument.getParent();
    if (argumentListCandidate instanceof PsiExpressionList) {
      PsiElement methodCallCandidate = argumentListCandidate.getParent();
      if (methodCallCandidate != null && methodCallCandidate instanceof PsiMethodCallExpression) {
        return (PsiMethodCallExpression)methodCallCandidate;
      }
    }
    return null;
  }

  @Nullable
  private static PsiAnnotation findAnnotation(@NotNull PsiLiteralExpression argument) {
    PsiElement nameValuePair = argument.getParent();
    PsiElement annotationParameterListCandidate = nameValuePair.getParent();
    if (annotationParameterListCandidate instanceof PsiAnnotationParameterList) {
      PsiElement annotationCandidate = annotationParameterListCandidate.getParent();
      if (annotationCandidate instanceof PsiAnnotation) {
        return (PsiAnnotation)annotationCandidate;
      }
    }
    return null;
  }

  private List<String> getParameterNamesIfMethodCallArgumentIsJsExpression(@NotNull PsiLiteralExpression argumentStringLiteralExpression,
                                                                           @NotNull PsiMethodCallExpression methodCall) {
    List<String> parameterNames = null;
    PsiMethod method = (PsiMethod)methodCall.getMethodExpression().resolve();
    if (method != null) {
      PsiParameterList parameterList = method.getParameterList();
      PsiParameter[] parameters = parameterList.getParameters();
      // find argumentStringLiteralExpression in argument list:
      PsiExpression[] arguments = methodCall.getArgumentList().getExpressions();
      for (int i = 0; i < Math.min(arguments.length, parameters.length); i++) {
        // check whether parameter at same index has JavaScriptExpression annotation:
        if (arguments[i] == argumentStringLiteralExpression) {
          parameterNames = getParameterNamesIfHasJavaScriptExpressionAnnotation(parameters[i]);
          if (parameterNames != null) {
            addEllipsisArguments(parameters, arguments, i, parameterNames);
          }
          break;
        }
      }
    }
    return parameterNames;
  }

  private static void addEllipsisArguments(PsiParameter[] parameters, PsiExpression[] arguments,
                                           int expressionParameterIndex, @NotNull List<String> parameterNames) {
    if (expressionParameterIndex + 1 < parameters.length) {
        PsiType nextParameterType = parameters[expressionParameterIndex + 1].getType();
        if (nextParameterType instanceof PsiEllipsisType) {
          if (Object.class.getName().equals(((PsiEllipsisType)nextParameterType).getComponentType().getCanonicalText())) {
            for (int j = expressionParameterIndex + 1; j < arguments.length; j += 2) {
              String parameterName = getStringLiteralExpressionValue(arguments[j]);
              if (parameterName != null) {
                parameterNames.add(parameterName);
              }
            }
          }
        }
      }
  }

  private List<String> getParameterNamesIfAnnotationArgumentIsJsExpression(PsiLiteralExpression argument,
                                                                           PsiAnnotation annotation) {
    PsiJavaCodeReferenceElement annotationNameReferenceElement = annotation.getNameReferenceElement();
    if (annotationNameReferenceElement != null) {
      PsiElement annotationClass = annotationNameReferenceElement.resolve();
      if (annotationClass instanceof PsiClass) {
        String parameterName = ((PsiNameValuePair)argument.getParent()).getName();
        if (parameterName == null) {
          parameterName = PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME;
        }
        PsiMethod[] methods = ((PsiClass)annotationClass).getMethods();
        for (PsiMethod method : methods) {
          if (parameterName.equals(method.getName())) {
            return getParameterNamesIfHasJavaScriptExpressionAnnotation(method);
          }
        }
      }
    }
    return null;
  }

  private static List<String> getParameterNamesIfHasJavaScriptExpressionAnnotation(@NotNull PsiModifierListOwner modifierListOwner) {
    PsiModifierList modifierList = modifierListOwner.getModifierList();
    if (modifierList == null) {
      return null;
    }
    PsiAnnotation annotation = AnnotationUtil.findAnnotation(modifierListOwner, JAVA_SCRIPT_EXPRESSION_ANNOTATION_CLASS_QNAME);
    if (annotation == null) {
      return null;
    }
    PsiNameValuePair[] attributes = annotation.getParameterList().getAttributes();
    List<String> parameterNames = new ArrayList<String>();
    for (PsiNameValuePair attribute : attributes) {
      if (JAVA_SCRIPT_EXPRESSION_ANNOTATION_PARAMETERS_NAME.equals(attribute.getName())) {
        PsiAnnotationMemberValue value = attribute.getValue();
        if (value instanceof PsiArrayInitializerMemberValue) {
          for (PsiAnnotationMemberValue initializer : ((PsiArrayInitializerMemberValue)value).getInitializers()) {
            parameterNames.add(getStringLiteralExpressionValue(initializer));
          }
        } else {
          parameterNames.add(getStringLiteralExpressionValue(value));
        }
      }
    }
    return parameterNames;
  }

  @NotNull
  private static String buildCodePrefix(@NotNull List<String> parameterNames) {
    StringBuilder prefix = new StringBuilder();
    prefix.append("function(");
    for (int i = 0; i < parameterNames.size(); i++) {
      if (i > 0) {
        prefix.append(",");
      }
      prefix.append(parameterNames.get(i));
    }
    prefix.append("){return ");
    return prefix.toString();
  }

  @Nullable
  private static String getStringLiteralExpressionValue(PsiElement psiElement) {
    if (psiElement instanceof PsiLiteralExpression) {
      PsiLiteralExpression literalExpression = (PsiLiteralExpression)psiElement;
      final PsiElement child = literalExpression.getFirstChild();
      if (child instanceof PsiJavaToken && ((PsiJavaToken)child).getTokenType() == JavaTokenType.STRING_LITERAL) {
        return (String)literalExpression.getValue();
      }
    }
    return null;
  }

}
