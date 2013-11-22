package net.jangaroo.ide.idea;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import net.jangaroo.jooc.config.PublicApiViolationsMode;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.importing.FacetImporter;
import org.jetbrains.idea.maven.importing.MavenModifiableModelsProvider;
import org.jetbrains.idea.maven.importing.MavenRootModelAdapter;
import org.jetbrains.idea.maven.model.MavenPlugin;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectChanges;
import org.jetbrains.idea.maven.project.MavenProjectsProcessorTask;
import org.jetbrains.idea.maven.project.MavenProjectsTree;
import org.jetbrains.idea.maven.project.SupportedRequestType;
import org.jetbrains.idea.maven.utils.MavenArtifactUtil;
import org.jetbrains.idea.maven.utils.MavenJDOMUtil;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static net.jangaroo.ide.idea.util.IdeaFileUtils.toIdeaUrl;

/**
 * A Facet-from-Maven Importer for the Jangaroo Facet type.
 */
public abstract class JangarooFacetImporter extends FacetImporter<JangarooFacet, JangarooFacetConfiguration, JangarooFacetType> {
  public static final String JANGAROO_GROUP_ID = "net.jangaroo";
  static final String JANGAROO_MAVEN_PLUGIN_ARTIFACT_ID = "jangaroo-maven-plugin";
  static final String JANGAROO_COMPILER_API_ARTIFACT_ID = "jangaroo-compiler-api";
  public static final String EXML_MAVEN_PLUGIN_ARTIFACT_ID = "exml-maven-plugin";
  public static final String JANGAROO_PACKAGING_TYPE = "jangaroo";
  static final String JANGAROO_DEPENDENCY_TYPE = "jangaroo";
  private static final String DEFAULT_JANGAROO_FACET_NAME = "Jangaroo";

  public JangarooFacetImporter() {
    super(JANGAROO_GROUP_ID, JANGAROO_MAVEN_PLUGIN_ARTIFACT_ID, JangarooFacetType.INSTANCE, DEFAULT_JANGAROO_FACET_NAME);
  }

  // we cannot use MavenProject#findPlugin(), because it also searches in <pluginManagement>:
  public static MavenPlugin findDeclaredJangarooPlugin(MavenProject mavenProject, @Nullable String artifactId) {
    for (MavenPlugin each : mavenProject.getDeclaredPlugins()) {
      if (each.getMavenId().equals(JANGAROO_GROUP_ID, artifactId)) {
        return each;
      }
    }
    return null;
  }

  public static int getMajorVersion(String version) {
    if (version != null) {
      try {
        return Integer.parseInt(version.split("[.-]", 2)[0]);
      } catch (NumberFormatException e) {
        // ignore
      }
    }
    return -1;
  }
  public boolean isApplicable(MavenProject mavenProjectModel) {
    // any of the two Jangaroo Maven plugins has to be configured explicitly:
    MavenPlugin jangarooMavenPlugin = findJangarooMavenPlugin(mavenProjectModel);
    if (jangarooMavenPlugin == null && JANGAROO_PACKAGING_TYPE.equals(mavenProjectModel.getPackaging())) {
      Notifications.Bus.notify(new Notification("jangaroo", "Jangaroo Facet not created/updated",
        "Module " + mavenProjectModel.getMavenId() + " uses packaging type 'jangaroo', " +
        "but no jangaroo-maven-plugin or exml-maven-plugin was found. Try repeating 'Reimport All Maven Projects'.",
        NotificationType.WARNING));
    }
    return jangarooMavenPlugin != null
      && isApplicableVersion(getMajorVersion(jangarooMavenPlugin.getVersion()));
  }

  protected abstract boolean isApplicableVersion(int majorVersion);

  /**
   * Find jangaroo-maven-plugin, or, if not present, exml-maven-plugin, which also activates the
   * Jangaroo Maven lifecycle and thus the Jangaroo compiler.
   * @param mavenProjectModel IDEA's Maven project model
   * @return jangaroo-maven-plugin or exml-maven-plugin
   */
  protected MavenPlugin findJangarooMavenPlugin(MavenProject mavenProjectModel) {
    MavenPlugin jangarooPlugin = findDeclaredJangarooPlugin(mavenProjectModel, JANGAROO_MAVEN_PLUGIN_ARTIFACT_ID);
    if (jangarooPlugin == null) {
      jangarooPlugin = findDeclaredJangarooPlugin(mavenProjectModel, EXML_MAVEN_PLUGIN_ARTIFACT_ID);
    }
    return jangarooPlugin;
  }

  @Override
  public void getSupportedPackagings(Collection<String> result) {
    super.getSupportedPackagings(result);
    result.add(JANGAROO_PACKAGING_TYPE);
  }

  @Override
  public void getSupportedDependencyTypes(Collection<String> result, SupportedRequestType type) {
    super.getSupportedDependencyTypes(result, type);
    result.add(JANGAROO_DEPENDENCY_TYPE);
    result.add("jar"); // for Jangaroo 2!
  }

  @Override
  protected void setupFacet(JangarooFacet f, MavenProject mavenProject) {
    //System.out.println("setupFacet called!");
  }

  protected String getConfigurationValue(MavenProject mavenProjectModel, String configName, @Nullable String defaultValue) {
    String value = null;
    Element compileConfiguration = mavenProjectModel.getPluginGoalConfiguration(JANGAROO_GROUP_ID, JANGAROO_MAVEN_PLUGIN_ARTIFACT_ID, "compile");
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

  protected boolean getBooleanConfigurationValue(MavenProject mavenProjectModel, String configName, boolean defaultValue) {
    String value = getConfigurationValue(mavenProjectModel, configName, String.valueOf(defaultValue));
    return Boolean.valueOf(value);
  }

  @Override
  protected void reimportFacet(MavenModifiableModelsProvider modelsProvider, Module module, MavenRootModelAdapter rootModel, JangarooFacet jangarooFacet, MavenProjectsTree mavenTree, MavenProject mavenProjectModel, MavenProjectChanges changes, Map<MavenProject, String> mavenProjectToModuleName, List<MavenProjectsProcessorTask> postTasks) {
    //System.out.println("reimportFacet called!");
    JangarooFacetConfiguration jangarooFacetConfiguration = jangarooFacet.getConfiguration();
    JoocConfigurationBean jooConfig = jangarooFacetConfiguration.getState();
    MavenPlugin jangarooMavenPlugin = findJangarooMavenPlugin(mavenProjectModel);
    String jangarooSdkVersion = jangarooMavenPlugin.getVersion();
    String sdkHomePath = jangarooSdkHomePath(JANGAROO_COMPILER_API_ARTIFACT_ID, jangarooSdkVersion);
    Sdk jangarooSdk = JangarooSdkUtils.createOrGetSdk(JangarooSdkType.getInstance(), sdkHomePath);
    if (jangarooSdk == null) {
      if (sdkHomePath == null) {
        Notifications.Bus.notify(new Notification("Maven", "Jangaroo Version Not Found",
          "No or illegal version found for Jangaroo SDK in Maven POM " + mavenProjectModel.getDisplayName() +
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

    String jooClassesRelativePath = "joo/classes";
    int jangarooMajorVersion = getMajorVersion(jangarooSdkVersion);
    if (jangarooMajorVersion > 2) {
      jooClassesRelativePath = "amd/as3";
    }
    String jooClassesPath = "";
    if (jangarooMajorVersion > 1 && !isWar) {
      jooClassesPath = "META-INF/resources/" + jooClassesRelativePath;
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
    jooConfig.testOutputDirectory = toIdeaUrl(new File(testOutputDir, jooClassesRelativePath).getAbsolutePath());

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
  }

  public static String jangarooSdkHomePath(String artifactId, String version) {
    File localRepository = MavenUtil.resolveLocalRepository(null, null, null);
    File jarFile = MavenArtifactUtil.getArtifactFile(localRepository, JANGAROO_GROUP_ID, artifactId, version, "jar");
    return jarFile.getParentFile().getAbsolutePath();
  }

  public void collectSourceFolders(MavenProject mavenProject, List<String> result) {
    collectSourceOrTestFolders(mavenProject, "compile", "src/main/joo", result);
  }

  public void collectTestFolders(MavenProject mavenProject, List<String> result) {
    collectSourceOrTestFolders(mavenProject, "testCompile", "src/test/joo", result);
  }

  private void collectSourceOrTestFolders(MavenProject mavenProject, String goal, String defaultDir, List<String> sourceDirs) {
    Element goalConfiguration = getGoalConfig(mavenProject, goal);
    if (goalConfiguration != null) {
      List<String> mvnSrcDirs = MavenJDOMUtil.findChildrenValuesByPath(goalConfiguration, "sources", "directory");
      if (!mvnSrcDirs.isEmpty()) {
        sourceDirs.addAll(mvnSrcDirs);
        return;
      }
    }
    sourceDirs.add(defaultDir);
  }

}
