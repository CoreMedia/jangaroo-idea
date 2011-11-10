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
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.InjectedLanguagePlaces;
import com.intellij.psi.LanguageInjector;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlElementDescriptor;
import net.jangaroo.exml.api.Exmlc;
import net.jangaroo.utils.CompilerUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Jangaroo Project Component.
 */
public class ExmlProjectComponent implements ProjectComponent {
  private Project project;
  private ExmlCompiler exmlc;

  public ExmlProjectComponent(Project project) {
    this.project = project;
  }

  private String getModuleRelativePath(VirtualFile file) {
    final Module module = getModuleForFile(file);
    if (module != null) {
      for (VirtualFile sourceRoot : ModuleRootManager.getInstance(module).getSourceRoots()) {
        if (VfsUtil.isAncestor(sourceRoot, file, false)) {
          return VfsUtil.getRelativePath(file, sourceRoot, '.');
        }
      }
    }
    return "";
  }

  private Module getModuleForFile(VirtualFile file) {
    return ProjectRootManager.getInstance(project).getFileIndex().getModuleForFile(file);
  }

  public void initComponent() {
    exmlc = new ExmlCompiler();
    // language injection: see http://www.jetbrains.net/devnet/message/5208687
    PsiManager.getInstance(project).registerLanguageInjector(new LanguageInjector() {
      public void getLanguagesToInject(@NotNull PsiLanguageInjectionHost psiLanguageInjectionHost, @NotNull InjectedLanguagePlaces injectedLanguagePlaces) {
        //System.out.println("psiLanguageInjectionHost: "+psiLanguageInjectionHost);
        PsiFile psiFile = psiLanguageInjectionHost.getContainingFile();
        if (psiFile.getName().endsWith(".exml") && psiLanguageInjectionHost instanceof XmlAttributeValue) {
          VirtualFile exmlFile = psiFile.getOriginalFile().getVirtualFile();
          if (exmlFile == null) {
            return;
          }
          Module module = getModuleForFile(exmlFile);
          if (module == null) {
            return;
          }
          ExmlcConfigurationBean exmlConfig = ExmlCompiler.getExmlConfig(module);
          if (exmlConfig == null) {
            return;
          }
          XmlAttributeValue attributeValue = (XmlAttributeValue)psiLanguageInjectionHost;
          if (isImportClassAttribute(attributeValue)) {
            injectedLanguagePlaces.addPlace(JavaScriptSupportLoader.ECMA_SCRIPT_L4, new TextRange(1, attributeValue.getTextRange().getLength()-1), "import ", ";");
          } else {
            boolean baseClassAttribute = getBaseClassAttribute(attributeValue) != null;
            String text = attributeValue.getText();
            if (baseClassAttribute || text.startsWith("\"{") && text.endsWith("}\"")) {
              injectAS(injectedLanguagePlaces, exmlFile, module, exmlConfig, attributeValue, baseClassAttribute);
            }
          }
        }
      }

      private void injectAS(InjectedLanguagePlaces injectedLanguagePlaces, VirtualFile exmlFile, Module module, ExmlcConfigurationBean exmlConfig, XmlAttributeValue attributeValue, boolean baseClassAttribute) {
        String configClassPackage = exmlConfig.getConfigClassPackage();
        if (configClassPackage == null) {
          IdeaLogger.getInstance(this.getClass()).warn("No config class package set in module " + module.getName() + ", EXML AS3 language injection cancelled.");
          return;
        }
        XmlTag exmlComponentTag = ((XmlFile)attributeValue.getContainingFile()).getRootTag();
        if (exmlComponentTag != null) {

          // find relative path to source root to determine package name:
          VirtualFile packageDir = exmlFile.getParent();
          String packageName = packageDir == null ? "" : getModuleRelativePath(packageDir);
          String className = exmlFile.getNameWithoutExtension();

          StringBuilder codePrefix = new StringBuilder();
          codePrefix.append(String.format("package %s {\n", packageName));

          String superClassName = baseClassAttribute ? null : findSuperClass(exmlComponentTag);

          // find and append imports:
          List<String> imports = findImports(exmlComponentTag);
          if (superClassName != null) {
            imports.add(superClassName);
          }
          for (String importName : imports) {
            codePrefix.append(String.format("import %s;\n", importName));
          }

          codePrefix.append(String.format("public class %s", className));

          String configClassName = CompilerUtils.qName(configClassPackage, CompilerUtils.uncapitalize(className));
          String constructorPrefix = String.format("public function %s(config:%s = null){\n  super(", className, configClassName);
          String constructorSuffix = ");\n}\n";
          StringBuilder codeSuffix = new StringBuilder();

          TextRange textRange;
          if (baseClassAttribute) {
            codePrefix
              .append(" extends ");
            //textRange = attributeValue.getTextRange();
            textRange = new TextRange(1, attributeValue.getTextRange().getLength() - 1);
            codeSuffix
              .append("{")
              .append(constructorPrefix)
              .append("config");
          } else {
            if (superClassName != null) {
              codePrefix.append(String.format(" extends %s", superClassName));
            }
            codePrefix
              .append(" {\n")
              .append(constructorPrefix)
              .append(String.format("%s({x:(", configClassName));
            textRange = new TextRange(2, attributeValue.getTextRange().getLength() - 2);
            codeSuffix
              .append(")})");
          }
          codeSuffix
            .append(constructorSuffix)
            .append("\n}")
            .append("\n}\n");

          injectedLanguagePlaces.addPlace(JavaScriptSupportLoader.ECMA_SCRIPT_L4, textRange, codePrefix.toString(), codeSuffix.toString());
        }
      }
    });
  }

  private static String findSuperClass(XmlTag exmlComponentTag) {
    XmlAttribute baseClassAttribute = exmlComponentTag.getAttribute("baseClass");
    if (baseClassAttribute != null) {
      return baseClassAttribute.getValue();
    }
    XmlTag[] subTags = exmlComponentTag.getSubTags();
    XmlTag componentTag = subTags[subTags.length - 1]; // TODO: should we search for the first sub tag that is not from the exml namespace instead?
    XmlElementDescriptor descriptor = componentTag.getDescriptor();
    if (descriptor instanceof ComponentXmlElementDescriptorProvider.ComponentXmlElementDescriptor) {
      JSClass componentClass = ((ComponentXmlElementDescriptorProvider.ComponentXmlElementDescriptor)descriptor).getComponentClass();
      if (componentClass != null) {
        return componentClass.getQualifiedName();
      }
    }
    return null;
  }

  private static List<String> findImports(XmlTag exmlComponentTag) {
    List<String> imports = new ArrayList<String>();
    for (XmlTag topLevelXmlTag : exmlComponentTag.getSubTags()) {
      if ("import".equals(topLevelXmlTag.getLocalName())) {
        imports.add(topLevelXmlTag.getAttributeValue("class"));
      }
    }
    return imports;
  }

  private static boolean isImportClassAttribute(XmlAttributeValue attributeValue) {
    if (attributeValue.getParent() instanceof XmlAttribute &&
      "class".equals(((XmlAttribute)attributeValue.getParent()).getName())) {
      XmlTag element = (XmlTag)attributeValue.getParent().getParent();
      return "import".equals(element.getLocalName()) &&
        Exmlc.EXML_NAMESPACE_URI.equals(element.getNamespace());
    }
    return false;
  }

  private static String getBaseClassAttribute(XmlAttributeValue attributeValue) {
    if (attributeValue.getParent() instanceof XmlAttribute) {
      XmlAttribute attribute = (XmlAttribute)attributeValue.getParent();
      if ("baseClass".equals(attribute.getName()) &&
        Exmlc.EXML_NAMESPACE_URI.equals(attribute.getParent().getNamespace())) {
        return attributeValue.getValue();
      }
    }
    return null;
  }

  public void disposeComponent() {
    exmlc = null;
    //propc = null;
  }

  @NotNull
  public String getComponentName() {
    return "ExmlProjectComponent";
  }

  public void projectOpened() {
    CompilerManager compilerManager = CompilerManager.getInstance(project);
    compilerManager.addCompiler(exmlc);
  }

  public void projectClosed() {
    CompilerManager compilerManager = CompilerManager.getInstance(project);
    compilerManager.removeCompiler(exmlc);
  }
}
