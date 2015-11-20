package net.jangaroo.ide.idea.exml.migration;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.refactoring.migration.MigrationMap;
import com.intellij.refactoring.migration.MigrationMapEntry;
import org.jdom.Element;
import org.jdom.JDOMException;

import java.io.IOException;
import java.io.InputStream;

/**
 * Loads the map to migrate ActionScript/MXML.
 */
public class FlexMigrationMapLoader {

  private static final Logger LOG = Logger.getInstance(FlexMigrationManager.class);

  private static final String MIGRATION_MAP_ROOT = "migrationMap";
  private static final String ENTRY = "entry";
  private static final String NAME = "name";
  private static final String OLD_NAME = "oldName";
  private static final String NEW_NAME = "newName";
  private static final String DESCRIPTION = "description";
  private static final String VALUE = "value";
  private static final String TYPE = "type";
  private static final String PACKAGE_TYPE = "package";
  private static final String CLASS_TYPE = "class";
  private static final String RECURSIVE = "recursive";

  static MigrationMap loadMigrationMap(String migrationMap) {
    InputStream is = FlexMigrationManager.class.getResourceAsStream(migrationMap);
    if (is == null) {
      error("Migration map '" + migrationMap + "' not found in class path.", null);
      return null;
    }
    try {
      return loadMigrationMap(JDOMUtil.load(is));
    } catch (JDOMException e) {
      error("Error loading migration map '" + migrationMap + '\'', e);
    } catch (IOException e) {
      error("Error loading migration map '" + migrationMap + '\'', e);
    } catch (InvalidDataException e) {
      error("Invalid data in migration map '" + migrationMap + '\'', e);
    } finally {
      try {
        is.close();
      } catch (IOException e) {
        // ignore
      }
    }
    return null;
  }

  private static void error(String message, Exception cause) {
    LOG.error(message, cause);
    String s = cause == null ? message : message +  ": " + cause.toString();
    Notifications.Bus.notify(new Notification("jangaroo", "Cannot load migration map for ActionScript and MXML code",
      s, NotificationType.ERROR));
  }


  private static MigrationMap loadMigrationMap(Element root) throws InvalidDataException {
    if (!MIGRATION_MAP_ROOT.equals(root.getName())){
      throw new InvalidDataException();
    }
    MigrationMap map = new MigrationMap();

    for (Element node : root.getChildren()) {
      if (NAME.equals(node.getName())) {
        String name = node.getAttributeValue(VALUE);
        map.setName(name);
      }
      if (DESCRIPTION.equals(node.getName())) {
        String description = node.getAttributeValue(VALUE);
        map.setDescription(description);
      }

      if (ENTRY.equals(node.getName())) {
        MigrationMapEntry entry = new MigrationMapEntry();
        String oldName = getRequiredAttribute(node, OLD_NAME);
        entry.setOldName(oldName);
        String newName = getRequiredAttribute(node, NEW_NAME);
        entry.setNewName(newName);
        String typeStr = getRequiredAttribute(node, TYPE);
        entry.setType(MigrationMapEntry.CLASS);
        if (typeStr.equals(PACKAGE_TYPE)) {
          entry.setType(MigrationMapEntry.PACKAGE);
          String isRecursiveStr = node.getAttributeValue(RECURSIVE);
          if ("true".equals(isRecursiveStr)) {
            entry.setRecursive(true);
          } else {
            entry.setRecursive(false);
          }
        }
        map.addEntry(entry);
      }
    }

    return map;
  }

  private static String getRequiredAttribute(Element node, String name) throws InvalidDataException {
    String value = node.getAttributeValue(name);
    if (value == null) {
      throw new InvalidDataException("Element '" + node.getName() + "' without attribute '" + name + '\'');
    }
    return value;
  }

}
