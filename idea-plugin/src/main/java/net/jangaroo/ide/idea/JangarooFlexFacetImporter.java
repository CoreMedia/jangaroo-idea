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
import com.intellij.lang.javascript.flex.sdk.FlexSdkUtils;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.impl.libraries.LibraryTableBase;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.importing.MavenModifiableModelsProvider;
import org.jetbrains.idea.maven.importing.MavenRootModelAdapter;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectChanges;
import org.jetbrains.idea.maven.project.MavenProjectsProcessorTask;
import org.jetbrains.idea.maven.project.MavenProjectsTree;
import org.jetbrains.idea.maven.project.SupportedRequestType;
import org.jetbrains.idea.maven.utils.MavenLog;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

//import net.jangaroo.jooc.config.CompilerConfigParser;
//import net.jangaroo.jooc.config.NamespaceConfiguration;

/**
 * A Facet-from-Maven Importer for the Jangaroo Facet type.
 */
public class JangarooFlexFacetImporter extends JangarooFacetImporter {

  @Override
  public void getSupportedPackagings(Collection<String> result) {
    // do *not* call super, this subclass does not support "war" packaging!
    result.add(JANGAROO_PACKAGING_TYPE);
  }

  @Override
  public void getSupportedDependencyTypes(Collection<String> result, SupportedRequestType type) {
    super.getSupportedDependencyTypes(result, type);
    result.add("jar"); // for Flex modules, "jar" dependencies (to Jangaroo libraries) are not handled automatically.
  }

  @NotNull
  @Override
  public ModuleType getModuleType() {
    return FlexModuleType.getInstance();
  }

  @Override
  protected void reimportFacet(MavenModifiableModelsProvider modelsProvider, Module module,
                               final MavenRootModelAdapter rootModel, JangarooFacet jangarooFacet,
                               MavenProjectsTree mavenTree, MavenProject mavenProjectModel,
                               MavenProjectChanges changes, Map<MavenProject, String> mavenProjectToModuleName,
                               List<MavenProjectsProcessorTask> postTasks) {
    FlexProjectConfigurationEditor currentEditor = getCurrentFlexProjectConfigurationEditor();
    FlexProjectConfigurationEditor flexEditor = currentEditor == null
      ? createFlexProjectConfigurationEditor(modelsProvider, module, rootModel)
      : currentEditor;

    doConfigure(flexEditor, modelsProvider, module, mavenProjectModel);

    if (currentEditor == null) {
      commitFlexProjectConfigurationEditor(flexEditor);
    }

    super.reimportFacet(modelsProvider, module, rootModel, jangarooFacet, mavenTree, mavenProjectModel, changes,
      mavenProjectToModuleName, postTasks);
  }

  private static FlexProjectConfigurationEditor getCurrentFlexProjectConfigurationEditor() {
    return FlexBuildConfigurationsExtension.getInstance().getConfigurator().getConfigEditor();
  }

  @NotNull
  private static FlexProjectConfigurationEditor createFlexProjectConfigurationEditor(MavenModifiableModelsProvider modelsProvider, final Module module, final MavenRootModelAdapter rootModel) {
    FlexProjectConfigurationEditor flexEditor;
    final LibraryTableBase.ModifiableModelEx projectLibrariesModel =
      (LibraryTableBase.ModifiableModelEx)modelsProvider.getProjectLibrariesModel();
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

  private void doConfigure(FlexProjectConfigurationEditor flexEditor, MavenModifiableModelsProvider modelsProvider,
                                    Module module, MavenProject mavenProjectModel) {
    String buildConfigurationName = mavenProjectModel.getMavenId().getArtifactId();
    if (buildConfigurationName == null) {
      return;
    }

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

    ModifiableDependencies modifiableDependencies = buildConfiguration.getDependencies();
    String flexSdkName = null;
    for (Sdk flexSdk : FlexSdkUtils.getFlexSdks()) {
      flexSdkName = flexSdk.getName();
      if ("FlexSDK4.6".equals(flexSdkName)) {
        break;
      }
    }
    if (flexSdkName == null) {
      Notifications.Bus.notify(new Notification("jangaroo", "No Flex SDK",
        "To use MXML, you have to have some Flex SDK installed.", NotificationType.WARNING));
    } else {
      modifiableDependencies.setSdkEntry(Factory.createSdkEntry(flexSdkName));
      modifiableDependencies.setFrameworkLinkage(LinkageType.External);
    }

    modifiableDependencies.getModifiableEntries().clear();
    StringBuilder namespaceConfigs = new StringBuilder();
    for (MavenArtifact dependency : mavenProjectModel.getDependencies()) {
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
              modifiableDependencies.getModifiableEntries().add(buildConfigurationEntry);
              // look for config.xml containing additional compiler settings:
//              for (VirtualFile sourceRoot : ModuleRootManager.getInstance(dependentModule).getSourceRoots()) {
//                parseCompilerConfig(sourceRoot, namespaceConfigs);
//              }
            }
          }
        } else {
          String libraryName = dependency.getLibraryName() + "-joo";
          Library library = modelsProvider.getLibraryByName(libraryName);
          if (library == null) {
            VirtualFile jooApiDir = artifactJarFile.findFileByRelativePath("META-INF/joo-api");
            if (jooApiDir != null) {
              library = modelsProvider.getProjectLibrariesModel().createLibrary(libraryName, FlexLibraryType.FLEX_LIBRARY);
              final LibraryEx.ModifiableModelEx libraryModifiableModel = ((LibraryEx.ModifiableModelEx)library.getModifiableModel());
              libraryModifiableModel.setProperties(FlexLibraryType.FLEX_LIBRARY.createDefaultProperties());
              libraryModifiableModel.addRoot(jooApiDir, OrderRootType.CLASSES);
              String sourcesPath = dependency.getPathForExtraArtifact("sources", null);
              VirtualFile sourcesJar = LocalFileSystem.getInstance().findFileByPath(sourcesPath);
              if (sourcesJar != null && sourcesJar.exists()) {
                libraryModifiableModel.addRoot(sourcesJar, OrderRootType.SOURCES);
              }
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

    if (namespaceConfigs.length() > 0) {
      buildConfiguration.getCompilerOptions().setAllOptions(Collections.singletonMap(
        "compiler.namespaces.namespace", namespaceConfigs.toString()
      ));
    }
  }

  @Nullable
  private static ModifiableFlexBuildConfiguration getFirstFlexBuildConfiguration(FlexProjectConfigurationEditor flexEditor, Module module) {
    ModifiableFlexBuildConfiguration[] configurations = flexEditor.getConfigurations(module);
    return configurations.length == 0 ? null : configurations[0];
  }

//  private static void parseCompilerConfig(VirtualFile baseDirOrJar, StringBuilder namespaceConfigs) {
//    VirtualFile compilerConfigXml = baseDirOrJar.findFileByRelativePath("config.xml");
//    if (compilerConfigXml != null) {
//      JoocConfiguration joocConfiguration = new JoocConfiguration();
//      try {
//        new CompilerConfigParser(joocConfiguration).parse(compilerConfigXml.getInputStream());
//        for (NamespaceConfiguration namespace : joocConfiguration.getNamespaces()) {
//          VirtualFile manifestFile = baseDirOrJar.findFileByRelativePath(namespace.getManifest());
//          if (manifestFile != null && manifestFile.exists()) {
//            if (namespaceConfigs.length() > 0) {
//              namespaceConfigs.append('\n');
//            }
//            namespaceConfigs.append(namespace.getUri()).append('\t').append(manifestFile.getPath());
//          } else {
//            Notifications.Bus.notify(new Notification("jangaroo", "Manifest file not found",
//              "Compiler config.xml " + compilerConfigXml.getPresentableName() + " contains a reference to manifest "
//                + namespace.getManifest() + ", but the file could not be found.", NotificationType.INFORMATION));
//          }
//        }
//
//      } catch (IOException e) {
//        Notifications.Bus.notify(new Notification("jangaroo", "Manifest file not found",
//          "Error while trying to read config.xml " + compilerConfigXml.getPresentableName() + ": " + e,
//          NotificationType.WARNING));
//      }
//    }
//  }

}
