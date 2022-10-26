package net.jangaroo.ide.idea.jps.util;

import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;
import java.net.URI;

/**
 * Utility methods that convert virtual file URLs to paths and vice versa.
 */
public class IdeaFileUtils {
  public static final String IDEA_URL_PREFIX = "file://";

  public static String toPath(String ideaUrl) {
    if (ideaUrl == null) {
      return "";
    }
    if (ideaUrl.startsWith(IDEA_URL_PREFIX)) {
      try {
        return new File(new URI(VfsUtilCore.fixIDEAUrl(ideaUrl))).getPath();
      } catch (Exception e) {
        ideaUrl = ideaUrl.substring(IDEA_URL_PREFIX.length());
      }
    }
    return ideaUrl.replace('/', File.separatorChar);
  }

  public static String toIdeaUrl(String path) {
    path = path.trim();
    if (path.length()==0) {
      return path;
    }
    VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(path);
    return virtualFile==null ? IDEA_URL_PREFIX + path.replace(File.separatorChar, '/') : virtualFile.getUrl();
  }
}
