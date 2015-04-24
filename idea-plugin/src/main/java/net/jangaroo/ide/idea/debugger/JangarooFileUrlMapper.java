package net.jangaroo.ide.idea.debugger;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Url;
import com.intellij.util.Urls;
import com.jetbrains.javascript.debugger.FileUrlMapper;
import net.jangaroo.ide.idea.Utils;
import net.jangaroo.jooc.api.Jooc;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Map Source Map URLs to local source files in some module.
 */
public class JangarooFileUrlMapper extends FileUrlMapper {

  public static final String AS3_SOURCE_URL_PATH_PREFIX = "/amd/as3/";

  @NotNull
  @Override
  public List<Url> getUrls(@NotNull VirtualFile sourceFile, @NotNull Project project, String authority) {
    final String relativePath = Utils.getModuleRelativeSourcePath(project, sourceFile, '/');
    if (relativePath.length() != 0) {
      return Collections.singletonList(Urls.newUrl("http", authority, AS3_SOURCE_URL_PATH_PREFIX + relativePath));
    }
    return Collections.emptyList();
  }

  @Nullable
  @Override
  public VirtualFile getFile(@NotNull Url sourceUrl, @NotNull Project project, Url pageUrl) {
    String path = sourceUrl.getPath();
    if (path.endsWith(Jooc.AS_SUFFIX) && path.startsWith(AS3_SOURCE_URL_PATH_PREFIX)) {
      String fullyQualifiedName =
        path.substring(AS3_SOURCE_URL_PATH_PREFIX.length(), path.length() - Jooc.AS_SUFFIX.length())
          .replace('/', '.');
      return Utils.getActionScriptFile(project, fullyQualifiedName);
    }
    return null;
  }

}
