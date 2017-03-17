package net.jangaroo.ide.idea;

import com.intellij.flex.model.bc.LinkageType;
import com.intellij.flex.model.bc.OutputType;
import com.intellij.lang.javascript.flex.FlexModuleType;
import com.intellij.lang.javascript.flex.library.FlexLibraryType;
import com.intellij.lang.javascript.flex.projectStructure.FlexBuildConfigurationsExtension;
import com.intellij.lang.javascript.flex.projectStructure.model.BuildConfigurationEntry;
import com.intellij.lang.javascript.flex.projectStructure.model.FlexBuildConfigurationManager;
import com.intellij.lang.javascript.flex.projectStructure.model.ModifiableBuildConfigurationEntry;
import com.intellij.lang.javascript.flex.projectStructure.model.ModifiableDependencies;
import com.intellij.lang.javascript.flex.projectStructure.model.ModifiableDependencyEntry;
import com.intellij.lang.javascript.flex.projectStructure.model.ModifiableFlexBuildConfiguration;
import com.intellij.lang.javascript.flex.projectStructure.model.impl.Factory;
import com.intellij.lang.javascript.flex.projectStructure.model.impl.FlexProjectConfigurationEditor;
import com.intellij.lang.javascript.flex.sdk.FlexSdkType2;
import com.intellij.lang.javascript.flex.sdk.FlexSdkUtils;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PairConsumer;
import net.jangaroo.ide.idea.jps.JoocConfigurationBean;
import net.jangaroo.ide.idea.jps.JpsJangarooSdkType;
import net.jangaroo.jooc.config.PublicApiViolationsMode;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.importing.FacetImporter;
import org.jetbrains.idea.maven.importing.MavenRootModelAdapter;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.model.MavenPlugin;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectChanges;
import org.jetbrains.idea.maven.project.MavenProjectsProcessorTask;
import org.jetbrains.idea.maven.project.MavenProjectsTree;
import org.jetbrains.idea.maven.project.SupportedRequestType;
import org.jetbrains.idea.maven.utils.MavenJDOMUtil;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenUtil;
import org.jetbrains.jps.model.java.JavaResourceRootType;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static net.jangaroo.ide.idea.jps.util.IdeaFileUtils.toIdeaUrl;

/**
 * A Facet-from-Maven Importer for the Jangaroo Facet type.
 */
public class JangarooFacetImporter extends FacetImporter<JangarooFacet, JangarooFacetConfiguration, JangarooFacetType> {
  public static final String JANGAROO_GROUP_ID = "net.jangaroo";
  static final String JANGAROO_MAVEN_PLUGIN_ARTIFACT_ID = "jangaroo-maven-plugin";
  public static final String JANGAROO_PKG_PACKAGING_TYPE = "jangaroo-pkg";
  public static final String JANGAROO_APP_PACKAGING_TYPE = "jangaroo-app";
  private static final String MAVEN_PACKAGING_POM = "pom";
  private static final String DEFAULT_JANGAROO_FACET_NAME = "Jangaroo";

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
    // fast path for unsupported packagings:
    if (!isSupportedPackaging(this, mavenProjectModel)) {
      return false;
    }
    // any of the two Jangaroo Maven plugins has to be configured explicitly:
    MavenPlugin jangarooMavenPlugin = findJangarooMavenPlugin(mavenProjectModel);
    if (MAVEN_PACKAGING_POM.equals(mavenProjectModel.getPackaging())) {
      // The only pom-based Flash modules we support are those that use jangaroo:extract-remote-packages:
      if (jangarooMavenPlugin != null) {
        for (MavenPlugin.Execution execution : jangarooMavenPlugin.getExecutions()) {
          if (execution.getGoals().contains("extract-remote-packages")) {
            return true;
          }
        }
      }
      return false;
    }
    if (jangarooMavenPlugin == null) {
      Notifications.Bus.notify(new Notification("jangaroo", "Jangaroo Facet not created/updated",
        "Module " + mavenProjectModel.getMavenId() + " uses packaging type '" + mavenProjectModel.getPackaging() + "', " +
        "but no jangaroo-maven-plugin was found. Try repeating 'Reimport All Maven Projects'.",
        NotificationType.WARNING));
    }
    return jangarooMavenPlugin != null;
  }

  @NotNull
  @Override
  public ModuleType getModuleType() {
    return FlexModuleType.getInstance();
  }

  public static boolean isSupportedPackaging(FacetImporter facetImporter, MavenProject mavenProjectModel) {
    ArrayList<String> packagings = new ArrayList<String>();
    facetImporter.getSupportedPackagings(packagings);
    return packagings.contains(mavenProjectModel.getPackaging());
  }

  /**
   * Find jangaroo-maven-plugin which also activates the
   * Jangaroo Maven lifecycle and thus the Jangaroo compiler.
   * @param mavenProjectModel IDEA's Maven project model
   * @return jangaroo-maven-plugin
   */
  protected MavenPlugin findJangarooMavenPlugin(MavenProject mavenProjectModel) {
    return findDeclaredJangarooPlugin(mavenProjectModel, JANGAROO_MAVEN_PLUGIN_ARTIFACT_ID);
  }

  @Override
  public void getSupportedPackagings(Collection<String> result) {
    super.getSupportedPackagings(result);
    result.add(JANGAROO_PKG_PACKAGING_TYPE);
    result.add(JANGAROO_APP_PACKAGING_TYPE);
    result.add(MAVEN_PACKAGING_POM); // for remote packages module!
  }

  @Override
  public void getSupportedDependencyTypes(Collection<String> result, SupportedRequestType type) {
    super.getSupportedDependencyTypes(result, type);
    // for Flex modules, "jar" dependencies (to Jangaroo libraries) are not handled automatically:
    result.add("jar");
    result.add("pom");
  }

  @Override
  protected void setupFacet(JangarooFacet f, MavenProject mavenProject) {
    //System.out.println("setupFacet called!");
  }

  protected String getConfigurationValue(MavenProject mavenProjectModel, String configName, @Nullable String defaultValue) {
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

  protected boolean getBooleanConfigurationValue(MavenProject mavenProjectModel, String configName, boolean defaultValue) {
    String value = getConfigurationValue(mavenProjectModel, configName, String.valueOf(defaultValue));
    return Boolean.valueOf(value);
  }

  @Override
  protected void reimportFacet(IdeModifiableModelsProvider modelsProvider, Module module,
                               MavenRootModelAdapter rootModel, JangarooFacet jangarooFacet,
                               MavenProjectsTree mavenTree, MavenProject mavenProjectModel,
                               MavenProjectChanges changes, Map<MavenProject, String> mavenProjectToModuleName,
                               List<MavenProjectsProcessorTask> postTasks) {
    //System.out.println("reimportFacet called!");
    FlexProjectConfigurationEditor currentEditor = getCurrentFlexProjectConfigurationEditor();
    FlexProjectConfigurationEditor flexEditor = currentEditor == null
      ? createFlexProjectConfigurationEditor(modelsProvider, module, rootModel)
      : currentEditor;

    doConfigure(flexEditor, modelsProvider, module, mavenProjectModel);

    if (currentEditor == null) {
      commitFlexProjectConfigurationEditor(flexEditor);
    }

    if (MAVEN_PACKAGING_POM.equals(mavenProjectModel.getPackaging())) {
      // The only pom-based Flash modules we have are remote-packages modules, for which nothing more has to be done here.
      return;
    }

    JangarooFacetConfiguration jangarooFacetConfiguration = jangarooFacet.getConfiguration();
    JoocConfigurationBean jooConfig = jangarooFacetConfiguration.getState();
    MavenPlugin jangarooMavenPlugin = findJangarooMavenPlugin(mavenProjectModel);
    String jangarooSdkVersion = jangarooMavenPlugin.getVersion();
    String sdkHomePath = jangarooSdkHomePath(JpsJangarooSdkType.JANGAROO_COMPILER_API_ARTIFACT_ID, jangarooSdkVersion);
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
    boolean isPkg = JANGAROO_PKG_PACKAGING_TYPE.equals(mavenProjectModel.getPackaging());
    boolean isApp = JANGAROO_APP_PACKAGING_TYPE.equals(mavenProjectModel.getPackaging());

    String outputDirectory = findConfigValue(mavenProjectModel, "outputDirectory");
    if (outputDirectory == null) {
      outputDirectory = mavenProjectModel.getBuildDirectory() + (isPkg ? "/packages/" + getSenchaPackageName(mavenProjectModel.getMavenId()) + "/src" : "/app/app");
    }
    File outputDir = new File(outputDirectory);
    if (!outputDir.isAbsolute()) {
      outputDir = new File(mavenProjectModel.getDirectory(), outputDirectory);
    }

    int jangarooMajorVersion = getMajorVersion(jangarooSdkVersion);
    if (jangarooMajorVersion < 4) {
      return;
    }
    jooConfig.outputDirectory = toIdeaUrl(outputDir.getAbsolutePath());

    jooConfig.outputFilePrefix = mavenProjectModel.getMavenId().getGroupId() + "__" + mavenProjectModel.getMavenId().getArtifactId();

    String apiOutputDirectory = getConfigurationValue(mavenProjectModel, "apiOutputDirectory", null);
    jooConfig.apiOutputDirectory = isApp ? null : toIdeaUrl(apiOutputDirectory != null ? apiOutputDirectory : new File(mavenProjectModel.getBuildDirectory(), "META-INF/joo-api").getAbsolutePath());

    String testOutputDirectory = findConfigValue(mavenProjectModel, "testOutputDirectory", mavenProjectModel.getTestOutputDirectory());
    assert testOutputDirectory != null; // since it has a non-null default!
    File testOutputDir = new File(testOutputDirectory, "src");
    if (!testOutputDir.isAbsolute()) {
      testOutputDir = new File(mavenProjectModel.getDirectory(), testOutputDir.getPath());
    }
    jooConfig.testOutputDirectory = toIdeaUrl(testOutputDir.getAbsolutePath());

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

  private String getSenchaPackageName(MavenId mavenId) {
    return getSenchaPackageName(mavenId.getGroupId(), mavenId.getArtifactId());
  }

  // TODO: reuse jangaroo-tools utility method through compiler API?
  private static String getSenchaPackageName(String groupId, String artifactId) {
    return groupId + "__" + artifactId;
  }

  private static String jangarooSdkHomePath(String artifactId, String version) {
    File localRepository = MavenUtil.resolveLocalRepository(null, null, null);
    File jarFile = JpsJangarooSdkType.getJangarooArtifact(localRepository, artifactId, version);
    return jarFile.getParentFile().getAbsolutePath();
  }

  @Override
  public void collectSourceRoots(MavenProject mavenProject, PairConsumer<String, JpsModuleSourceRootType<?>> result) {
    if (MAVEN_PACKAGING_POM.equals(mavenProject.getPackaging())) {
      // The only pom-based Flash modules we have are remote-packages modules, which contain the Ext framework.
      // This has to be marked as a resource root folder.
      result.consume("target/ext/classic", JavaResourceRootType.RESOURCE);
      return;
    }
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

  private static FlexProjectConfigurationEditor getCurrentFlexProjectConfigurationEditor() {
    return FlexBuildConfigurationsExtension.getInstance().getConfigurator().getConfigEditor();
  }

  @NotNull
  private static FlexProjectConfigurationEditor createFlexProjectConfigurationEditor(IdeModifiableModelsProvider modelsProvider, final Module module, final MavenRootModelAdapter rootModel) {
    FlexProjectConfigurationEditor flexEditor;
    LibraryTable.ModifiableModel projectLibrariesModel = modelsProvider.getModifiableProjectLibrariesModel();
    final Map<Module, ModifiableRootModel> moduleToModifiableModel = Collections.singletonMap(module, rootModel.getRootModel());
    flexEditor = new FlexProjectConfigurationEditor(module.getProject(),
      FlexProjectConfigurationEditor.createModelProvider(moduleToModifiableModel, projectLibrariesModel, null)) {
      @Nullable
      protected Module findModuleWithBC(final BuildConfigurationEntry bcEntry) {
        // don't check BC presence here because corresponding BC may appear later in next import cycle
        return rootModel.findModuleByName(bcEntry.getModuleName());
      }
    };
    return flexEditor;
  }

  private static void commitFlexProjectConfigurationEditor(FlexProjectConfigurationEditor flexEditor) {
    try {
      flexEditor.commit();
    } catch (ConfigurationException e) {
      MavenLog.LOG.error(e); // can't happen
    }
  }

  private void doConfigure(FlexProjectConfigurationEditor flexEditor, IdeModifiableModelsProvider modelsProvider,
                           Module module, MavenProject mavenProjectModel) {
    String buildConfigurationName = module.getName();

    ModifiableFlexBuildConfiguration buildConfiguration = getFirstFlexBuildConfiguration(flexEditor, module);
    if (buildConfiguration == null) {
      buildConfiguration = flexEditor.createConfiguration(module);
    }
    buildConfiguration.setName(buildConfigurationName);
    buildConfiguration.setOutputType(OutputType.Library);
    buildConfiguration.setSkipCompile(true);
    // just to satisfy IDEA:
    buildConfiguration.setOutputFolder(mavenProjectModel.getBuildDirectory());
    buildConfiguration.setOutputFileName(mavenProjectModel.getFinalName() + ".swc");
    configureMxmlNamespaces(mavenProjectModel, buildConfiguration);

    ModifiableDependencies modifiableDependencies = buildConfiguration.getDependencies();
    Sdk flExtAsSDK = FlexSdkUtils.createOrGetSdk(FlexSdkType2.getInstance(), PathManager.getPluginsPath() + "/jangaroo-4/FlExtAS");
    if (flExtAsSDK == null) {
      Notifications.Bus.notify(new Notification("jangaroo", "No Flex SDK",
        "Jangaroo's internal mock Flex SDK 'FlExtAS' could not be found.", NotificationType.ERROR));
    } else {
      modifiableDependencies.setSdkEntry(Factory.createSdkEntry(flExtAsSDK.getName()));
      modifiableDependencies.setFrameworkLinkage(LinkageType.External);
    }

    modifiableDependencies.getModifiableEntries().clear();
    for (MavenArtifact dependency : mavenProjectModel.getDependencies()) {
      String packageType = findGoalConfigValue(mavenProjectModel, "package-pkg", "packageType");
      // leave out provided pom dependencies, i.e. the dependency to remote-packages, but not in a theme:
      if (MAVEN_PACKAGING_POM.equals(dependency.getType()) && "provided".equals(dependency.getScope()) && !"theme".equals(packageType)) {
        continue;
      }
      VirtualFile artifactFile = LocalFileSystem.getInstance().findFileByIoFile(dependency.getFile());
      if (artifactFile != null) {
        VirtualFile artifactJarFile = JarFileSystem.getInstance().getJarRootForLocalFile(artifactFile);
        // build library if it does not exit:
        if (artifactJarFile == null) {
          Module dependentModule = ProjectFileIndex.SERVICE.getInstance(module.getProject()).getModuleForFile(artifactFile);
          if (dependentModule != null && FlexModuleType.getInstance().equals(ModuleType.get(dependentModule))) {
            FlexBuildConfigurationManager flexBuildConfigurationManager = FlexBuildConfigurationManager.getInstance(dependentModule);
            ModifiableFlexBuildConfiguration dependentBC = (ModifiableFlexBuildConfiguration)flexBuildConfigurationManager.getActiveConfiguration();
            if (dependentBC != null) {
              ModifiableBuildConfigurationEntry buildConfigurationEntry = flexEditor.createBcEntry(modifiableDependencies, dependentModule.getName(), dependentBC.getName());
              buildConfigurationEntry.getDependencyType().setLinkageType("test".equals(dependency.getScope()) ? LinkageType.Test : LinkageType.Merged);
              modifiableDependencies.getModifiableEntries().add(buildConfigurationEntry);
            }
          }
        } else {
          String libraryName = dependency.getLibraryName() + "-joo";
          Library library = modelsProvider.getLibraryByName(libraryName);
          if (library == null) {
            VirtualFile jooApiDir = artifactJarFile.findFileByRelativePath("META-INF/joo-api");
            if (jooApiDir != null) {
              library = modelsProvider.getModifiableProjectLibrariesModel().createLibrary(libraryName, FlexLibraryType.FLEX_LIBRARY);
              final LibraryEx.ModifiableModelEx libraryModifiableModel = ((LibraryEx.ModifiableModelEx)library.getModifiableModel());
              libraryModifiableModel.setProperties(FlexLibraryType.FLEX_LIBRARY.createDefaultProperties());
              libraryModifiableModel.addRoot(artifactJarFile, OrderRootType.CLASSES);
              String sourcesPath = dependency.getPathForExtraArtifact("sources", null);
              VirtualFile sourcesJar = LocalFileSystem.getInstance().findFileByPath(sourcesPath);
              if (sourcesJar != null && sourcesJar.exists()) {
                libraryModifiableModel.addRoot(sourcesJar, OrderRootType.SOURCES);
              }
              libraryModifiableModel.addRoot(jooApiDir, OrderRootType.SOURCES);
              String asdocPath = dependency.getPathForExtraArtifact("asdoc", null);
              VirtualFile asdocJar = LocalFileSystem.getInstance().findFileByPath(asdocPath);
              if (asdocJar != null && asdocJar.exists()) {
                libraryModifiableModel.addRoot(asdocJar, OrderRootType.DOCUMENTATION);
              }
              ApplicationManager.getApplication().runWriteAction(new Runnable() {
                public void run() {
                  libraryModifiableModel.commit();
                }
              });
            }
          }

          // look for component namespace:
//          parseCompilerConfig(artifactJarFile, namespaceConfigs);

          if (library != null) {
            // add library to buildConfiguration...
            final ModifiableDependencyEntry libraryEntry = flexEditor.createSharedLibraryEntry(modifiableDependencies, libraryName, LibraryTablesRegistrar.PROJECT_LEVEL);
            libraryEntry.getDependencyType().setLinkageType("test".equals(dependency.getScope()) ? LinkageType.Test : LinkageType.Merged);
            modifiableDependencies.getModifiableEntries().add(libraryEntry);
          }
        }
      }
    }
  }

  private void configureMxmlNamespaces(MavenProject mavenProjectModel, ModifiableFlexBuildConfiguration buildConfiguration) {
    List<Pair<String, String>> namespacesToManifests = new ArrayList<Pair<String, String>>();
    Element jangarooPluginConfiguration = mavenProjectModel.getPluginConfiguration(JANGAROO_GROUP_ID, JANGAROO_MAVEN_PLUGIN_ARTIFACT_ID);
    if (jangarooPluginConfiguration != null) {
      Element namespacesElement = jangarooPluginConfiguration.getChild("namespaces");
      if (namespacesElement != null) {
        for (Element namespaceElement : namespacesElement.getChildren("namespace")) {
          Element uriElement = namespaceElement.getChild("uri");
          if (uriElement != null) {
            String namespace = uriElement.getTextTrim();
            Element manifestElement = namespaceElement.getChild("manifest");
            if (manifestElement != null) {
              addManifestNamespaceMapping(namespacesToManifests, namespace, manifestElement.getTextTrim());
            } else {
              addManifestNamespaceMapping(namespacesToManifests, namespace, mavenProjectModel.getSources());
              addManifestNamespaceMapping(namespacesToManifests, namespace, mavenProjectModel.getTestSources());
            }
          }
        }
        String namespaceMapping = formatNamespaceMapping(namespacesToManifests);
        if (namespaceMapping != null) {
          Map<String, String> allOptions = Collections.singletonMap("compiler.namespaces.namespace", namespaceMapping);
          buildConfiguration.getCompilerOptions().setAllOptions(allOptions);
        }
      }
    }
  }

  private void addManifestNamespaceMapping(List<Pair<String, String>> namespaceToManifest, String namespace, List<String> directories) {
    for (String directory : directories) {
      String manifestFileName = directory.replace('\\', '/') + "/manifest.xml";
      if (addManifestNamespaceMapping(namespaceToManifest, namespace, manifestFileName)) {
        break;
      }
    }
  }

  private boolean addManifestNamespaceMapping(List<Pair<String, String>> namespaceToManifest, String namespace, String manifestFileName) {
    if (new File(manifestFileName).exists()) {
      namespaceToManifest.add(Pair.create(namespace, manifestFileName));
      return true;
    }
    return false;
  }

  private String formatNamespaceMapping(List<Pair<String, String>> namespacesToManifests) {
    if (namespacesToManifests.isEmpty()) {
      return null;
    }
    StringBuilder result = new StringBuilder();
    Iterator<Pair<String, String>> iterator = namespacesToManifests.iterator();
    while (true) {
      Pair<String, String> namespaceToManifest = iterator.next();
      result.append(namespaceToManifest.first).append('\t').append(namespaceToManifest.second);
      if (!iterator.hasNext()) {
        return result.toString();
      }
      result.append('\n');
    }
  }

  @Nullable
  private static ModifiableFlexBuildConfiguration getFirstFlexBuildConfiguration(FlexProjectConfigurationEditor flexEditor, Module module) {
    ModifiableFlexBuildConfiguration[] configurations = flexEditor.getConfigurations(module);
    return configurations.length == 0 ? null : configurations[0];
  }

}
