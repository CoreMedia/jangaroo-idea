package net.jangaroo.ide.idea.exml;

import com.intellij.find.FindManager;
import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.FindUsagesHandlerFactory;
import com.intellij.find.findUsages.FindUsagesManager;
import com.intellij.find.findUsages.FindUsagesOptions;
import com.intellij.find.impl.FindManagerImpl;
import com.intellij.lang.javascript.psi.JSFunction;
import com.intellij.lang.javascript.psi.ecmal4.JSClass;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.Processor;
import com.intellij.xml.util.XmlPsiUtil;
import com.intellij.xml.util.XmlUtil;
import net.jangaroo.exml.api.Exmlc;
import net.jangaroo.ide.idea.jps.exml.ExmlcConfigurationBean;
import net.jangaroo.ide.idea.jps.util.IdeaFileUtils;
import net.jangaroo.utils.CompilerUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Find usages of EXML elements by the EXML file that defines the element.
 */
public class FindExmlUsagesHandlerFactory extends FindUsagesHandlerFactory {

  @Override
  public boolean canFindUsages(@NotNull PsiElement element) {
    if (!element.getContainingFile().getName().endsWith(Exmlc.EXML_SUFFIX)) {
      return false;
    }
    if (element instanceof XmlFile) {
      // find EXML usages of the EXML element defined in this file!
      return true;
    }
    // check for <exml:cfg name="foo" ...>
    if (element instanceof XmlAttributeValue) {
      XmlAttribute xmlAttribute = (XmlAttribute)element.getParent();
      if (Exmlc.EXML_CFG_NAME_ATTRIBUTE.equals(xmlAttribute.getLocalName())) {
        return hasQName(xmlAttribute.getParent(), Exmlc.EXML_NAMESPACE_URI, Exmlc.EXML_CFG_NODE_NAME);
      }
    }
    return false;
  }

  private static boolean hasQName(XmlTag xmlTag, String namespace, String localName) {
    return namespace.equals(xmlTag.getNamespace()) && localName.equals(xmlTag.getLocalName());
  }

  @Nullable
  @Override
  public FindUsagesHandler createFindUsagesHandler(@NotNull PsiElement element, boolean forHighlightUsages) {
    if (canFindUsages(element)) {
      // find module:
      VirtualFile exmlFile = element.getContainingFile().getVirtualFile();
      Project project = element.getProject();
      final Module module = ProjectRootManager.getInstance(project).getFileIndex().getModuleForFile(exmlFile);
      if (module != null) {
        // find EXML facet configuration:
        ExmlFacet exmlFacet = ExmlFacet.ofModule(module);
        if (exmlFacet != null) {
          ExmlcConfigurationBean exmlConfiguration = exmlFacet.getConfiguration().getState();
          // find generated XSD file of this EXML module:
          String configClassPackage = exmlConfiguration.getConfigClassPackage();
          String generatedResourcesDirectory = exmlConfiguration.getGeneratedResourcesDirectory();
          VirtualFile xsdFile = VfsUtil.findFileByIoFile(new File(IdeaFileUtils.toPath(generatedResourcesDirectory),
            configClassPackage + XSD.FILE_SUFFIX), true);
          if (xsdFile != null) {
            XmlFile xsdPsiFile = (XmlFile)PsiManager.getInstance(project).findFile(xsdFile);
            if (xsdPsiFile != null) {
              XmlTag rootTag = xsdPsiFile.getRootTag();
              if (rootTag != null) {
                // collect all generated declarations to find usages of:
                final List<PsiElement> generatedDeclarations = new ArrayList<PsiElement>(3);
                String elementName = CompilerUtils.uncapitalize(exmlFile.getNameWithoutExtension());
                String configClassName = CompilerUtils.qName(configClassPackage, elementName);
                if (element instanceof XmlFile) {
                  addGeneratedDeclarationsOfExmlFile(rootTag, elementName, configClassName,
                    generatedDeclarations);
                } else {
                  addGeneratedDeclarationsOfExmlCfgElement((XmlAttributeValue)element, rootTag, configClassName,
                    generatedDeclarations);
                }
                return getFindUsageHandler(generatedDeclarations, element, forHighlightUsages);
              }
            }
          }
        }
      }
    }
    return null;
  }

  private FindUsagesHandler getFindUsageHandler(List<PsiElement> generatedDeclarations,
                                                PsiElement element, boolean forHighlightUsages) {
    if (generatedDeclarations.isEmpty()) {
      return null;
    }
    FindUsagesManager findUsagesManager = ((FindManagerImpl)FindManager.getInstance(element.getProject())).getFindUsagesManager();
    List<FindUsagesHandler> delegateFindUsageHandlers = new ArrayList<FindUsagesHandler>(3);
    for (PsiElement psiElement : generatedDeclarations) {
      delegateFindUsageHandlers.add(findUsagesManager.getFindUsagesHandler(psiElement, forHighlightUsages));
    }
    return delegateFindUsageHandlers.size() == 1
      ? delegateFindUsageHandlers.get(0)
      : new DelegatingFindUsagesHandler(element, delegateFindUsageHandlers);
  }

  private void addGeneratedDeclarationsOfExmlCfgElement(XmlAttributeValue element,
                                                        XmlTag rootTag,
                                                        String configClassName,
                                                        final List<PsiElement> generatedDeclarations) {
    final String attributeName = element.getValue();
    JSClass configClass = ExmlLanguageInjector.getASClass(element, configClassName);
    if (configClass != null) {
      JSFunction attributeSetter = configClass.findFunctionByNameAndKind(attributeName, JSFunction.FunctionKind.SETTER);
      if (attributeSetter != null) {
        generatedDeclarations.add(attributeSetter);
      }
      JSFunction attributeGetter = configClass.findFunctionByNameAndKind(attributeName, JSFunction.FunctionKind.GETTER);
      if (attributeGetter != null) {
        generatedDeclarations.add(attributeGetter);
      }
    }
    final int generatedDeclarationsSize = generatedDeclarations.size();
    XmlTag[] typeDeclarations = rootTag.findSubTags(XSD.COMPLEX_TYPE_ELEMENT_NAME, XmlUtil.XML_SCHEMA_URI);
    for (XmlTag typeDeclaration : typeDeclarations) {
      if (configClassName.equals(typeDeclaration.getAttributeValue(XSD.NAME_ATTRIBUTE_NAME))) {
        // find attribute and element declaration
        XmlPsiUtil.processXmlElementChildren(typeDeclaration, new PsiElementProcessor() {
          @Override
          public boolean execute(@NotNull PsiElement element) {
            XmlTag xmlTag = null;
            if (element instanceof XmlAttributeValue) {
              XmlAttribute xmlAttribute = (XmlAttribute)element.getParent();
              if (XSD.NAME_ATTRIBUTE_NAME.equals(xmlAttribute.getLocalName()) && attributeName.equals(xmlAttribute.getValue())) {
                if (hasQName(xmlAttribute.getParent(), XmlUtil.XML_SCHEMA_URI, XSD.ATTRIBUTE_ELEMENT_NAME)) {
                  xmlTag = xmlAttribute.getParent();
                }
              }
            } else if (element instanceof XmlTag && hasQName((XmlTag)element, XmlUtil.XML_SCHEMA_URI, XSD.ELEMENT_ELEMENT_NAME)) {
              xmlTag = (XmlTag)element;
            }
            if (xmlTag != null && attributeName.equals(xmlTag.getAttributeValue(XSD.NAME_ATTRIBUTE_NAME))) {
              generatedDeclarations.add(xmlTag);
            }
            return generatedDeclarations.size() < generatedDeclarationsSize + 2;
          }
        }, true);
        break;
      }
    }
  }

  private void addGeneratedDeclarationsOfExmlFile(XmlTag xsdRootTag, String elementName, String configClassName, List<PsiElement> generatedDeclarations) {
    JSClass configClass = ExmlLanguageInjector.getASClass(xsdRootTag, configClassName);
    if (configClass != null) {
      String targetClassName = ExmlElementGotoDeclarationHandler.findTargetClassName(configClass);
      if (targetClassName != null) {
        JSClass targetClass = ExmlLanguageInjector.getASClass(configClass, targetClassName);
        if (targetClass != null) {
          generatedDeclarations.add(targetClass);
        }
      }
      generatedDeclarations.add(configClass);
    }
    // find <xs:element name="<elementName>" ...> in xsdPsiFile and find its usages!
    XmlTag[] elementDeclarations = xsdRootTag.findSubTags("element", XmlUtil.XML_SCHEMA_URI);
    for (XmlTag elementDeclaration : elementDeclarations) {
      if (elementName.equals(elementDeclaration.getAttributeValue("name"))) {
        generatedDeclarations.add(elementDeclaration);
        break;
      }
    }
  }

  private static class DelegatingFindUsagesHandler extends FindUsagesHandler {

    private List<FindUsagesHandler> delegates;

    private DelegatingFindUsagesHandler(@NotNull PsiElement psiElement, List<FindUsagesHandler> delegates) {
      super(psiElement);
      this.delegates = delegates;
    }

    @NotNull
    @Override
    public PsiElement[] getPrimaryElements() {
      List<PsiElement> result = new ArrayList<PsiElement>();
      for (FindUsagesHandler delegate : delegates) {
        result.addAll(Arrays.asList(delegate.getPrimaryElements()));
      }
      return result.toArray(new PsiElement[result.size()]);
    }

    @NotNull
    @Override
    public PsiElement[] getSecondaryElements() {
      List<PsiElement> result = new ArrayList<PsiElement>();
      for (FindUsagesHandler delegate : delegates) {
        result.addAll(Arrays.asList(delegate.getSecondaryElements()));
      }
      return result.toArray(new PsiElement[result.size()]);
    }

    @Override
    public boolean processElementUsages(@NotNull PsiElement element, @NotNull Processor<UsageInfo> processor, @NotNull FindUsagesOptions options) {
      boolean result = true;
      for (FindUsagesHandler delegate : delegates) {
        result = result && delegate.processElementUsages(element, processor, options);
      }
      return result;
    }

    @Override
    public boolean processUsagesInText(@NotNull PsiElement element, @NotNull Processor<UsageInfo> processor, @NotNull GlobalSearchScope searchScope) {
      boolean result = true;
      for (FindUsagesHandler delegate : delegates) {
        result = result && delegate.processUsagesInText(element, processor, searchScope);
      }
      return result;
    }

    @Override
    public Collection<PsiReference> findReferencesToHighlight(@NotNull PsiElement target, @NotNull SearchScope searchScope) {
      List<PsiReference> result = new ArrayList<PsiReference>();
      for (FindUsagesHandler delegate : delegates) {
        result.addAll((delegate.findReferencesToHighlight(target, searchScope)));
      }
      return result;
    }

  }
}
