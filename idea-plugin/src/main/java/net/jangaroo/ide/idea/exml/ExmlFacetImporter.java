package net.jangaroo.ide.idea.exml;

import com.intellij.javaee.ExternalResourceManager;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PairConsumer;
import net.jangaroo.exml.api.Exmlc;
import net.jangaroo.exml.config.ValidationMode;
import net.jangaroo.ide.idea.jps.exml.ExmlcConfigurationBean;
import net.jangaroo.utils.CompilerUtils;
import org.jdom.Element;
import org.jetbrains.idea.maven.importing.FacetImporter;
import org.jetbrains.idea.maven.importing.MavenModifiableModelsProvider;
import org.jetbrains.idea.maven.importing.MavenRootModelAdapter;
import org.jetbrains.idea.maven.model.MavenPlugin;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectChanges;
import org.jetbrains.idea.maven.project.MavenProjectsProcessorTask;
import org.jetbrains.idea.maven.project.MavenProjectsTree;
import org.jetbrains.jps.model.java.JavaResourceRootType;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static net.jangaroo.ide.idea.JangarooFacetImporter.EXML_MAVEN_PLUGIN_ARTIFACT_ID;
import static net.jangaroo.ide.idea.jps.JpsJangarooSdkType.JANGAROO_GROUP_ID;
import static net.jangaroo.ide.idea.JangarooFacetImporter.findDeclaredJangarooPlugin;
import static net.jangaroo.ide.idea.jps.util.IdeaFileUtils.toIdeaUrl;
import static net.jangaroo.ide.idea.jps.util.IdeaFileUtils.toPath;

/**
 * A Facet-from-Maven Importer for the EXML Facet type.
 */
public class ExmlFacetImporter extends FacetImporter<ExmlFacet, ExmlFacetConfiguration, ExmlFacetType> {
  private static final String DEFAULT_EXML_FACET_NAME = "EXML";

  public ExmlFacetImporter() {
    super(JANGAROO_GROUP_ID, EXML_MAVEN_PLUGIN_ARTIFACT_ID, ExmlFacetType.INSTANCE, DEFAULT_EXML_FACET_NAME);
  }

  public boolean isApplicable(MavenProject mavenProjectModel) {
    return findExmlMavenPlugin(mavenProjectModel) != null;
  }

  private MavenPlugin findExmlMavenPlugin(MavenProject mavenProjectModel) {
    return findDeclaredJangarooPlugin(mavenProjectModel, EXML_MAVEN_PLUGIN_ARTIFACT_ID);
  }

  protected void setupFacet(ExmlFacet exmlFacet, MavenProject mavenProjectModel) {
    //System.out.println("setupFacet called!");
  }

  private String getConfigurationValue(MavenProject mavenProjectModel, String configName, String defaultValue) {
    String value = getConfigurationValue(configName, mavenProjectModel.getPluginConfiguration(JANGAROO_GROUP_ID, EXML_MAVEN_PLUGIN_ARTIFACT_ID));
    if (value == null) {
      value = getConfigurationValue(configName, mavenProjectModel.getPluginGoalConfiguration(JANGAROO_GROUP_ID, EXML_MAVEN_PLUGIN_ARTIFACT_ID, "exml"));
    }
    return value == null ? defaultValue : value;
  }

  private String getConfigurationValue(String configName, Element compileConfiguration) {
    if (compileConfiguration != null) {
      Element compileConfigurationChild = compileConfiguration.getChild(configName);
      if (compileConfigurationChild != null) {
        return compileConfigurationChild.getTextTrim();
      }
    }
    return null;
  }

  @Override
  protected void reimportFacet(MavenModifiableModelsProvider modelsProvider, Module module,
                               MavenRootModelAdapter rootModel, ExmlFacet exmlFacet, MavenProjectsTree mavenTree,
                               MavenProject mavenProjectModel, MavenProjectChanges changes,
                               Map<MavenProject, String> mavenProjectToModuleName, List<MavenProjectsProcessorTask> postTasks) {
    //System.out.println("reimportFacet called!");
    ExmlFacetConfiguration exmlFacetConfiguration = exmlFacet.getConfiguration();
    ExmlcConfigurationBean exmlConfig = exmlFacetConfiguration.getState();
    exmlConfig.setGeneratedSourcesDirectory(toIdeaUrl(mavenProjectModel.getGeneratedSourcesDirectory(false) + "/joo"));
    exmlConfig.setGeneratedTestSourcesDirectory(toIdeaUrl(mavenProjectModel.getGeneratedSourcesDirectory(true) + "/joo"));
    exmlConfig.setGeneratedResourcesDirectory(toIdeaUrl(getTargetOutputPath(mavenProjectModel, "generated-resources")));
    String configClassPackage = getConfigurationValue(mavenProjectModel, "configClassPackage", "");
    exmlConfig.setConfigClassPackage(configClassPackage);
    String validationMode = getConfigurationValue(mavenProjectModel, "validationMode", "off");
    try {
      exmlConfig.validationMode = ValidationMode.valueOf(validationMode.toUpperCase());
    } catch (IllegalArgumentException e) {
      Notifications.Bus.notify(new Notification("Maven", "Invalid Jangaroo EXML Configuration",
        "Illegal value for &lt;validationMode>: '" + validationMode + "' in Maven POM " +
          mavenProjectModel.getDisplayName() +", falling back to 'off'.",
        NotificationType.WARNING));
      exmlConfig.validationMode = ValidationMode.OFF;
    }

    final Map<String, String> resourceMap = getXsdResourcesOfModule(module);
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            ExternalResourceManager externalResourceManager = ExternalResourceManager.getInstance();
            for (Map.Entry<String, String> uri2filename : resourceMap.entrySet()) {
              externalResourceManager.removeResource(uri2filename.getKey());
              externalResourceManager.addResource(uri2filename.getKey(), uri2filename.getValue());
            }
          }
        });
      }
    });
  }

  @Override
  public void collectSourceRoots(MavenProject mavenProject, PairConsumer<String, JpsModuleSourceRootType<?>> result) {
    // TODO: peek into Maven config of ext-xml goal!
//    result.consume("target/generated-sources/joo", JavaSourceRootType.SOURCE); // seems to be built-in
    result.consume("target/generated-test-sources/joo", JavaSourceRootType.TEST_SOURCE);
    result.consume("target/generated-resources", JavaResourceRootType.RESOURCE);
  }

  private Map<String, String> getXsdResourcesOfModule(Module module) {
    // Collect the XSD resource mappings of this modules and all its dependent component suites.
    //System.out.println("Scanning dependencies of " + moduleName + " for component suite XSDs...");
    final Map<String, String> resourceMap = new LinkedHashMap<String, String>();
    OrderEntry[] orderEntries = ModuleRootManager.getInstance(module).getOrderEntries();
    for (OrderEntry orderEntry : orderEntries) {
      try {
        if (orderEntry instanceof ModuleOrderEntry) {
          ExmlcConfigurationBean exmlConfig = ExmlFacet.getExmlConfig(((ModuleOrderEntry)orderEntry).getModule());
          mapXsdResources(resourceMap, exmlConfig);
        } else {
          String zipFileName = findDependentModuleZipFileName(orderEntry);
          if (zipFileName != null) {
            ZipFile zipFile = new ZipFile(zipFileName);
            String zipFilePath = zipFileName + "!/";
            Set<ZipEntry> xsdZipEntries = findXsdZipEntries(zipFile);
            for (ZipEntry xsdZipEntry : xsdZipEntries) {
              mapXsdResource(resourceMap, zipFilePath, xsdZipEntry.getName());
            }
          }
        }
      } catch (IOException e) {
        // ignore
      }
    }
    ExmlcConfigurationBean exmlConfig = ExmlFacet.getExmlConfig(module);
    mapXsdResources(resourceMap, exmlConfig);
    return resourceMap;
  }

  private static void mapXsdResources(Map<String, String> resourceMap, ExmlcConfigurationBean exmlConfig) {
    if (exmlConfig != null) {
      String generatedResourcesPath = toPath(exmlConfig.getGeneratedResourcesDirectory()) + File.separator;
      File generatedResourcesDirectory = new File(generatedResourcesPath);
      if (generatedResourcesDirectory.exists()) {
        String[] xsdFiles = generatedResourcesDirectory.list(new FilenameFilter() {
          @Override
          public boolean accept(File dir, String name) {
            return name.toLowerCase().endsWith(XSD.FILE_SUFFIX);
          }
        });
        for (String xsdFile : xsdFiles) {
          mapXsdResource(resourceMap, generatedResourcesPath, xsdFile);
        }
      }
    }
  }

  private static void mapXsdResource(Map<String, String> resourceMap, String path, String xsdFileName) {
    String namespace = Exmlc.EXML_CONFIG_URI_PREFIX + CompilerUtils.removeExtension(xsdFileName);
    resourceMap.put(namespace, path + xsdFileName);
  }

  private static String findDependentModuleZipFileName(OrderEntry orderEntry) throws IOException {
    VirtualFile[] files = orderEntry.getFiles(OrderRootType.CLASSES);
    // check that library is not empty:
    for (VirtualFile file : files) {
      // TODO: make it work for classes, not only for jars!
      String filename = file.getPath();
      if (filename.endsWith("!/")) { // it is a jar:
        return filename.substring(0, filename.length() - "!/".length());
      }
    }
    return null;
  }

  private static Set<ZipEntry> findXsdZipEntries(ZipFile zipFile) throws IOException {
    // find a *.xsd in jar's root folder:
    Enumeration<? extends ZipEntry> enumeration = zipFile.entries();
    Set<ZipEntry> result = new LinkedHashSet<ZipEntry>();
    while (enumeration.hasMoreElements()) {
      ZipEntry zipEntry = enumeration.nextElement();
      if (!zipEntry.isDirectory() && zipEntry.getName().indexOf('/') == -1 && zipEntry.getName().endsWith(".xsd")) {
        result.add(zipEntry);
      }
    }
    return result;
  }

}