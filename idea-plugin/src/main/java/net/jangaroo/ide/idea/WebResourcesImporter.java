package net.jangaroo.ide.idea;

import com.intellij.javaee.facet.JavaeeFacet;
import com.intellij.javaee.ui.packaging.ExplodedWarArtifactType;
import com.intellij.javaee.ui.packaging.JavaeeFacetResourcesPackagingElement;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactManager;
import com.intellij.packaging.artifacts.ModifiableArtifact;
import com.intellij.packaging.artifacts.ModifiableArtifactModel;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.elements.PackagingElementResolvingContext;
import com.intellij.packaging.impl.elements.ArtifactPackagingElement;
import com.intellij.packaging.impl.elements.DirectoryCopyPackagingElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.importing.MavenImporter;
import org.jetbrains.idea.maven.importing.MavenModifiableModelsProvider;
import org.jetbrains.idea.maven.importing.MavenRootModelAdapter;
import org.jetbrains.idea.maven.project.MavenConsole;
import org.jetbrains.idea.maven.project.MavenEmbeddersManager;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectChanges;
import org.jetbrains.idea.maven.project.MavenProjectsProcessorTask;
import org.jetbrains.idea.maven.project.MavenProjectsTree;
import org.jetbrains.idea.maven.project.SupportedRequestType;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A Facet-from-Maven Importer for the Jangaroo Facet type.
 */
public class WebResourcesImporter extends MavenImporter {
  public static final String JANGAROO_GROUP_ID = "net.jangaroo";
  private static final String JANGAROO_MAVEN_PLUGIN_ARTIFACT_ID = "jangaroo-maven-plugin";
  private static final String JANGAROO_PACKAGING_TYPE = "jangaroo";

  public WebResourcesImporter() {
    super(JANGAROO_GROUP_ID, JANGAROO_MAVEN_PLUGIN_ARTIFACT_ID);
  }

  @Override
  public void getSupportedPackagings(Collection<String> result) {
    super.getSupportedPackagings(result);
    result.add("war");
  }

  public boolean isApplicable(MavenProject mavenProjectModel) {
    return true;
  }

  @Override
  public void getSupportedDependencyTypes(Collection<String> result, SupportedRequestType type) {
    super.getSupportedDependencyTypes(result, type);
    result.add(JANGAROO_PACKAGING_TYPE);
    result.add("jar"); // for Jangaroo 2 + 3!
  }

  @Override
  public void preProcess(Module module, MavenProject mavenProject, MavenProjectChanges changes, MavenModifiableModelsProvider modifiableModelsProvider) {
    // nothing to do
  }

  @Override
  public void process(MavenModifiableModelsProvider modifiableModelsProvider, Module module, MavenRootModelAdapter rootModel, MavenProjectsTree mavenModel, MavenProject mavenProject, MavenProjectChanges changes, Map<MavenProject, String> mavenProjectToModuleName, List<MavenProjectsProcessorTask> postTasks) {
    //System.out.println("reimportFacet called!");
    postTasks.add(new UnpackJarResourcesIntoExplodedWebArtifactsTask(module));
  }

  private static class UnpackJarResourcesIntoExplodedWebArtifactsTask implements MavenProjectsProcessorTask {
    private final Module warModule;

    private UnpackJarResourcesIntoExplodedWebArtifactsTask(Module warModule) {
      this.warModule = warModule;
    }

    public void perform(final Project project, MavenEmbeddersManager mavenEmbeddersManager, MavenConsole mavenConsole, MavenProgressIndicator mavenProgressIndicator) throws MavenProcessCanceledException {
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          // for this Jangaroo-enabled Web app, add all Jangaroo-dependent modules' Jangaroo compiler output.

          // find the IDEA exploded Web artifact for this Jangaroo-enabled Web app module:
          final Artifact artifact = getExplodedWebArtifact(warModule);
          if (artifact != null) {
            // instruct IDEA to build the Web app on make:
            final ArtifactManager artifactManager = ArtifactManager.getInstance(project);
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

            // get all modules used by this Web app:
            Set<Module> dependencies = getTransitiveJarDependencies(warModule);
            // remove all modules already contained in some overlay:
            Set<Module> overlays = findTransitiveWarOverlays(artifactManager, artifact);
            for (Module overlay : overlays) {
              dependencies.remove(overlay);
              dependencies.removeAll(getTransitiveJarDependencies(overlay));
            }
            // add the remaining modules' Jangaroo packaging output to the Web app's root directory:
            CompositePackagingElement<?> rootDirectory = artifact.getRootElement();
            for (Module dependency : dependencies) {
              CompilerModuleExtension compilerModuleExtension = CompilerModuleExtension.getInstance(dependency);
              if (compilerModuleExtension != null) {
                VirtualFile compilerOutputPath = compilerModuleExtension.getCompilerOutputPath();
                if (compilerOutputPath != null) {
                  rootDirectory.addOrFindChild(new DirectoryCopyPackagingElement(compilerOutputPath.getPath() + "/META-INF/resources"));
                }
              }
            }
          }
        }
      });
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

    private static Set<Module> findTransitiveWarOverlays(ArtifactManager artifactManager, Artifact artifact) {
      Set<Module> overlays = new LinkedHashSet<Module>();
      for (PackagingElement<?> packagingElement : artifact.getRootElement().getChildren()) {
        if (packagingElement instanceof ArtifactPackagingElement) {
          String artifactName = ((ArtifactPackagingElement) packagingElement).getArtifactName();
          if (artifactName != null) {
            Artifact overlayArtifact = artifactManager.findArtifact(artifactName);
            if (overlayArtifact != null) {
              Module overlayModule = findModule(artifactManager, overlayArtifact);
              if (overlayModule != null) {
                overlays.add(overlayModule);
                overlays.addAll(findTransitiveWarOverlays(artifactManager, overlayArtifact));
              }
            }
          }
        }
      }
      return overlays;
    }

    private static Set<Module> getTransitiveJarDependencies(Module warModule) {
      return collectTransitiveJarDependencies(warModule, new LinkedHashSet<Module>());
    }

    private static Set<Module> collectTransitiveJarDependencies(Module warModule, Set<Module> dependencies) {
      dependencies.add(warModule);
      for (Module directDependency : ModuleRootManager.getInstance(warModule).getDependencies()) {
        collectTransitiveJarDependencies(directDependency, dependencies);
      }
      return dependencies;
    }

  }
}
