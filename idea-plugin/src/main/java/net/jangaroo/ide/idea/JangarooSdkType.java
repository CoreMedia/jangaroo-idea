package net.jangaroo.ide.idea;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.projectRoots.AdditionalDataConfigurable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.projectRoots.SdkModel;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.roots.JavadocOrderRootType;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import net.jangaroo.ide.idea.jps.JangarooSdkPropertiesSerializer;
import net.jangaroo.ide.idea.jps.JpsJangarooSdkProperties;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A Jangaroo SDK specifies the location of all relevant compiler JARs.
 */
public class JangarooSdkType extends SdkType {

  private static final Pattern JANGAROO_COMPILER_API_JAR_PATTERN =
    Pattern.compile("^" + JangarooFacetImporter.JANGAROO_COMPILER_API_ARTIFACT_ID + "-([0-9]+\\.[0-9]+(\\.|-preview-)[0-9]+(-SNAPSHOT)?)\\.jar$");

  private static final String[] JANGAROO_API_JAR_ARTIFACTS = new String[]{
    "jangaroo-compiler",
    "exml-compiler",
    "properties-compiler"
  };
  public JangarooSdkType() {
    super(JangarooSdkPropertiesSerializer.JANGAROO_SDK_TYPE_ID);
  }

  @Override
  public void setupSdkPaths(@NotNull Sdk sdk) {
    VirtualFile sdkRoot = sdk.getHomeDirectory();
    if (sdkRoot != null && sdkRoot.isValid()) {
      String sdkVersion;
      List<String> jarPaths = new ArrayList<String>();
      // check Jangaroo SDK Maven layout: 
      String mavenVersion = getVersionFromMavenLayout(sdkRoot);
      if (mavenVersion != null) {
        sdkVersion = mavenVersion;
        File rootDirectory = new File(sdkRoot.getParent().getParent().getPath());
        for (String jangarooApiJarArtifact : JANGAROO_API_JAR_ARTIFACTS) {
          jarPaths.add(JangarooSdkUtils.getJangarooArtifact(rootDirectory, jangarooApiJarArtifact, mavenVersion).getPath());
        }
      } else {
        // check Jangaroo SDK download layout:
        sdkVersion = getVersionFromSdkLayout(sdkRoot);
        if (sdkVersion != null) {
          VirtualFile binDir = sdkRoot.findChild("bin");
          if (binDir == null) {
            return;
          }
          File binDirFile = new File(binDir.getPath());
          String fileSuffix = sdkVersion + "-jar-with-dependencies";
          for (String jangarooApiJarArtifact : JANGAROO_API_JAR_ARTIFACTS) {
            jarPaths.add(getArtifactPath(binDirFile, jangarooApiJarArtifact, fileSuffix));
          }
        }
      }
      if (sdkVersion != null) {
        SdkModificator modificator = sdk.getSdkModificator();
        modificator.setVersionString(sdkVersion);
        for (String jarPath : jarPaths) {
          addJarPath(modificator, jarPath);
        }
        modificator.setSdkAdditionalData(new JpsJangarooSdkProperties(jarPaths));
        modificator.commitChanges();
      }
    }
  }

  private static @NotNull String getArtifactPath(File dir, String artifactId, String version) {
    return new File(dir, artifactId + "-" + version + ".jar").getPath();
  }

  private static void addJarPath(SdkModificator modificator, @NotNull String jarPath) {
    VirtualFile jar = LocalFileSystem.getInstance().findFileByPath(jarPath);
    if (jar != null) {
      VirtualFile jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(jar);
      if (!Arrays.asList(modificator.getRoots(OrderRootType.CLASSES)).contains(jarRoot)) {
        modificator.addRoot(jarRoot, OrderRootType.CLASSES);
      }
    }
  }

  @Override
  public String suggestHomePath() {
    return null;
  }

  @Override
  public boolean isValidSdkHome(String path) {
    return getVersionString(path) != null;
  }

  @Override
  public String suggestSdkName(String currentSdkName, String sdkHome) {
    return "Jangaroo SDK " + getVersionString(sdkHome);
  }

  @Override
  public AdditionalDataConfigurable createAdditionalDataConfigurable(SdkModel sdkModel, SdkModificator sdkModificator) {
    return null;
  }

  @Override
  public void saveAdditionalData(@NotNull SdkAdditionalData additionalData, @NotNull Element additional) {
  }

  @Override
  public String getPresentableName() {
    return "Jangaroo SDK";
  }

  public Icon getIconForAddAction() {
    return getIcon();
  }

  @Override
  public Icon getIcon() {
    return IconLoader.getIcon(JangarooFacetType.JANGAROO_FACET_ICON_URL);
  }

  @Override
  public String getVersionString(String sdkHome) {
    VirtualFile sdkRoot = sdkHome != null ? VfsUtil.findRelativeFile(sdkHome, null) : null;
    if (sdkRoot != null && sdkRoot.isValid()) {
      // check Jangaroo SDK Maven layout: 
      String joocJar = getVersionFromMavenLayout(sdkRoot);
      if (joocJar == null) {
        // check Jangaroo SDK download layout:
        joocJar = getVersionFromSdkLayout(sdkRoot);
      }
      return joocJar;
    }
    return null;
  }

  private String getVersionFromSdkLayout(VirtualFile sdkRoot) {
    VirtualFile binDir = sdkRoot.findChild("bin");
    if (binDir != null && binDir.isValid()) {
      return getVersionFromMavenLayout(binDir);
    }
    return null;
  }

  private static String getVersionFromMavenLayout(VirtualFile dir) {
    for (VirtualFile child : dir.getChildren()) {
      Matcher matcher = JANGAROO_COMPILER_API_JAR_PATTERN.matcher(child.getName());
      if (matcher.matches()) {
        return matcher.group(1);
      }
    }
    return null;
  }

  @Override
  public boolean isRootTypeApplicable(OrderRootType type) {
    return type == OrderRootType.CLASSES || type == OrderRootType.SOURCES || type == JavadocOrderRootType.getInstance();
  }

  @Override
  public Collection<String> suggestHomePaths() {
    return super.suggestHomePaths();    //To change body of overridden methods use File | Settings | File Templates.
  }

  @Override
  public String adjustSelectedSdkHome(String homePath) {
    return super.adjustSelectedSdkHome(homePath);    //To change body of overridden methods use File | Settings | File Templates.
  }

  @Override
  public String getVersionString(@NotNull Sdk sdk) {
    return super.getVersionString(sdk);    //To change body of overridden methods use File | Settings | File Templates.
  }

  @Override
  public SdkAdditionalData loadAdditionalData(Element additional) {
    return super.loadAdditionalData(additional);    //To change body of overridden methods use File | Settings | File Templates.
  }

  @NotNull
  @Override
  public String getName() {
    return super.getName();    //To change body of overridden methods use File | Settings | File Templates.
  }

  @Override
  public FileChooserDescriptor getHomeChooserDescriptor() {
    return super.getHomeChooserDescriptor();    //To change body of overridden methods use File | Settings | File Templates.
  }

  @Override
  public String getHomeFieldLabel() {
    return super.getHomeFieldLabel();    //To change body of overridden methods use File | Settings | File Templates.
  }

  public static SdkType getInstance() {
    return findInstance(JangarooSdkType.class);
  }

}
