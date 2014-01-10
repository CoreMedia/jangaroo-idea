package net.jangaroo.ide.idea.exml;

import com.intellij.idea.IdeaLogger;
import com.intellij.lang.javascript.psi.ecmal4.JSAttribute;
import com.intellij.lang.javascript.psi.ecmal4.JSAttributeList;
import com.intellij.lang.javascript.psi.ecmal4.JSClass;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.xml.XmlElementDescriptorProvider;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.impl.schema.XmlElementDescriptorImpl;
import com.intellij.xml.util.XmlUtil;
import net.jangaroo.exml.api.Exmlc;
import net.jangaroo.exml.utils.ExmlUtils;
import net.jangaroo.utils.CompilerUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


/**
 * A custom XmlElementDescriptorProvider to support navigation from component/attribute elements to
 * the corresponding EXML or AS files.
 */
public class ComponentXmlElementDescriptorProvider implements XmlElementDescriptorProvider {
  public XmlElementDescriptor getDescriptor(XmlTag xmltag) {
    if (xmltag.isValid()) {
      String namespace = xmltag.getNamespace();
      if (xmltag.getContainingFile().getName().endsWith(Exmlc.EXML_SUFFIX)
        && !Exmlc.EXML_NAMESPACE_URI.equals(namespace)) {
        XmlNSDescriptor xmlNSDescriptor = xmltag.getNSDescriptor(namespace, false);
        XmlElementDescriptor xmlElementDescriptor = null;
        if (xmlNSDescriptor != null) {
          try {
            xmlElementDescriptor = xmlNSDescriptor.getElementDescriptor(xmltag);
          } catch (Exception e) {
            // assertion isValid() sometimes fails: log & ignore!
            IdeaLogger.getInstance(this.getClass()).warn("Cannot determine element descriptor of XML element " + xmltag.getName() + " in file " + xmltag.getContainingFile().getName(), e);
          }
        }
        if (xmlElementDescriptor == null) {
          xmlElementDescriptor = XmlUtil.findXmlDescriptorByType(xmltag);
        }
        return xmlElementDescriptor instanceof XmlElementDescriptorImpl ? new ComponentXmlElementDescriptor((XmlElementDescriptorImpl)xmlElementDescriptor) : null;
      }
    }
    return null;
  }

  public static class ComponentXmlElementDescriptor extends XmlElementDescriptorImpl {

    @SuppressWarnings({"UnusedDeclaration"})
    public ComponentXmlElementDescriptor() {
      super();
    }

    public ComponentXmlElementDescriptor(@NotNull XmlElementDescriptorImpl xmlElementDescriptor) {
      super(xmlElementDescriptor.getDeclaration());
      NSDescriptor = xmlElementDescriptor.getNSDescriptor();
    }

    public String getTargetClassName() {
      XmlTag declaration = super.getDeclaration();
      // only check top-level declarations:
      if (declaration != null) {
        XmlTag parentTag = declaration.getParentTag();
        if (parentTag != null
          && "schema".equals(parentTag.getLocalName())
          && "http://www.w3.org/2001/XMLSchema".equals(parentTag.getNamespace())) {
          String packageName = ExmlUtils.parsePackageFromNamespace(getNamespace());
          if (packageName != null) {
            String className = CompilerUtils.qName(packageName, getName());
            JSClass asClass = ExmlLanguageInjector.getASClass(parentTag, className);
            if (asClass != null && asClass.isValid()) {
              // found ActionScript class.
              String targetClassName = className;
              // could be a config class with an [ExtConfig(target="...")] annotation:
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
          }
        }
      }
      return null;
    }

    public XmlTag getDeclaration() {
      XmlTag declaration = super.getDeclaration();
      String targetClassName = getTargetClassName();
      if (targetClassName != null) {
        // always prefer EXML file:
        Project project = declaration.getProject();
        VirtualFile exmlFile = findExmlFile(project, targetClassName);
        if (exmlFile != null) {
          PsiFile file = PsiManager.getInstance(project).findFile(exmlFile);
          if (file instanceof XmlFile && file.isValid()) {
            XmlTag rootTag = ((XmlFile)file).getRootTag();
            if (rootTag != null) {
              return rootTag;
            }
          }
        }
      }
      return declaration;
    }

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

    private static @Nullable VirtualFile findExmlFile(@NotNull VirtualFile[] sourceRoots, @NotNull String exmlFileName) {
      for (VirtualFile contentRoot : sourceRoots) {
        VirtualFile exmlFile = contentRoot.findFileByRelativePath(exmlFileName);
        if (exmlFile != null) {
          return exmlFile;
        }
      }
      return null;
    }

    public XmlNSDescriptor getNSDescriptor() {
      if (NSDescriptor == null || !NSDescriptor.getDeclaration().isValid()) {
        XmlFile xmlfile = XmlUtil.getContainingFile(super.getDeclaration()); // use old getDeclaration() behavior!
        if (xmlfile != null) {
          XmlDocument xmldocument = xmlfile.getDocument();
          if (xmldocument != null) {
            return NSDescriptor = (XmlNSDescriptor)xmldocument.getMetaData();
          }
        }
        return null;
      }
      return NSDescriptor;
    }

  }
}
