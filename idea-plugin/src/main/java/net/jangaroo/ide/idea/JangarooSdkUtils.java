package net.jangaroo.ide.idea;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Ref;
import net.jangaroo.ide.idea.jps.JpsJangarooSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.execution.MavenRunner;
import org.jetbrains.idea.maven.execution.MavenRunnerParameters;
import org.jetbrains.idea.maven.execution.MavenRunnerSettings;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA. User: fwienber Date: 27.11.11 Time: 23:49 To change this template use File | Settings |
 * File Templates.
 */
public class JangarooSdkUtils {

  private JangarooSdkUtils() {
  }

  @NotNull
  private static Sdk createOrGetSdk(String sdkHomePath) {
    String sdkHomePathNormalized = normalizePath(sdkHomePath);
    String suggestedName = JangarooSdkType.getInstance().suggestSdkName(null, sdkHomePathNormalized);
    Sdk existingSdk = findSdkWithHomePathOrName(sdkHomePathNormalized, suggestedName);
    if (existingSdk == null) {
      return createSdk(sdkHomePath);
    }
    if (!sdkHomePathNormalized.equals(normalizePath(existingSdk.getHomePath()))) {
      // reusing existing SDK with the suggested name, but a different home path. Overwrite with the new home path:
      SdkModificator sdkModificator = existingSdk.getSdkModificator();
      sdkModificator.setHomePath(sdkHomePath);
      sdkModificator.commitChanges();
    }
    JangarooSdkType.getInstance().setupSdkPaths(existingSdk);
    return existingSdk;
  }

  @Nullable
  private static Sdk findSdkWithHomePathOrName(String sdkHomePath, String name) {
    Sdk existingSdk = null;
    for (Sdk sdk : getSdksOfType(JangarooSdkType.getInstance())) {
      String homePath = sdk.getHomePath();
      if (existingSdk == null && name.equals(sdk.getName())) {
        // remember first SDK with desired name, but continue in case we find a "better" SDK with desired home path:
        existingSdk = sdk;
      }
      if (sdkHomePath.equals(normalizePath(homePath))) {
        existingSdk = sdk;
        break;
      }
    }
    return existingSdk;
  }

  @Nullable
  private static String normalizePath(@Nullable String homePath) {
    return homePath == null ? null : homePath.replace('\\', '/');
  }

  private static Sdk createSdk(@NotNull final String sdkHomePath) {
    if (ApplicationManager.getApplication().isDispatchThread() || ApplicationManager.getApplication().isUnitTestMode()) {
      return doCreateSdk(sdkHomePath);
    }

    final Ref<Sdk> sdkRef = new Ref<>();
    ApplicationManager.getApplication().invokeAndWait(new Runnable() {
      public void run() {
        sdkRef.set(doCreateSdk(sdkHomePath));
      }
    }, ModalityState.defaultModalityState());

    return sdkRef.get();
  }

  private static Sdk doCreateSdk(@NotNull final String sdkHomePath) {
    return ApplicationManager.getApplication().runWriteAction(new Computable<Sdk>() {
      public Sdk compute() {
        SdkType sdkType = JangarooSdkType.getInstance();
        Sdk sdk = createProjectJdk(sdkType.suggestSdkName(null, sdkHomePath), "", sdkHomePath, sdkType);
        sdkType.setupSdkPaths(sdk);
        ProjectJdkTable projectJdkTable = ProjectJdkTable.getInstance();
        projectJdkTable.addJdk(sdk);
        return sdk;
      }
    });
  }

  @SuppressWarnings("SameParameterValue")
  private static Sdk createProjectJdk(final String name, final String version, final String homePath, final SdkType sdkType) {
    final ProjectJdkImpl projectJdk = new ProjectJdkImpl(name, sdkType);
    projectJdk.setHomePath(homePath);
    projectJdk.setVersionString(version);
    return projectJdk;
  }

  /**
   * Invoke Sdk#getSdkType() by reflection because of binary incompatibility between IDEA 11 and 12.
   */
  public static SdkType getSdkType(Sdk sdk) {
    return (SdkType)sdk.getSdkType();
  }

  /**
   * Invoke ProjectJskTable#getSdksOfType() by reflection because of binary incompatibility between IDEA 11 and 12.
   */
  private static List<Sdk> getSdksOfType(SdkTypeId sdkType) {
    return ProjectJdkTable.getInstance().getSdksOfType(sdkType);
  }

  static Sdk getOrCreateJangarooSdk(Project project, final String version) {
    MavenGeneralSettings mavenSettings = MavenProjectsManager.getInstance(project).getGeneralSettings();
    File localRepository = MavenUtil.resolveLocalRepository(mavenSettings.getLocalRepository(), mavenSettings.getMavenHome(), mavenSettings.getUserSettingsFile());
    File jarFile = JpsJangarooSdkType.getJangarooArtifact(localRepository, version);
    String sdkHomePath = jarFile.getParentFile().getAbsolutePath();
    final Sdk jangarooSdk = createOrGetSdk(sdkHomePath);
    if (!jarFile.exists()) {
      if (downloadJangarooCompilerMavenArtifact(project, version)) {
        Notifications.Bus.notify(new Notification("Maven", "Jangaroo Compiler Download",
          "Jangaroo Compiler " + version + " has been downloaded into your local Maven repository successfully.",
          NotificationType.INFORMATION));
        // Now that the JARs are actually there, retry setting up SDK paths:
        JangarooSdkType.getInstance().setupSdkPaths(jangarooSdk);
      } else {
        Notifications.Bus.notify(new Notification("Maven", "Jangaroo Compiler Download Failed",
          "Jangaroo Compiler " + version + " could not be downloaded into your local Maven repository."
            + " Please check 'Messages' window, fix problems and repeat Maven Import.",
          NotificationType.ERROR));
      }
    }
    return jangarooSdk;
  }

  private static boolean downloadJangarooCompilerMavenArtifact(Project project, final String version/*, Runnable onComplete*/) {
    // Download via Maven:
    MavenRunner mavenRunner = MavenRunner.getInstance(project);
    MavenRunnerParameters parameters = new MavenRunnerParameters();
    parameters.setGoals(Collections.singletonList("dependency:get"));
    // Execute Maven command in a temp dir, so that the goal is executed independently of any Maven project:
    Path tempDirectory;
    try {
      tempDirectory = Files.createTempDirectory("");
    } catch (IOException e) {
      return false;
    }
    try {
      String workingDirPath = tempDirectory.toString();
      parameters.setWorkingDirPath(workingDirPath);
      Map<String, String> envs = new HashMap<>();
      envs.put("groupId", JpsJangarooSdkType.JANGAROO_GROUP_ID);
      envs.put("artifactId", JpsJangarooSdkType.JANGAROO_COMPILER_ARTIFACT_ID);
      envs.put("version", version);
      envs.put("classifier", JpsJangarooSdkType.JAR_WITH_DEPENDENCIES_CLASSIFIER);
      envs.put("transitive", String.valueOf(false));
      MavenRunnerSettings settings = new MavenRunnerSettings();
      settings.setMavenProperties(envs);
      settings.setRunMavenInBackground(true);
      //mavenRunner.run(parameters, settings, onComplete);
      MavenGeneralSettings mavenSettings = MavenProjectsManager.getInstance(project).getGeneralSettings();
      if (mavenSettings.isWorkOffline()) {
        mavenSettings = mavenSettings.clone();
        mavenSettings.setWorkOffline(false);
      }
      return mavenRunner.runBatch(Collections.singletonList(parameters), mavenSettings, settings, version, null);
    } finally {
      try {
        Files.delete(tempDirectory);
      } catch (IOException ignored) {
      }
    }
  }
}
