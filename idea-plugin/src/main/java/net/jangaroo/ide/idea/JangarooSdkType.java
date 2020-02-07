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
import net.jangaroo.ide.idea.jps.JpsJangarooSdkType;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * A Jangaroo SDK specifies the location of all relevant compiler JARs.
 */
public class JangarooSdkType extends SdkType {

  public JangarooSdkType() {
    super(JpsJangarooSdkType.JANGAROO_SDK_TYPE_ID);
  }

  @Override
  public void setupSdkPaths(@NotNull Sdk sdk) {
    VirtualFile sdkRoot = sdk.getHomeDirectory();
    if (sdkRoot != null && sdkRoot.isValid()) {
      File sdkRootDir = VfsUtil.virtualToIoFile(sdkRoot);
      List<String> jarPaths = new ArrayList<String>();
      String sdkVersion = JpsJangarooSdkType.setupSdkPaths(sdkRootDir, jarPaths);
      if (sdkVersion != null) {
        SdkModificator modificator = sdk.getSdkModificator();
        modificator.setVersionString(sdkVersion);
        modificator.removeAllRoots();
        for (String jarPath : jarPaths) {
          addJarPath(modificator, jarPath);
        }
        modificator.commitChanges();
      }
    }
  }

  private static void addJarPath(SdkModificator modificator, @NotNull String jarPath) {
    VirtualFile jar = LocalFileSystem.getInstance().refreshAndFindFileByPath(jarPath);
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
  public String suggestSdkName(@Nullable String currentSdkName, String sdkHome) {
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
    File sdkRoot = sdkHome != null ? new File(sdkHome) : null;
    if (sdkRoot != null) {
      // check Jangaroo SDK Maven layout: 
      String joocJar = JpsJangarooSdkType.getVersionFromMavenLayout(sdkRoot);
      if (joocJar == null) {
        // check Jangaroo SDK download layout:
        joocJar = JpsJangarooSdkType.getVersionFromSdkLayout(sdkRoot);
      }
      return joocJar;
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

  public static JangarooSdkType getInstance() {
    return findInstance(JangarooSdkType.class);
  }

}
