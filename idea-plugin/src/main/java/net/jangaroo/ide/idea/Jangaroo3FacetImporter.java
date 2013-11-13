package net.jangaroo.ide.idea;

import com.intellij.lang.javascript.flex.FlexModuleType;
import com.intellij.lang.javascript.flex.library.FlexLibraryProperties;
import com.intellij.lang.javascript.flex.library.FlexLibraryType;
import com.intellij.lang.javascript.flex.projectStructure.model.FlexBuildConfigurationManager;
import com.intellij.flex.model.bc.LinkageType;
import com.intellij.lang.javascript.flex.projectStructure.model.ModifiableBuildConfigurationEntry;
import com.intellij.lang.javascript.flex.projectStructure.model.ModifiableDependencies;
import com.intellij.lang.javascript.flex.projectStructure.model.ModifiableFlexBuildConfiguration;
import com.intellij.lang.javascript.flex.projectStructure.model.ModifiableSharedLibraryEntry;
import com.intellij.flex.model.bc.OutputType;
import com.intellij.lang.javascript.flex.projectStructure.model.impl.ConversionHelper;
import com.intellij.lang.javascript.flex.projectStructure.model.impl.Factory;
import com.intellij.lang.javascript.flex.sdk.FlexSdkUtils;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.importing.MavenModifiableModelsProvider;
import org.jetbrains.idea.maven.importing.MavenRootModelAdapter;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.project.MavenConsole;
import org.jetbrains.idea.maven.project.MavenEmbeddersManager;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectChanges;
import org.jetbrains.idea.maven.project.MavenProjectsProcessorTask;
import org.jetbrains.idea.maven.project.MavenProjectsTree;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * A Facet-from-Maven Importer for the Jangaroo Facet type.
 */
public class Jangaroo3FacetImporter extends JangarooFacetImporter {

  @Override
  protected boolean isApplicableVersion(int majorVersion) {
    return majorVersion == 3;
  }

  @NotNull
  @Override
  public ModuleType getModuleType() {
    return FlexModuleType.getInstance();
  }

  @Override
  protected void reimportFacet(MavenModifiableModelsProvider modelsProvider, Module module,
                               MavenRootModelAdapter rootModel, JangarooFacet jangarooFacet,
                               MavenProjectsTree mavenTree, MavenProject mavenProjectModel,
                               MavenProjectChanges changes, Map<MavenProject, String> mavenProjectToModuleName,
                               List<MavenProjectsProcessorTask> postTasks) {
    //System.out.println("reimportFacet called!");
    FlexBuildConfigurationManager flexBuildConfigurationManager = FlexBuildConfigurationManager.getInstance(module);
    ModifiableFlexBuildConfiguration buildConfiguration = (ModifiableFlexBuildConfiguration)flexBuildConfigurationManager.getActiveConfiguration();
    String buildConfigurationName = mavenProjectModel.getName();
    if (buildConfigurationName == null) {
      buildConfigurationName = mavenProjectModel.getFinalName();
    }
    buildConfiguration.setName(buildConfigurationName);
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
              modifiableModel.setKind(FlexLibraryType.FLEX_LIBRARY);
              modifiableModel.setProperties(new FlexLibraryProperties(libraryName));
              modifiableModel.addRoot(jooApiDir, OrderRootType.CLASSES);
              String sourcesPath = dependency.getPathForExtraArtifact("sources", null);
              VirtualFile sourcesJar = LocalFileSystem.getInstance().findFileByPath(sourcesPath);
              if (sourcesJar != null && sourcesJar.exists()) {
                modifiableModel.addRoot(sourcesJar, OrderRootType.SOURCES);
              }
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

    super.reimportFacet(modelsProvider, module, rootModel, jangarooFacet, mavenTree, mavenProjectModel, changes,
      mavenProjectToModuleName, postTasks);
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
              ModifiableFlexBuildConfiguration buildConfiguration = (ModifiableFlexBuildConfiguration)flexBuildConfigurationManager.getActiveConfiguration();

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
