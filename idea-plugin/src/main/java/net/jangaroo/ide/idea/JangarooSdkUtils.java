package net.jangaroo.ide.idea;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.peer.PeerFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA. User: fwienber Date: 27.11.11 Time: 23:49 To change this template use File | Settings |
 * File Templates.
 */
public class JangarooSdkUtils {

  @Nullable
  public static Sdk createOrGetSdk(SdkType sdkType, String sdkHomePath) {
    if (sdkHomePath == null || LocalFileSystem.getInstance().refreshAndFindFileByPath(sdkHomePath) == null) {
      return null;
    }
    ProjectJdkTable projectJdkTable = ProjectJdkTable.getInstance();
    String sdkHomePathNormalized = sdkHomePath.replace('\\', '/');
    for (Sdk sdk : projectJdkTable.getSdksOfType(sdkType)) {
      String homePath = sdk.getHomePath();
      if (homePath != null && homePath.replace('\\', '/').equals(sdkHomePathNormalized)) {
        if (sdkType instanceof JangarooSdkType) {
          sdkType.setupSdkPaths(sdk);
        }
        return sdk;
      }
    }
    return createSdk(sdkType, sdkHomePath);
  }

  private static Sdk createSdk(final SdkType sdkType, @NotNull final String sdkHomePath) {
    if ((ApplicationManager.getApplication().isDispatchThread()) || (ApplicationManager.getApplication().isUnitTestMode())) {
      return doCreateSdk(sdkType, sdkHomePath);
    }

    final Ref<Sdk> sdkRef = new Ref<Sdk>();
    ApplicationManager.getApplication().invokeAndWait(new Runnable() {
      public void run() {
        sdkRef.set(doCreateSdk(sdkType, sdkHomePath));
      }
    }, ModalityState.defaultModalityState());

    return sdkRef.get();
  }

  private static Sdk doCreateSdk(final SdkType sdkType, @NotNull final String sdkHomePath) {
    return ApplicationManager.getApplication().runWriteAction(new Computable<Sdk>() {
      public Sdk compute() {
        Sdk sdk = PeerFactory.getInstance().createProjectJdk(sdkType.suggestSdkName(null, sdkHomePath), "", sdkHomePath, sdkType);
        sdkType.setupSdkPaths(sdk);
        ProjectJdkTable projectJdkTable = ProjectJdkTable.getInstance();
        projectJdkTable.addJdk(sdk);
        return sdk;
      }
    });
  }

}
