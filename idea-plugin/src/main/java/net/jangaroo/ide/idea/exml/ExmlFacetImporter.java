package net.jangaroo.ide.idea.exml;

import com.intellij.javaee.ExternalResourceManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import net.jangaroo.exml.api.Exmlc;
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

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static net.jangaroo.ide.idea.JangarooFacetImporter.EXML_MAVEN_PLUGIN_ARTIFACT_ID;
import static net.jangaroo.ide.idea.JangarooFacetImporter.JANGAROO_GROUP_ID;
import static net.jangaroo.ide.idea.JangarooFacetImporter.findDeclaredJangarooPlugin;
import static net.jangaroo.ide.idea.util.IdeaFileUtils.toIdeaUrl;
import static net.jangaroo.ide.idea.util.IdeaFileUtils.toPath;

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
    exmlConfig.setSourceDirectory(toIdeaUrl(mavenProjectModel.getSources().get(0)));
    exmlConfig.setGeneratedSourcesDirectory(toIdeaUrl(mavenProjectModel.getGeneratedSourcesDirectory(false) + "/joo"));
    exmlConfig.setGeneratedResourcesDirectory(toIdeaUrl(getTargetOutputPath(mavenProjectModel, "generated-resources")));
    String configClassPackage = getConfigurationValue(mavenProjectModel, "configClassPackage", "");
    exmlConfig.setConfigClassPackage(configClassPackage);

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

  public void collectSourceFolders(MavenProject mavenProject, List<String> result) {
    // TODO: peek into Maven config of ext-xml goal!
    result.add("target/generated-sources/joo");
  }

  private Map<String, String> getXsdResourcesOfModule(Module module) {
    // Collect the XSD resource mappings of this modules and all its dependent component suites.
    //System.out.println("Scanning dependencies of " + moduleName + " for component suite XSDs...");
    final Map<String, String> resourceMap = new LinkedHashMap<String, String>();
    OrderEntry[] orderEntries = ModuleRootManager.getInstance(module).getOrderEntries();
    for (OrderEntry orderEntry : orderEntries) {
      try {
        if (orderEntry instanceof ModuleOrderEntry) {
          ExmlcConfigurationBean exmlConfig = ExmlCompiler.getExmlConfig(((ModuleOrderEntry)orderEntry).getModule());
          if (exmlConfig != null) {
            resourceMap.put(exmlConfig.getNamespace(), toPath(exmlConfig.getXsdFilename()));
          }
        } else {
          String zipFileName = ExmlCompiler.findDependentModuleZipFileName(orderEntry);
          if (zipFileName != null) {
            ZipFile zipFile = new ZipFile(zipFileName);
            ZipEntry xsdZipEntry = ExmlCompiler.findXsdZipEntry(zipFile);
            if (xsdZipEntry != null) {
              String namespace = Exmlc.EXML_CONFIG_URI_PREFIX + CompilerUtils.removeExtension(xsdZipEntry.getName());
              String filename = zipFileName + "!/" + xsdZipEntry.getName();
              resourceMap.put(namespace, filename);
            }
          }
        }
      } catch (IOException e) {
        // ignore
      }
    }
    ExmlcConfigurationBean exmlConfig = ExmlCompiler.getExmlConfig(module);
    if (exmlConfig != null) {
      resourceMap.put(exmlConfig.getNamespace(), toPath(exmlConfig.getXsdFilename()));
    }
    return resourceMap;
  }

}