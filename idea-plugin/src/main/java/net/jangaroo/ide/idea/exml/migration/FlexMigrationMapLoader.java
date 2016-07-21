package net.jangaroo.ide.idea.exml.migration;

import com.intellij.lang.javascript.psi.ecmal4.JSAttribute;
import com.intellij.lang.javascript.psi.ecmal4.JSAttributeList;
import com.intellij.lang.javascript.psi.ecmal4.JSAttributeNameValuePair;
import com.intellij.lang.javascript.psi.ecmal4.JSClass;
import com.intellij.lang.javascript.psi.ecmal4.JSQualifiedNamedElement;
import com.intellij.lang.javascript.psi.resolve.JSClassResolver;
import com.intellij.lang.javascript.search.JSClassSearch;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.util.Query;
import net.jangaroo.ide.idea.Utils;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Properties;
import java.util.SortedMap;
import java.util.TreeMap;

import static net.jangaroo.ide.idea.exml.migration.MigrationMapEntryType.CONFIG_CLASS;
import static net.jangaroo.ide.idea.exml.migration.MigrationMapEntryType.PROPERTIES_CLASS;
import static net.jangaroo.ide.idea.exml.migration.MigrationMapEntryType.REPLACE;

/**
 * Loads the map to migrate ActionScript.
 */
public class FlexMigrationMapLoader {

  private static final Logger LOG = Logger.getInstance(FlexMigrationMapLoader.class);

  private FlexMigrationMapLoader() {
  }

  @Nullable
  static SortedMap<String, MigrationMapEntry> load(Project project, VirtualFile migrationMap, boolean migrateApi,
                                                   boolean migrateConfigClasses, boolean migrateProperties) {
    // use sorted map for reproducible results
    SortedMap<String, MigrationMapEntry> map = new TreeMap<String, MigrationMapEntry>();

    if (migrateApi && migrationMap != null) {
      addEntriesToReplaceApiElements(map, migrationMap);
    }
    if (migrateConfigClasses || migrateProperties) {
      addEntriesToReplaceGeneratedClasses(project, map, migrateConfigClasses, migrateProperties);
    }

    LOG.info("Migration Map: (" + map.size() + " entries): " + map.values());
    return map;
  }

  private static void addEntriesToReplaceApiElements(SortedMap<String, MigrationMapEntry> map, VirtualFile migrationMap) {
    Properties properties = new Properties();
    InputStream is = null;
    try {
      is = migrationMap.getInputStream();
      properties.load(is);
    } catch (IOException e) {
      if (is != null) {
        try {
          is.close();
        } catch (IOException ignored) {
          LOG.debug("Ignored", ignored);
        }
      }
      error("Failed to load migration map", e);
    }
    for (String source : properties.stringPropertyNames()) {
      map.put(source, new MigrationMapEntry(source, properties.getProperty(source), REPLACE));
    }
  }

  private static void addEntriesToReplaceGeneratedClasses(Project project, SortedMap<String, MigrationMapEntry> map,
                                                          boolean migrateConfigClasses, boolean migrateProperties) {
    JSClass jsObjectClass = getJavaScriptObjectClass(project);
    if (jsObjectClass == null) {
      error("joo.JavaScriptObject not found in project libraries", null);
      return;
    }

    GlobalSearchScope scope = ProjectScope.getAllScope(project);
    Query<JSClass> jsObjectClasses = JSClassSearch.searchClassInheritors(jsObjectClass, true, scope);

    for (JSClass clazz : jsObjectClasses) {
      String clazzQualifiedName = clazz.getQualifiedName();
      if (clazzQualifiedName == null) {
        continue;
      }

      if (migrateConfigClasses) {
        JSAttribute configAttribute = getAttribute(clazz, "ExtConfig");
        if (configAttribute != null) {
          // replace usages of generated config classes by target classes
          JSAttributeNameValuePair targetPair = configAttribute.getValueByName("target");
          if (targetPair != null) {
            String targetClassName = targetPair.getSimpleValue();
            MigrationMapEntry existingEntry = map.get(targetClassName);
            String target = existingEntry == null ? targetClassName : existingEntry.getNewName();
            map.put(clazzQualifiedName, new MigrationMapEntry(clazzQualifiedName, target, CONFIG_CLASS));
          }
        }
      }

      if (migrateProperties && clazzQualifiedName.endsWith("_properties")) {
        // replace usages of generated properties classes by new ResourceManager bundle lookup
        JSAttribute propertiesAttribute = getAttribute(clazz, "ResourceBundle");
        if (propertiesAttribute != null) {
          map.put(clazzQualifiedName, new MigrationMapEntry(clazzQualifiedName, null, PROPERTIES_CLASS));
        }
      }
    }
  }

  private static JSAttribute getAttribute(JSClass clazz, String attributeName) {
    JSAttributeList attributeList = clazz.getAttributeList();
    return attributeList == null ? null : attributeList.findAttributeByName(attributeName);
  }

  private static void error(String message, Exception cause) {
    String s = cause == null ? message : message +  ": " + cause.toString();
    Notifications.Bus.notify(new Notification("jangaroo", "Ext AS migration map", s, NotificationType.ERROR));
  }

  private static JSClass getJavaScriptObjectClass(Project project) {
    JSClassResolver resolver = Utils.getActionScriptClassResolver();
    GlobalSearchScope librariesScope = ProjectScope.getLibrariesScope(project);
    Collection<JSQualifiedNamedElement> elements = resolver.findElementsByQName("joo.JavaScriptObject", librariesScope);
    for (JSQualifiedNamedElement element : elements) {
      if (element.isValid() && element instanceof JSClass) {
        return (JSClass) element;
      }
    }
    return null;
  }

}
