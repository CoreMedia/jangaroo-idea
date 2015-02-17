package net.jangaroo.ide.idea;

import com.intellij.javaee.facet.JavaeeFacet;
import com.intellij.javaee.ui.packaging.ExplodedWarArtifactType;
import com.intellij.javaee.ui.packaging.JavaeeFacetResourcesPackagingElement;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactManager;
import com.intellij.packaging.artifacts.ModifiableArtifact;
import com.intellij.packaging.artifacts.ModifiableArtifactModel;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.elements.PackagingElementResolvingContext;
import com.intellij.packaging.impl.elements.ArchivePackagingElement;
import com.intellij.packaging.impl.elements.ModuleOutputPackagingElement;
import com.intellij.util.PairConsumer;
import net.jangaroo.ide.idea.jps.JoocConfigurationBean;
import net.jangaroo.ide.idea.jps.JpsJangarooSdkType;
import net.jangaroo.jooc.config.PublicApiViolationsMode;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.importing.FacetImporter;
import org.jetbrains.idea.maven.importing.MavenModifiableModelsProvider;
import org.jetbrains.idea.maven.importing.MavenRootModelAdapter;
import org.jetbrains.idea.maven.model.MavenPlugin;
import org.jetbrains.idea.maven.project.MavenConsole;
import org.jetbrains.idea.maven.project.MavenEmbeddersManager;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectChanges;
import org.jetbrains.idea.maven.project.MavenProjectsProcessorTask;
import org.jetbrains.idea.maven.project.MavenProjectsTree;
import org.jetbrains.idea.maven.project.SupportedRequestType;
import org.jetbrains.idea.maven.utils.MavenJDOMUtil;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;
import org.jetbrains.idea.maven.utils.MavenUtil;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static net.jangaroo.ide.idea.jps.util.IdeaFileUtils.toIdeaUrl;

/**
 * A Facet-from-Maven Importer for the Jangaroo Facet type.
 */
public class JangarooFacetImporter extends FacetImporter<JangarooFacet, JangarooFacetConfiguration, JangarooFacetType> {
  private static final String JANGAROO_MAVEN_PLUGIN_ARTIFACT_ID = "jangaroo-maven-plugin";
  public static final String EXML_MAVEN_PLUGIN_ARTIFACT_ID = "exml-maven-plugin";
  private static final String JANGAROO_PACKAGING_TYPE = "jangaroo";
  public static final String DEFAULT_JANGAROO_FACET_NAME = "Jangaroo";
  private static final String JOO_CLASSES_PATH = "joo/classes";

  public JangarooFacetImporter() {
    super(JpsJangarooSdkType.JANGAROO_GROUP_ID, JANGAROO_MAVEN_PLUGIN_ARTIFACT_ID, JangarooFacetType.INSTANCE, DEFAULT_JANGAROO_FACET_NAME);
  }

  // we cannot use MavenProject#findPlugin(), because it also searches in <pluginManagement>:
  public static MavenPlugin findDeclaredJangarooPlugin(MavenProject mavenProject, @Nullable String artifactId) {
    for (MavenPlugin each : mavenProject.getDeclaredPlugins()) {
      if (each.getMavenId().equals(JpsJangarooSdkType.JANGAROO_GROUP_ID, artifactId)) {
        return each;
      }
    }
    return null;
  }

  public boolean isApplicable(MavenProject mavenProjectModel) {
    MavenPlugin jangarooMavenPlugin = findJangarooMavenPlugin(mavenProjectModel);
    if (jangarooMavenPlugin == null && JANGAROO_PACKAGING_TYPE.equals(mavenProjectModel.getPackaging())) {
      Notifications.Bus.notify(new Notification("jangaroo", "Jangaroo Facet not created/updated",
        "Module " + mavenProjectModel.getMavenId() + " uses packaging type 'jangaroo', " +
        "but no jangaroo-maven-plugin or exml-maven-plugin was found. Try repeating 'Reimport All Maven Projects'.",
        NotificationType.WARNING));
    }
    // any of the two Jangaroo Maven plugins has to be configured explicitly:
    return jangarooMavenPlugin != null;
  }

  /**
   * Find jangaroo-maven-plugin, or, if not present, exml-maven-plugin, which also activates the
   * Jangaroo Maven lifecycle and thus the Jangaroo compiler.
   * @param mavenProjectModel IDEA's Maven project model
   * @return jangaroo-maven-plugin or exml-maven-plugin
   */
  private MavenPlugin findJangarooMavenPlugin(MavenProject mavenProjectModel) {
    MavenPlugin jangarooPlugin = findDeclaredJangarooPlugin(mavenProjectModel, JANGAROO_MAVEN_PLUGIN_ARTIFACT_ID);
    if (jangarooPlugin == null) {
      jangarooPlugin = findDeclaredJangarooPlugin(mavenProjectModel, EXML_MAVEN_PLUGIN_ARTIFACT_ID);
    }
    return jangarooPlugin;
  }

  @Override
  public void getSupportedDependencyTypes(Collection<String> result, SupportedRequestType type) {
    super.getSupportedDependencyTypes(result, type);
    result.add(JANGAROO_PACKAGING_TYPE);
  }

  @Override
  protected void setupFacet(JangarooFacet f, MavenProject mavenProject) {
    //System.out.println("setupFacet called!");
  }

  private String getConfigurationValue(MavenProject mavenProjectModel, String configName, @Nullable String defaultValue) {
    String value = null;
    Element compileConfiguration = mavenProjectModel.getPluginGoalConfiguration(JpsJangarooSdkType.JANGAROO_GROUP_ID, JANGAROO_MAVEN_PLUGIN_ARTIFACT_ID, "compile");
    if (compileConfiguration != null) {
      Element compileConfigurationChild = compileConfiguration.getChild(configName);
      if (compileConfigurationChild != null) {
        value = compileConfigurationChild.getTextTrim();
      }
    }
    if (value == null) {
      value = findGoalConfigValue(mavenProjectModel, "compile", configName);
      if (value == null) {
        value = findConfigValue(mavenProjectModel, configName);
      }
    }
    return value != null && value.length() > 0 ? value : defaultValue;
  }

  private boolean getBooleanConfigurationValue(MavenProject mavenProjectModel, String configName, boolean defaultValue) {
    String value = getConfigurationValue(mavenProjectModel, configName, String.valueOf(defaultValue));
    return Boolean.valueOf(value);
  }

  @Override
  protected void reimportFacet(MavenModifiableModelsProvider modelsProvider, Module module,
                               MavenRootModelAdapter rootModel, JangarooFacet jangarooFacet,
                               MavenProjectsTree mavenTree, MavenProject mavenProjectModel,
                               MavenProjectChanges changes, Map<MavenProject, String> mavenProjectToModuleName,
                               List<MavenProjectsProcessorTask> postTasks) {
    //System.out.println("reimportFacet called!");
    JangarooFacetConfiguration jangarooFacetConfiguration = jangarooFacet.getConfiguration();
    JoocConfigurationBean jooConfig = jangarooFacetConfiguration.getState();
    MavenPlugin jangarooMavenPlugin = findJangarooMavenPlugin(mavenProjectModel);
    String jangarooSdkVersion = jangarooMavenPlugin.getVersion();
    String sdkHomePath = jangarooSdkHomePath(JpsJangarooSdkType.JANGAROO_COMPILER_API_ARTIFACT_ID, jangarooSdkVersion);
    Sdk jangarooSdk = JangarooSdkUtils.createOrGetSdk(JangarooSdkType.getInstance(), sdkHomePath);
    if (jangarooSdk == null) {
      if (jangarooSdkVersion == null) {
        Notifications.Bus.notify(new Notification("Maven", "Jangaroo Version Not Found",
          "No version found for Jangaroo SDK in Maven POM " + mavenProjectModel.getDisplayName() +
            ", no Jangaroo facet created.", NotificationType.WARNING));
        return;
      }
      jooConfig.jangarooSdkName = "Jangaroo SDK " + jangarooSdkVersion;
    } else {
      jooConfig.jangarooSdkName = jangarooSdk.getName();
      if (jangarooSdk.getVersionString() == null) {
        Notifications.Bus.notify(new Notification("Jangaroo", "Incompatible Jangaroo Version",
          "Jangaroo compiler version " + jangarooSdkVersion + " is not compatible with this Jangaroo IDEA plugin. "
        + "Either use another IDEA plugin or change the Jangaroo Maven plugin version in " + mavenProjectModel.getDisplayName(),
          NotificationType.WARNING));
        return;
      }
      jangarooSdkVersion = jangarooSdk.getVersionString();
    }
    jooConfig.allowDuplicateLocalVariables = getBooleanConfigurationValue(mavenProjectModel, "allowDuplicateLocalVariables", jooConfig.allowDuplicateLocalVariables);
    jooConfig.verbose = getBooleanConfigurationValue(mavenProjectModel, "verbose", false);
    jooConfig.enableAssertions = getBooleanConfigurationValue(mavenProjectModel, "enableAssertions", false);
    // "debug" (boolean; true), "debuglevel" ("none", "lines", "source"; "source")
    boolean isWar = "war".equals(mavenProjectModel.getPackaging());

    String outputDirectory = findConfigValue(mavenProjectModel, "outputDirectory");
    if (outputDirectory == null) {
      outputDirectory = isWar ? "target/jangaroo-output" : mavenProjectModel.getOutputDirectory();
    }
    File outputDir = new File(outputDirectory);
    if (!outputDir.isAbsolute()) {
      outputDir = new File(mavenProjectModel.getDirectory(), outputDirectory);
    }

    String jooClassesPath = JOO_CLASSES_PATH;
    boolean isJangaroo2or3 = jangarooSdkVersion != null && (jangarooSdkVersion.startsWith("2.") || jangarooSdkVersion.startsWith("3"));
    if (isJangaroo2or3 && !isWar) {
      jooClassesPath = "META-INF/resources/" + jooClassesPath;
    }
    jooConfig.outputDirectory = toIdeaUrl(new File(outputDir, jooClassesPath).getAbsolutePath());

    String apiOutputDirectory = getConfigurationValue(mavenProjectModel, "apiOutputDirectory", null);
    jooConfig.apiOutputDirectory = isWar ? null : toIdeaUrl(apiOutputDirectory != null ? apiOutputDirectory : new File(outputDir, "META-INF/joo-api").getAbsolutePath());

    String testOutputDirectory = findConfigValue(mavenProjectModel, "testOutputDirectory");
    if (testOutputDirectory == null) {
      testOutputDirectory = isWar ? "target/jangaroo-test-output" : mavenProjectModel.getTestOutputDirectory();
    }
    File testOutputDir = new File(testOutputDirectory);
    if (!testOutputDir.isAbsolute()) {
      testOutputDir = new File(mavenProjectModel.getDirectory(), testOutputDirectory);
    }
    jooConfig.testOutputDirectory = toIdeaUrl(new File(testOutputDir, JOO_CLASSES_PATH).getAbsolutePath());

    String publicApiViolationsMode = getConfigurationValue(mavenProjectModel, "publicApiViolations", "warn");
    try {
      jooConfig.publicApiViolationsMode = PublicApiViolationsMode.valueOf(publicApiViolationsMode.toUpperCase());
    } catch (IllegalArgumentException e) {
      Notifications.Bus.notify(new Notification("Maven", "Invalid Jangaroo Configuration",
        "Illegal value for &lt;publicApiViolations>: '" + publicApiViolationsMode + "' in Maven POM " +
          mavenProjectModel.getDisplayName() +", falling back to 'warn'.",
        NotificationType.WARNING));
      jooConfig.publicApiViolationsMode = PublicApiViolationsMode.WARN;
    }

    if (isWar) {
      postTasks.add(new AddJangarooPackagingOutputToExplodedWebArtifactsTask(jangarooFacet));
    }
  }

  private static String jangarooSdkHomePath(String artifactId, String version) {
    File localRepository = MavenUtil.resolveLocalRepository(null, null, null);
    File jarFile = JpsJangarooSdkType.getJangarooArtifact(localRepository, artifactId, version);
    return jarFile.getParentFile().getAbsolutePath();
  }

  @Override
  public void collectSourceRoots(MavenProject mavenProject, PairConsumer<String, JpsModuleSourceRootType<?>> result) {
    collectSourceOrTestFolders(mavenProject, JavaSourceRootType.SOURCE, "compile", "src/main/joo", result);
    collectSourceOrTestFolders(mavenProject, JavaSourceRootType.TEST_SOURCE, "testCompile", "src/test/joo", result);
  }

  private void collectSourceOrTestFolders(MavenProject mavenProject, JavaSourceRootType type, String goal, String defaultDir,
                                          PairConsumer<String, JpsModuleSourceRootType<?>> result) {
    Element goalConfiguration = getGoalConfig(mavenProject, goal);
    if (goalConfiguration != null) {
      List<String> mvnSrcDirs = MavenJDOMUtil.findChildrenValuesByPath(goalConfiguration, "sources", "directory");
      if (!mvnSrcDirs.isEmpty()) {
        for (String mvnSrcDir : mvnSrcDirs) {
          result.consume(mvnSrcDir, type);
        }
        return;
      }
    }
    result.consume(defaultDir, type);
  }

  private static class AddJangarooPackagingOutputToExplodedWebArtifactsTask implements MavenProjectsProcessorTask {
    private final JangarooFacet jangarooFacet;

    private AddJangarooPackagingOutputToExplodedWebArtifactsTask(JangarooFacet jangarooFacet) {
      this.jangarooFacet = jangarooFacet;
    }

    public void perform(final Project project, MavenEmbeddersManager mavenEmbeddersManager, MavenConsole mavenConsole, MavenProgressIndicator mavenProgressIndicator) throws MavenProcessCanceledException {
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          Module webModule = jangarooFacet.getModule();
          // for this Jangaroo-enabled Web app, add all Jangaroo-dependent modules' Jangaroo compiler output.

          // find the IDEA exploded Web artifact for this Jangaroo-enabled Web app module:
          final Artifact artifact = getExplodedWebArtifact(webModule);
          if (artifact != null) {
            final ArtifactManager artifactManager = ArtifactManager.getInstance(project);

            // add the remaining modules' Jangaroo compile output to the Web app's root directory:
            CompositePackagingElement<?> webInfDir = artifact.getRootElement().findCompositeChild("WEB-INF");
            if (webInfDir != null) {
              CompositePackagingElement<?> libDir = webInfDir.findCompositeChild("lib");
              if (libDir != null) {
                PackagingElementResolvingContext resolvingContext = artifactManager.getResolvingContext();
                Collection<ArchivePackagingElement> jangarooArchives = findJangarooArchives(resolvingContext, libDir);
                if (!jangarooArchives.isEmpty()) {
                  createJangarooPackagingOutputElements(artifact, resolvingContext, jangarooArchives);
                  libDir.removeChildren(jangarooArchives);
                  ensureBuildOnMake(artifact, artifactManager);
                }
              }
            }
          }
        }
      });
    }

    private static void createJangarooPackagingOutputElements(Artifact artifact,
                                                              PackagingElementResolvingContext resolvingContext,
                                                              Collection<ArchivePackagingElement> jangarooArchives) {
      CompositePackagingElement<?> rootBeer = artifact.getRootElement();
      for (ArchivePackagingElement archivePackagingElement : jangarooArchives) {
        Module jarModule = getModuleOfArchive(archivePackagingElement, resolvingContext);
        JangarooFacet jangarooFacet = JangarooFacet.ofModule(jarModule);
        PackagingElement<?> jangarooPackagingOutputElement =
          new JangarooPackagingOutputElement(resolvingContext.getProject(), jangarooFacet);
        rootBeer.addOrFindChild(jangarooPackagingOutputElement);
      }
    }

    private static void ensureBuildOnMake(final Artifact artifact, final ArtifactManager artifactManager) {
      // instruct IDEA to build the Web app on make:
      if (!artifact.isBuildOnMake()) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            ApplicationManager.getApplication().runWriteAction(new Runnable() {
              public void run() {
                ModifiableArtifactModel modifiableArtifactModel = artifactManager.createModifiableModel();
                final ModifiableArtifact modifiableArtifact = modifiableArtifactModel.getOrCreateModifiableArtifact(artifact);
                modifiableArtifact.setBuildOnMake(true);
                modifiableArtifactModel.commit();
              }
            });
          }
        });
      }
    }

    private static Collection<ArchivePackagingElement> findJangarooArchives(PackagingElementResolvingContext resolvingContext,
                                                                            CompositePackagingElement<?> directory) {
      Collection<ArchivePackagingElement> toBeRemovedLibraries = new ArrayList<ArchivePackagingElement>();
      for (PackagingElement packagingElement : directory.getChildren()) {
        if (packagingElement instanceof ArchivePackagingElement) {
          ArchivePackagingElement archivePackagingElement = (ArchivePackagingElement)packagingElement;
          Module module = getModuleOfArchive(archivePackagingElement, resolvingContext);
          if (JangarooFacet.ofModule(module) != null) {
            toBeRemovedLibraries.add(archivePackagingElement);
          }
        }
      }
      return toBeRemovedLibraries;
    }

    private static Module getModuleOfArchive(ArchivePackagingElement archivePackagingElement,
                                             PackagingElementResolvingContext resolvingContext) {
      List<PackagingElement<?>> archiveChildren = archivePackagingElement.getChildren();
      if (archiveChildren.size() == 1) {
        PackagingElement<?> moduleOutputPackagingElement = archiveChildren.get(0);
        if (moduleOutputPackagingElement instanceof ModuleOutputPackagingElement) {
          return ((ModuleOutputPackagingElement)moduleOutputPackagingElement).findModule(resolvingContext);
        }
      }
      return null;
    }

    private static Artifact getExplodedWebArtifact(Module module) {
      ArtifactManager artifactManager = ArtifactManager.getInstance(module.getProject());
      for (Artifact artifact : artifactManager.getArtifactsByType(ExplodedWarArtifactType.getInstance())) {
        Module artifactModule = findModule(artifactManager, artifact);
        if (module.equals(artifactModule)) {
          return artifact;
        }
      }
      return null;
    }

    private static @Nullable Module findModule(@NotNull ArtifactManager artifactManager, @NotNull Artifact artifact) {
      PackagingElementResolvingContext packagingElementResolvingContext = artifactManager.getResolvingContext();
      for (PackagingElement<?> packagingElement : artifact.getRootElement().getChildren()) {
        if (packagingElement instanceof JavaeeFacetResourcesPackagingElement) {
          JavaeeFacet facet = ((JavaeeFacetResourcesPackagingElement) packagingElement).findFacet(packagingElementResolvingContext);
          if (facet != null) {
            return facet.getModule();
          }
        }
      }
      return null;
    }

  }
}
