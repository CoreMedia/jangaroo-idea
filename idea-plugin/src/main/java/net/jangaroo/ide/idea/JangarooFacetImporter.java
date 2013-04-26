package net.jangaroo.ide.idea;

import com.intellij.lang.javascript.flex.FlexModuleType;
import com.intellij.lang.javascript.flex.library.FlexLibraryProperties;
import com.intellij.lang.javascript.flex.library.FlexLibraryType;
import com.intellij.lang.javascript.flex.projectStructure.model.FlexBuildConfigurationManager;
import com.intellij.lang.javascript.flex.projectStructure.model.LinkageType;
import com.intellij.lang.javascript.flex.projectStructure.model.ModifiableBuildConfigurationEntry;
import com.intellij.lang.javascript.flex.projectStructure.model.ModifiableDependencies;
import com.intellij.lang.javascript.flex.projectStructure.model.ModifiableFlexIdeBuildConfiguration;
import com.intellij.lang.javascript.flex.projectStructure.model.ModifiableSharedLibraryEntry;
import com.intellij.lang.javascript.flex.projectStructure.model.OutputType;
import com.intellij.lang.javascript.flex.projectStructure.model.impl.ConversionHelper;
import com.intellij.lang.javascript.flex.projectStructure.model.impl.Factory;
import com.intellij.lang.javascript.flex.sdk.FlexSdkUtils;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import net.jangaroo.jooc.config.CompilerConfigParser;
import net.jangaroo.jooc.config.JoocConfiguration;
import net.jangaroo.jooc.config.NamespaceConfiguration;
import net.jangaroo.jooc.config.PublicApiViolationsMode;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.importing.FacetImporter;
import org.jetbrains.idea.maven.importing.MavenModifiableModelsProvider;
import org.jetbrains.idea.maven.importing.MavenRootModelAdapter;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.model.MavenPlugin;
import org.jetbrains.idea.maven.project.MavenConsole;
import org.jetbrains.idea.maven.project.MavenEmbeddersManager;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectChanges;
import org.jetbrains.idea.maven.project.MavenProjectsProcessorTask;
import org.jetbrains.idea.maven.project.MavenProjectsTree;
import org.jetbrains.idea.maven.project.SupportedRequestType;
import org.jetbrains.idea.maven.utils.MavenArtifactUtil;
import org.jetbrains.idea.maven.utils.MavenJDOMUtil;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static net.jangaroo.ide.idea.util.IdeaFileUtils.toIdeaUrl;

/**
 * A Facet-from-Maven Importer for the Jangaroo Facet type.
 */
public class JangarooFacetImporter extends FacetImporter<JangarooFacet, JangarooFacetConfiguration, JangarooFacetType> {
  public static final String JANGAROO_GROUP_ID = "net.jangaroo";
  private static final String JANGAROO_MAVEN_PLUGIN_ARTIFACT_ID = "jangaroo-maven-plugin";
  static final String JANGAROO_COMPILER_API_ARTIFACT_ID = "jangaroo-compiler-api";
  public static final String EXML_MAVEN_PLUGIN_ARTIFACT_ID = "exml-maven-plugin";
  private static final String JANGAROO_PACKAGING_TYPE = "jangaroo";
  private static final String DEFAULT_JANGAROO_FACET_NAME = "Jangaroo";

  public JangarooFacetImporter() {
    super(JANGAROO_GROUP_ID, JANGAROO_MAVEN_PLUGIN_ARTIFACT_ID, JangarooFacetType.INSTANCE, DEFAULT_JANGAROO_FACET_NAME);
  }

  @Override
  public void getSupportedPackagings(Collection<String> result) {
    super.getSupportedPackagings(result);
    result.add(JANGAROO_PACKAGING_TYPE);
  }

  @NotNull
  @Override
  public ModuleType getModuleType() {
    return FlexModuleType.getInstance();
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

  public boolean isApplicable(MavenProject mavenProjectModel) {
    // any of the two Jangaroo Maven plugins has to be configured explicitly:
    return findJangarooMavenPlugin(mavenProjectModel) != null;
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
    result.add("jar"); // for Jangaroo 2!
  }

  @Override
  protected void setupFacet(JangarooFacet f, MavenProject mavenProject) {
    //System.out.println("setupFacet called!");
  }

  private String getConfigurationValue(MavenProject mavenProjectModel, String configName, @Nullable String defaultValue) {
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
    FlexBuildConfigurationManager flexBuildConfigurationManager = FlexBuildConfigurationManager.getInstance(module);
    ModifiableFlexIdeBuildConfiguration buildConfiguration = (ModifiableFlexIdeBuildConfiguration)flexBuildConfigurationManager.getActiveConfiguration();
    buildConfiguration.setName(mavenProjectModel.getName());
    buildConfiguration.setOutputType(OutputType.Library);
    buildConfiguration.setSkipCompile(true);
    // just to satisfy IDEA:
    buildConfiguration.setOutputFolder(mavenProjectModel.getBuildDirectory());
    buildConfiguration.setOutputFileName(mavenProjectModel.getFinalName() + ".swc");

    StringBuilder namespaceConfigs = new StringBuilder();
    ModifiableDependencies modifiableDependencies = buildConfiguration.getDependencies();
    if (modifiableDependencies.getSdkEntry() == null) {
      Iterator<Sdk> flexSdks = FlexSdkUtils.getFlexAndFlexmojosSdks().iterator();
      if (!flexSdks.hasNext()) {
        // TODO: complain that there is no Flex SDK at all!
      } else {
        Sdk flexSdk = flexSdks.next();
        modifiableDependencies.setSdkEntry(Factory.createSdkEntry(flexSdk.getName()));
      }
    }
    modifiableDependencies.setFrameworkLinkage(LinkageType.External);
    modifiableDependencies.getModifiableEntries().clear();
    for (MavenArtifact dependency : mavenProjectModel.getDependencies()) {
      String libraryName = dependency.getLibraryName() + "-joo";
      Library library = modelsProvider.getLibraryByName(libraryName);
      VirtualFile artifactFile = LocalFileSystem.getInstance().findFileByIoFile(dependency.getFile());
      if (artifactFile != null) {
        VirtualFile artifactJarFile = JarFileSystem.getInstance().getJarRootForLocalFile(artifactFile);
        // build library if it does not exit:
        if (artifactJarFile != null) {
          if (library == null) {
            VirtualFile jooApiDir = artifactJarFile.findFileByRelativePath("META-INF/joo-api");
            if (jooApiDir != null) {
              library = modelsProvider.createLibrary(libraryName);
              LibraryEx.ModifiableModelEx modifiableModel = (LibraryEx.ModifiableModelEx)library.getModifiableModel();
              modifiableModel.setType(FlexLibraryType.getInstance());
              modifiableModel.setProperties(new FlexLibraryProperties(libraryName));
              modifiableModel.addRoot(jooApiDir, OrderRootType.CLASSES);
              String sourcesPath = dependency.getPathForExtraArtifact("sources", null);
              VirtualFile sourcesJar = LocalFileSystem.getInstance().findFileByPath(sourcesPath);
              if (sourcesJar == null || !sourcesJar.exists()) {
                sourcesJar = jooApiDir;
              }
              modifiableModel.addRoot(sourcesJar, OrderRootType.SOURCES);
              String asdocPath = dependency.getPathForExtraArtifact("asdoc", null);
              VirtualFile asdocJar = LocalFileSystem.getInstance().findFileByPath(asdocPath);
              if (asdocJar != null && asdocJar.exists()) {
                modifiableModel.addRoot(asdocJar, OrderRootType.DOCUMENTATION);
              }
              modifiableModel.commit();
            }
          }
          // look for component namespace:
          parseCompilerConfig(artifactJarFile, namespaceConfigs);
        }
      }
      if (library != null) {
        // add library to buildConfiguration...
        ModifiableSharedLibraryEntry dependencyEntry = ConversionHelper.createSharedLibraryEntry(libraryName, "project");
        if ("test".equals(dependency.getScope())) {
          dependencyEntry.getDependencyType().setLinkageType(LinkageType.Test);
        }
        modifiableDependencies.getModifiableEntries().add(dependencyEntry);
      }
    }

    postTasks.add(new EstablishModuleDependenciesTask(module, namespaceConfigs));

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
    int jangarooMajorVersion = Integer.parseInt(jangarooSdkVersion.split("[.-]", 2)[0]);
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

  private static void parseCompilerConfig(VirtualFile baseDirOrJar, StringBuilder namespaceConfigs) {
    VirtualFile compilerConfigXml = baseDirOrJar.findFileByRelativePath("config.xml");
    if (compilerConfigXml != null) {
      JoocConfiguration joocConfiguration = new JoocConfiguration();
      try {
        new CompilerConfigParser(joocConfiguration).parse(compilerConfigXml.getInputStream());
        for (NamespaceConfiguration namespace : joocConfiguration.getNamespaces()) {
          VirtualFile manifestFile = baseDirOrJar.findFileByRelativePath(namespace.getManifest());
          if (manifestFile != null && manifestFile.exists()) {
            if (namespaceConfigs.length() > 0) {
              namespaceConfigs.append('\n');
            }
            namespaceConfigs.append(namespace.getUri()).append('\t').append(manifestFile.getPath());
          } else {
            // ignore, TODO: log!
          }
        }

      } catch (IOException e) {
        // ignore, TODO: log!
      }
    }
  }

  public static String jangarooSdkHomePath(String artifactId, String version) {
    // version sanity check:
    try {
      Integer.parseInt(version.split("[.-]", 2)[0]);
    } catch (NumberFormatException e) {
      return null;
    }
    File localRepository = MavenUtil.resolveLocalRepository(null, null, null);
    File jarFile = MavenArtifactUtil.getArtifactFile(localRepository, JANGAROO_GROUP_ID, artifactId, version, "jar");
    return jarFile.getParentFile().getAbsolutePath();
  }

  public void collectSourceFolders(MavenProject mavenProject, List<String> result) {
    collectSourceOrTestFolders(mavenProject, "compile", "src/main/joo", result);
    result.add("src/main/joo-api"); // must be a source folder in IDEA for references to API-only classes to work
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

  private static class EstablishModuleDependenciesTask implements MavenProjectsProcessorTask {
    private Module module;
    private StringBuilder namespaceConfigs;

    private EstablishModuleDependenciesTask(Module module, StringBuilder namespaceConfigs) {
      this.module = module;
      this.namespaceConfigs = namespaceConfigs;
    }

    @Override
    public void perform(Project project, MavenEmbeddersManager embeddersManager, MavenConsole console, MavenProgressIndicator indicator) throws MavenProcessCanceledException {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
              FlexBuildConfigurationManager flexBuildConfigurationManager = FlexBuildConfigurationManager.getInstance(module);
              ModifiableFlexIdeBuildConfiguration buildConfiguration = (ModifiableFlexIdeBuildConfiguration)flexBuildConfigurationManager.getActiveConfiguration();

              ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
              for (VirtualFile sourceRoot : moduleRootManager.getSourceRoots()) {
                parseCompilerConfig(sourceRoot, namespaceConfigs);
              }

              ModifiableDependencies modifiableDependencies = buildConfiguration.getDependencies();
              for (Module dependency : moduleRootManager.getDependencies()) {
                if (FlexModuleType.getInstance().equals(ModuleType.get(dependency))) {
                  ModifiableBuildConfigurationEntry buildConfigurationEntry = createModifiableBuildConfigurationEntry(dependency, dependency.getName());
                  modifiableDependencies.getModifiableEntries().add(buildConfigurationEntry);
                  // look for config.xml containing additional compiler settings:
                  for (VirtualFile sourceRoot : ModuleRootManager.getInstance(dependency).getSourceRoots()) {
                    parseCompilerConfig(sourceRoot, namespaceConfigs);
                  }
                }
              }
              if (namespaceConfigs.length() > 0) {
                buildConfiguration.getCompilerOptions().setAllOptions(Collections.singletonMap(
                  "compiler.namespaces.namespace", namespaceConfigs.toString()
                ));
              }
            }
          });
        }
      });
    }

    // how is one supposed to create new build configuration entries without using reflection and access control?!
    private static ModifiableBuildConfigurationEntry createModifiableBuildConfigurationEntry(Module module, String bcName) {
      try {
        Class<?> bcei = Class.forName("com.intellij.lang.javascript.flex.projectStructure.model.impl.BuildConfigurationEntryImpl");
        Constructor<?> constructor = bcei.getConstructor(Module.class, String.class);
        constructor.setAccessible(true);
        return (ModifiableBuildConfigurationEntry)constructor.newInstance(module, bcName);
      } catch (RuntimeException e) {
        throw e;
      } catch (Throwable e) {
        throw new RuntimeException(e);
      }
    }
  }
}
