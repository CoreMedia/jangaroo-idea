package net.jangaroo.ide.idea.exml.migration;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.migration.MigrationMap;
import com.intellij.refactoring.migration.MigrationMapEntry;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

/**
 * Loads the map to migrate ActionScript.
 */
public class FlexMigrationMapLoader {

  static MigrationMap loadMigrationMap(Project project, GlobalSearchScope ext6SearchScope, String migrationMap) {
    Collection<VirtualFile> candidates = FilenameIndex.getVirtualFilesByName(project, migrationMap, ext6SearchScope);
    if (candidates.isEmpty()) {
      error("Migration map '" + migrationMap + "' not found in " + ext6SearchScope.getDisplayName(), null);
      return null;
    }
    if (candidates.size() > 1) {
      warn("Found multiple migration maps '" + migrationMap + "' in " + ext6SearchScope.getDisplayName()
        + ". Will use the first of " + candidates);
    }

    InputStream is;
    try {
      is = candidates.iterator().next().getInputStream();
    } catch (IOException e) {
      error("Failed to load migration map '" + migrationMap + "' from '" + ext6SearchScope.getDisplayName(), e);
      return null;
    }
    Properties properties = new Properties();
    try {
      properties.load(is);
      return loadMigrationMap(properties);
    } catch (IOException e) {
      error("Failed to load migration map '" + migrationMap + "' from '" + ext6SearchScope.getDisplayName(), e);
    } finally {
      try {
        is.close();
      } catch (IOException e) {
        // ignore
      }
    }
    return null;
  }

  private static void warn(String message) {
    notify(message, null, NotificationType.WARNING);
  }

  private static void error(String message, Exception cause) {
    notify(message, cause, NotificationType.ERROR);
  }

  private static void notify(String message, Exception cause, NotificationType type) {
    String s = cause == null ? message : message +  ": " + cause.toString();
    Notifications.Bus.notify(new Notification("jangaroo", "Ext AS migration map",
      s, type));
  }

  private static MigrationMap loadMigrationMap(Properties properties) {
    // sort the keys for reproducible results
    Set<String> keys = new TreeSet<String>(properties.stringPropertyNames());

    MigrationMap map = new MigrationMap();
    for (String source : keys) {
      String target = properties.getProperty(source);
      map.addEntry(new MigrationMapEntry(source, target, MigrationMapEntry.CLASS, false));
    }
    return map;
  }

}
