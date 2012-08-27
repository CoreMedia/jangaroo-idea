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
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;

import javax.swing.Icon;
import java.util.Arrays;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A Jangaroo SDK specifies the location of all relevant compiler JARs.
 */
public class JangarooSdkType extends SdkType {

  private static final Pattern JANGAROO_COMPILER_API_JAR_PATTERN =
    Pattern.compile("^" + JangarooFacetImporter.JANGAROO_COMPILER_API_ARTIFACT_ID + "-([0-9]+\\.[0-9]+(\\.|-preview-)[0-9]+(-SNAPSHOT)?)\\.jar$");

  public JangarooSdkType() {
    super("Jangaroo SDK");
  }

  @Override
  public void setupSdkPaths(Sdk sdk) {
    VirtualFile sdkRoot = sdk.getHomeDirectory();
    if (sdkRoot != null && sdkRoot.isValid()) {
      // check Jangaroo SDK Maven layout: 
      String mavenVersion = getVersionFromMavenLayout(sdkRoot);
      if (mavenVersion != null) {
        VirtualFile rootDirectory = sdkRoot.getParent().getParent();
        SdkModificator modificator = sdk.getSdkModificator();
        modificator.setVersionString(mavenVersion);
        addArtifact(rootDirectory, "jangaroo-compiler", mavenVersion, modificator);
        addArtifact(rootDirectory, "exml-compiler", mavenVersion, modificator);
        addArtifact(rootDirectory, "properties-compiler", mavenVersion, modificator);
        modificator.commitChanges();
      } else {
        // check Jangaroo SDK download layout:
        String sdkVersion = getVersionFromSdkLayout(sdkRoot);
        if (sdkVersion != null) {
          VirtualFile binDir = sdkRoot.findChild("bin");
          String fileSuffix = sdkVersion + "-jar-with-dependencies";
          SdkModificator modificator = sdk.getSdkModificator();
          modificator.setVersionString(sdkVersion);
          addArtifactIfExists(binDir, "jangaroo-compiler", fileSuffix, modificator);
          addArtifactIfExists(binDir, "exml-compiler", fileSuffix, modificator);
          addArtifactIfExists(binDir, "properties-compiler", fileSuffix, modificator);
          modificator.commitChanges();
        }
      }
    }
  }

  private static void addArtifact(VirtualFile dir, String artifactId, String version, SdkModificator modificator) {
    VirtualFile parentDir = dir.findChild(artifactId);
    if (parentDir != null) {
      VirtualFile versionDir = parentDir.findChild(version);
      addArtifactIfExists(versionDir, artifactId, version, modificator);
    }
  }

  private static void addArtifactIfExists(VirtualFile dir, String artifactId, String version, SdkModificator modificator) {
    if (dir != null) {
      VirtualFile artifact = dir.findChild(artifactId + "-" + version + ".jar");
      if (artifact != null) {
        VirtualFile jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(artifact);
        if (!Arrays.asList(modificator.getRoots(OrderRootType.CLASSES)).contains(jarRoot)) {
          modificator.addRoot(jarRoot, OrderRootType.CLASSES);
        }
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
  public void saveAdditionalData(SdkAdditionalData additionalData, Element additional) {
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
  public String getVersionString(Sdk sdk) {
    return super.getVersionString(sdk);    //To change body of overridden methods use File | Settings | File Templates.
  }

  @Override
  public SdkAdditionalData loadAdditionalData(Element additional) {
    return super.loadAdditionalData(additional);    //To change body of overridden methods use File | Settings | File Templates.
  }

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
