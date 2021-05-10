package net.jangaroo.ide.idea.jps;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsDummyElement;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.model.library.sdk.JpsSdkType;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * JPS version of Jangaroo SDK type.
 */
public class JpsJangarooSdkType extends JpsSdkType<JpsDummyElement> {

  public static final JpsJangarooSdkType INSTANCE = new JpsJangarooSdkType();
  public static final String JANGAROO_SDK_TYPE_ID = "Jangaroo SDK";

  public static final String JANGAROO_GROUP_ID = "net.jangaroo";
  public static final String JANGAROO_COMPILER_ARTIFACT_ID = "jangaroo-compiler";
  public static final String JAR_WITH_DEPENDENCIES_CLASSIFIER = "jar-with-dependencies";

  private static final Pattern MAVEN_VERSION_PATTERN = Pattern.compile("(([0-9]+(\\.[0-9]+)*)(-alpha-[0-9]+)?(-SNAPSHOT)?)");

  static List<String> getSdkJarPaths(JpsSdk sdk) {
    List<String> jarPaths = new ArrayList<>();
    setupSdkPaths(new File(sdk.getHomePath()), jarPaths);
    return jarPaths;
  }

  @SuppressWarnings("TypeMayBeWeakened")
  public static String setupSdkPaths(File sdkRootDir, List<String> jarPaths) {
    String sdkVersion;// check Jangaroo SDK Maven layout: 
    String mavenVersion = getVersionFromMavenLayout(sdkRootDir);
    if (mavenVersion != null) {
      sdkVersion = mavenVersion;
      File rootDirectory = sdkRootDir.getParentFile().getParentFile().getParentFile().getParentFile();
      File jangarooArtifact = getJangarooArtifact(rootDirectory, mavenVersion);
      if (jangarooArtifact.exists()) {
        jarPaths.add(jangarooArtifact.getPath());
      }
    } else {
      // check Jangaroo SDK download layout:
      sdkVersion = getVersionFromSdkLayout(sdkRootDir);
      if (sdkVersion != null) {
        File binDirFile = new File(sdkRootDir, "bin");
        if (!binDirFile.exists()) {
          sdkVersion = null;
        } else {
          jarPaths.add(new File(binDirFile, JANGAROO_COMPILER_ARTIFACT_ID + "-" + sdkVersion + "-" + JAR_WITH_DEPENDENCIES_CLASSIFIER + ".jar").getPath());
        }
      }
    }
    return sdkVersion;
  }

  @Nullable
  public static String getVersionFromSdkLayout(File sdkRoot) {
    File binDir = new File(sdkRoot, "bin");
    if (binDir.exists()) {
      return getVersionFromMavenLayout(binDir);
    }
    return null;
  }

  @Nullable
  public static String getVersionFromMavenLayout(@NotNull File dir) {
    String versionCandidate = dir.getName();
    return MAVEN_VERSION_PATTERN.matcher(versionCandidate).matches() ? versionCandidate : null;
  }

  public static File getJangarooArtifact(File localRepository, String version) {
    return getArtifactFile(localRepository, JANGAROO_GROUP_ID, JANGAROO_COMPILER_ARTIFACT_ID, version, JAR_WITH_DEPENDENCIES_CLASSIFIER, "jar");
  }

  // Cannot use IDEA's MavenArtifactUtil, as it is not available in the JPS JVM :-(
  @SuppressWarnings("SameParameterValue")
  @NotNull
  private static File getArtifactFile(File localRepository, String groupId, String artifactId, String version, @Nullable String classifier, String type) {
    String relativePath = String.format("%s/%s/%s/%s-%s%s.%s", groupId.replace('.', '/'), artifactId, version, artifactId, version, classifier == null || classifier.isEmpty() ? "" : ("-" + classifier), type);
    return new File(localRepository, relativePath);
  }

  @Override
  public String toString() {
    return "jangaroo sdk type";
  }
}
