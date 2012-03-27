/*
 * Copyright 2009 CoreMedia AG
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
package net.jangaroo.ide.idea.exml;

import com.intellij.idea.IdeaLogger;
import com.intellij.lang.javascript.JavaScriptSupportLoader;
import com.intellij.lang.javascript.psi.ecmal4.JSClass;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.InjectedLanguagePlaces;
import com.intellij.psi.LanguageInjector;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import com.intellij.xml.XmlElementDescriptor;
import net.jangaroo.exml.api.Exmlc;
import net.jangaroo.exml.utils.ExmlUtils;
import net.jangaroo.utils.AS3Type;
import net.jangaroo.utils.CompilerUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * AS3 language injection in EXML files.
 */
public class ExmlLanguageInjector implements LanguageInjector {

  static String getModuleRelativePath(Project project, VirtualFile file) {
    final Module module = getModuleForFile(project, file);
    if (module != null) {
      for (VirtualFile sourceRoot : ModuleRootManager.getInstance(module).getSourceRoots()) {
        if (VfsUtil.isAncestor(sourceRoot, file, false)) {
          return VfsUtil.getRelativePath(file, sourceRoot, '.');
        }
      }
    }
    return "";
  }

  private static Module getModuleForFile(Project project, VirtualFile file) {
    return ProjectRootManager.getInstance(project).getFileIndex().getModuleForFile(file);
  }

  public void getLanguagesToInject(@NotNull PsiLanguageInjectionHost psiLanguageInjectionHost, @NotNull InjectedLanguagePlaces injectedLanguagePlaces) {
    PsiFile psiFile = psiLanguageInjectionHost.getContainingFile();
    if (psiFile.getName().endsWith(Exmlc.EXML_SUFFIX)) {
      if (psiLanguageInjectionHost instanceof XmlAttributeValue) {
        VirtualFile exmlFile = psiFile.getOriginalFile().getVirtualFile();
        if (exmlFile == null) {
          return;
        }
        Module module = getModuleForFile(psiFile.getProject(), exmlFile);
        if (module == null) {
          return;
        }
        ExmlcConfigurationBean exmlConfig = ExmlCompiler.getExmlConfig(module);
        if (exmlConfig == null) {
          return;
        }
        XmlAttributeValue attributeValue = (XmlAttributeValue)psiLanguageInjectionHost;
        String text = attributeValue.getValue();
        if (isImportClassAttribute(attributeValue)) {
          injectedLanguagePlaces.addPlace(JavaScriptSupportLoader.ECMA_SCRIPT_L4, TextRange.from(1, text.length()), "import ", ";");
        } else {
          if (isBaseClassAttribute(attributeValue) || isDeclarationTypeAttribute(attributeValue) ||
            isDeclarationValueAttribute(attributeValue) || ExmlUtils.isCodeExpression(text)) {
            injectAS(injectedLanguagePlaces, exmlFile, module, exmlConfig, attributeValue);
          }
        }
      } else if (psiLanguageInjectionHost instanceof XmlText) {
        XmlText xmlText = (XmlText)psiLanguageInjectionHost;
        if (isExmlElement(xmlText.getParentTag(), Exmlc.EXML_ANNOTATION_NODE_NAME)) {
          injectedLanguagePlaces.addPlace(JavaScriptSupportLoader.ECMA_SCRIPT_L4, TextRange.from(0, xmlText.getTextRange().getLength()), "[", "]");
        }
      }
    }
  }

  private void injectAS(InjectedLanguagePlaces injectedLanguagePlaces, VirtualFile exmlFile, Module module, ExmlcConfigurationBean exmlConfig, XmlAttributeValue attributeValue) {
    String configClassPackage = exmlConfig.getConfigClassPackage();
    if (configClassPackage == null) {
      IdeaLogger.getInstance(this.getClass()).warn("No config class package set in module " + module.getName() + ", EXML AS3 language injection cancelled.");
      return;
    }
    XmlTag exmlComponentTag = ((XmlFile)attributeValue.getContainingFile()).getRootTag();
    if (exmlComponentTag != null) {

      // find relative path to source root to determine package name:
      VirtualFile packageDir = exmlFile.getParent();
      String packageName = packageDir == null ? "" : getModuleRelativePath(module.getProject(), packageDir);
      String className = exmlFile.getNameWithoutExtension();

      StringBuilder code = new StringBuilder();
      code.append(String.format("package %s {\n", packageName));

      String text = attributeValue.getValue();
      boolean isCodeExpression = ExmlUtils.isCodeExpression(text);
      String superClassName = isCodeExpression ? findSuperClass(exmlComponentTag) : null;

      // find and append imports:
      Set<String> imports = findImports(exmlComponentTag);
      if (superClassName != null) {
        imports.add(superClassName);
      }
      for (String importName : imports) {
        code.append(String.format("import %s;\n", importName));
      }

      code.append(String.format("public class %s", className));

      String configClassName = CompilerUtils.qName(configClassPackage, CompilerUtils.uncapitalize(className));
      String codePrefix = null;

      code.append(" extends ");
      if (isBaseClassAttribute(attributeValue)) {
        codePrefix = flush(code);
      } else {
        code.append(superClassName != null ? superClassName : "Object");
      }
      code.append(" {\n");

      // determine the current EXML element:
      XmlAttribute xmlAttribute = getXmlAttribute(attributeValue);
      XmlTag xmlTag = xmlAttribute != null ? xmlAttribute.getParent() : null;

      if (codePrefix == null && xmlTag != null && isExmlElement(xmlTag) && Exmlc.EXML_CFG_NODE_NAME.equals(xmlTag.getLocalName())) {
        String cfgName = xmlTag.getAttributeValue(Exmlc.EXML_DECLARATION_NAME_ATTRIBUTE);
        if (cfgName != null && cfgName.length() > 0) {
          code.append(String.format("public var %s:", cfgName));
          codePrefix = flush(code);
          code.append(";");
        }
      }

      if (codePrefix == null) {
        codePrefix = renderDeclarations(exmlComponentTag, code, xmlTag, xmlAttribute,
          Exmlc.EXML_CONSTANT_NODE_NAME, "public static const");
      }
      code.append(String.format("public function %s(config:%s = null){\n", className, configClassName));
      if (codePrefix == null) {
        codePrefix = renderDeclarations(exmlComponentTag, code, xmlTag, xmlAttribute,
          Exmlc.EXML_VAR_NODE_NAME, "var");
      }
      code.append("  super(");
      if (codePrefix == null) {
        code.append(configClassName).append("({x:(");
        codePrefix = flush(code);
        code.append(")})");
      } else {
        code.append("config");
      }
      code
        .append(");\n")    // super(
        .append("}\n")     // constructor {
        .append("\n}\n")   // class {
        .append("\n}\n");  // package {

      TextRange textRange = isCodeExpression ?
        TextRange.from(2, text.length() - 2) : // cut off quotes and braces ("{...}")
        TextRange.from(1, text.length());      // cut off quotes only ("...")
      injectedLanguagePlaces.addPlace(JavaScriptSupportLoader.ECMA_SCRIPT_L4, textRange, codePrefix, code.toString());
    }
  }

  private String renderDeclarations(XmlTag exmlComponentTag, StringBuilder code,
                                    XmlTag xmlTag, XmlAttribute xmlAttribute,
                                    String nodeName, String declarationPrefix) {
    int editingIndex = 0;
    if (xmlTag != null && isExmlElement(xmlTag, nodeName)) {
      String attributeName = xmlAttribute.getLocalName();
      if (Exmlc.EXML_DECLARATION_TYPE_ATTRIBUTE.equals(attributeName)) {
        editingIndex = 1; // second element in String[]: type
      } else if (Exmlc.EXML_DECLARATION_VALUE_ATTRIBUTE.equals(attributeName)) {
        editingIndex = 2; // third element in String[]: value
      }
    }
    // find and append declarations:
    List<String[]> declarations = findDeclarationsUntil(exmlComponentTag, editingIndex > 0 ? xmlTag : null, nodeName);
    return renderDeclarations(code, declarations, declarationPrefix, editingIndex);
  }

  private String renderDeclarations(StringBuilder code, List<String[]> constants, String declarationPrefix,
                                    int editingIndex) {
    String codePrefix = null;
    for (int i = 0, constantsSize = constants.size(); i < constantsSize; i++) {
      String[] constantNameTypeValue = constants.get(i);
      String description = constantNameTypeValue[3];
      if (description != null) {
        code.append("/** ").append(description).append(" */");
      }
      code.append(declarationPrefix).append(' ');
      String name = constantNameTypeValue[0];
      code.append(name);

      String type = constantNameTypeValue[1];
      if (i == constantsSize - 1 && editingIndex == 1) {
        code.append(':');
        codePrefix = flush(code);
      } else {
        if (type != null) {
          code.append(':').append(type);
        }
      }

      if (i == constantsSize - 1 && editingIndex == 2) {
        code.append(" = ");
        codePrefix = flush(code);
      } else {
        String value = constantNameTypeValue[2];
        if (value != null) {
          code.append(" = ");
          if (ExmlUtils.isCodeExpression(value)) {
            value = ExmlUtils.getCodeExpression(value);
          }
          code.append(value);
        }
      }
      code.append(";\n");
    }
    return codePrefix;
  }

  private static String flush(StringBuilder sb) {
    String current = sb.toString();
    sb.setLength(0);
    return current;
  }

  private static String findSuperClass(XmlTag exmlComponentTag) {
    XmlAttribute baseClassAttribute = exmlComponentTag.getAttribute(Exmlc.EXML_BASE_CLASS_ATTRIBUTE);
    if (baseClassAttribute != null) {
      return baseClassAttribute.getValue();
    }
    XmlTag[] subTags = exmlComponentTag.getSubTags();
    XmlTag componentTag = findNonExmlNamespaceTag(subTags);
    if (componentTag != null) {
      XmlElementDescriptor descriptor = componentTag.getDescriptor();
      if (descriptor instanceof ComponentXmlElementDescriptorProvider.ComponentXmlElementDescriptor) {
        JSClass componentClass = ((ComponentXmlElementDescriptorProvider.ComponentXmlElementDescriptor)descriptor).getComponentClass();
        if (componentClass != null) {
          return componentClass.getQualifiedName();
        }
      }
    }
    return null;
  }

  private static XmlTag findNonExmlNamespaceTag(XmlTag[] subTags) {
    for (int i = subTags.length - 1; i >= 0; i--) {
      if (!isExmlElement(subTags[i])) {
        return subTags[i];
      }
    }
    return null;
  }

  private static Set<String> findImports(XmlTag exmlComponentTag) {
    Set<String> imports = new LinkedHashSet<String>();
    XmlTag componentTag = null;
    for (XmlTag topLevelXmlTag : exmlComponentTag.getSubTags()) {
      if (isExmlElement(topLevelXmlTag)) {
        String elementName = topLevelXmlTag.getLocalName();
        if (Exmlc.EXML_IMPORT_NODE_NAME.equals(elementName)) {
          imports.add(topLevelXmlTag.getAttributeValue(Exmlc.EXML_IMPORT_CLASS_ATTRIBUTE));
        } else if (Exmlc.EXML_CONSTANT_NODE_NAME.equals(elementName)
          || Exmlc.EXML_CFG_NODE_NAME.equals(elementName)
          || Exmlc.EXML_VAR_NODE_NAME.equals(elementName)) {
          String type = topLevelXmlTag.getAttributeValue(Exmlc.EXML_DECLARATION_TYPE_ATTRIBUTE);
          if (type != null && type.contains(".")) {
            imports.add(type);
          }
        }
      } else {
        componentTag = topLevelXmlTag; // remember last non-EXML-namespace tag, which contains the view tree
      }
    }

    if (componentTag != null) {
      addComponentImports(imports, componentTag);
    }
    return imports;
  }

  private static void addComponentImports(Set<String> imports, XmlTag componentTag) {
    String packageName = ExmlUtils.parsePackageFromNamespace(componentTag.getNamespace());
    if (packageName != null) {
      String configClassName = CompilerUtils.qName(packageName, componentTag.getLocalName());
      imports.add(configClassName);
      for (XmlTag property : componentTag.getSubTags()) {
        for (XmlTag subComponent : property.getSubTags()) {
          addComponentImports(imports, subComponent);
        }
      }
    }
  }

  private static List<String[]> findDeclarationsUntil(XmlTag exmlComponentTag, XmlTag untilNode, String nodeName) {
    List<String[]> constants = new ArrayList<String[]>();
    for (XmlTag topLevelXmlTag : exmlComponentTag.getSubTags()) {
      if (nodeName.equals(topLevelXmlTag.getLocalName())) {
        String name = topLevelXmlTag.getAttributeValue(Exmlc.EXML_DECLARATION_NAME_ATTRIBUTE);
        if (name == null) {
          continue;
        }
        String type = topLevelXmlTag.getAttributeValue(Exmlc.EXML_DECLARATION_TYPE_ATTRIBUTE);
        String attributeValue = topLevelXmlTag.getAttributeValue(Exmlc.EXML_DECLARATION_VALUE_ATTRIBUTE);
        if (type == null) {
          AS3Type as3Type = attributeValue == null ? null : CompilerUtils.guessType(attributeValue);
          if (as3Type == null) {
            as3Type = AS3Type.STRING;
          }
          type = as3Type.toString();
        }
        XmlTag[] descriptionTags = topLevelXmlTag.findSubTags(Exmlc.EXML_DESCRIPTION_NODE_NAME, Exmlc.EXML_NAMESPACE_URI);
        String description = descriptionTags.length > 0 ? descriptionTags[0].getValue().getText() : null;
        constants.add(new String[]{
          name,
          type,
          attributeValue,
          description
        });
        if (topLevelXmlTag.equals(untilNode)) {
          break;
        }
      }
    }
    return constants;
  }

  private static boolean isImportClassAttribute(XmlAttributeValue attributeValue) {
    return isAttribute(attributeValue, Exmlc.EXML_IMPORT_NODE_NAME, Exmlc.EXML_IMPORT_CLASS_ATTRIBUTE);
  }

  private static boolean isDeclarationTypeAttribute(XmlAttributeValue attributeValue) {
    return isAttribute(attributeValue, null, Exmlc.EXML_DECLARATION_TYPE_ATTRIBUTE);
  }

  private static boolean isDeclarationValueAttribute(XmlAttributeValue attributeValue) {
    if (isAttribute(attributeValue, null, Exmlc.EXML_DECLARATION_VALUE_ATTRIBUTE)) {
      // only return "true" if there also is a non-empty declaration type attribute, because otherwise,
      // language injection makes no sense:
      XmlTag declarationElement = (XmlTag)attributeValue.getParent().getParent();
      String type = declarationElement.getAttributeValue(Exmlc.EXML_DECLARATION_TYPE_ATTRIBUTE);
      return type != null && type.length() > 0 && !AS3Type.STRING.toString().equals(type);
    }
    return false;
  }

  private static boolean isBaseClassAttribute(XmlAttributeValue attributeValue) {
    return isAttribute(attributeValue, null, Exmlc.EXML_BASE_CLASS_ATTRIBUTE);
  }

  private static boolean isExmlElement(XmlTag element) {
    return Exmlc.EXML_NAMESPACE_URI.equals(element.getNamespace());
  }

  private static boolean isExmlElement(XmlTag element, @Nullable String exmlElementName) {
    return isExmlElement(element) && (exmlElementName == null || exmlElementName.equals(element.getLocalName()));
  }

  private static boolean isAttribute(XmlAttributeValue attributeValue, @Nullable String exmlElementName, String exmlAttribute) {
    XmlAttribute attribute = getXmlAttribute(attributeValue);
    if (attribute != null && exmlAttribute.equals(attribute.getName())) {
      XmlTag element = attribute.getParent();
      return isExmlElement(element, exmlElementName);
    }
    return false;
  }

  private static XmlAttribute getXmlAttribute(XmlAttributeValue attributeValue) {
    PsiElement parent = attributeValue.getParent();
    return parent instanceof XmlAttribute ? (XmlAttribute)parent : null;
  }

}
