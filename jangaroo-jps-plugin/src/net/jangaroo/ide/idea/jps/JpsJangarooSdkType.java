package net.jangaroo.ide.idea.jps;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsDummyElement;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.model.library.sdk.JpsSdkType;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JPS version of Jangaroo SDK type.
 */
public class JpsJangarooSdkType extends JpsSdkType<JpsDummyElement> {

  public static final JpsJangarooSdkType INSTANCE = new JpsJangarooSdkType();
  public static final String JANGAROO_COMPILER_API_ARTIFACT_ID = "jangaroo-compiler-api";
  public static final String JANGAROO_GROUP_ID = "net.jangaroo";
  public static final String JANGAROO_SDK_TYPE_ID = "Jangaroo SDK";
  private static final Pattern JANGAROO_COMPILER_API_JAR_PATTERN =
    Pattern.compile("^" + JANGAROO_COMPILER_API_ARTIFACT_ID + "-(([0-9]+(?:\\.[0-9]+)*)(-SNAPSHOT)?)\\.jar$");
  private static final String[] JANGAROO_COMPILER_ARTIFACT_IDS = new String[]{
    "jangaroo-compiler",
    "exml-compiler",
    "properties-compiler"
  };
  public static final String[] JANGAROO_3RD_PARTY_JARS = new String[]{
    "edu.princeton.cup:java-cup:10k",
    "commons-configuration:commons-configuration:1.10",
    "commons-io:commons-io:2.4",
    "commons-collections:commons-collections:3.2.1",
    "commons-lang:commons-lang:2.6",
    "org.apache.commons:commons-lang3:3.2.1",
    "commons-logging:commons-logging:1.1.1",
    "org.freemarker:freemarker:2.3.15",
    "com.google.guava:guava:18.0",
  };

  public static List<String> getSdkJarPaths(JpsSdk sdk) {
    ArrayList<String> jarPaths = new ArrayList<String>();
    setupSdkPaths(new File(sdk.getHomePath()), jarPaths);
    return jarPaths;
  }

  public static String setupSdkPaths(File sdkRootDir, List<String> jarPaths) {
    String sdkVersion;// check Jangaroo SDK Maven layout: 
    String mavenVersion = getVersionFromMavenLayout(sdkRootDir);
    if (mavenVersion != null) {
      sdkVersion = mavenVersion;
      File rootDirectory = sdkRootDir.getParentFile().getParentFile().getParentFile().getParentFile();
      for (String jangarooApiJarArtifact : JANGAROO_COMPILER_ARTIFACT_IDS) {
        jarPaths.add(getJangarooArtifact(rootDirectory, jangarooApiJarArtifact, mavenVersion).getPath());
      }
      for (String jangaroo3rdPartyJar : JANGAROO_3RD_PARTY_JARS) {
        String[] parts = jangaroo3rdPartyJar.split(":");
        jarPaths.add(getArtifactFile(rootDirectory, parts[0], parts[1], parts[2], "jar").getPath());
      }
    } else {
      // check Jangaroo SDK download layout:
      sdkVersion = getVersionFromSdkLayout(sdkRootDir);
      if (sdkVersion != null) {
        File binDirFile = new File(sdkRootDir, "bin");
        if (!binDirFile.exists()) {
          sdkVersion = null;
        } else {
          String fileSuffix = sdkVersion + "-jar-with-dependencies";
          for (String jangarooApiJarArtifact : JANGAROO_COMPILER_ARTIFACT_IDS) {
            jarPaths.add(getArtifactPath(binDirFile, jangarooApiJarArtifact, fileSuffix));
          }
        }
      }
    }
    return sdkVersion;
  }

  private static @NotNull String getArtifactPath(File dir, String artifactId, String version) {
    return new File(dir, artifactId + "-" + version + ".jar").getPath();
  }

  public static String getVersionFromSdkLayout(File sdkRoot) {
    File binDir = new File(sdkRoot, "bin");
    if (binDir.exists()) {
      return getVersionFromMavenLayout(binDir);
    }
    return null;
  }

  public static String getVersionFromMavenLayout(@NotNull File dir) {
    File[] children = dir.listFiles();
    if (children != null) {
      for (File child : children) {
        Matcher matcher = JANGAROO_COMPILER_API_JAR_PATTERN.matcher(child.getName());
        if (matcher.matches()) {
          return matcher.group(1);
        }
      }
    }
    return null;
  }

  public static File getJangarooArtifact(File localRepository, String artifactId, String version) {
    return getArtifactFile(localRepository, JANGAROO_GROUP_ID, artifactId, version, "jar");
  }

  // Cannot use IDEA's MavenArtifactUtil, as it is not available in the JPS JVM :-(
  @NotNull
  public static File getArtifactFile(File localRepository, String groupId, String artifactId, String version, String type) {
    String relativePath = String.format("%s/%s/%s/%s-%s.%s", groupId.replace('.', '/'), artifactId, version, artifactId, version, type);
    return new File(localRepository, relativePath);
  }

  @Override
  public String toString() {
    return "jangaroo sdk type";
  }
}
