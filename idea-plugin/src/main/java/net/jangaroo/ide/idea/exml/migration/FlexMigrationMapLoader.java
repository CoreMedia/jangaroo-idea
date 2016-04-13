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
import com.intellij.util.containers.HashMap;
import net.jangaroo.ide.idea.Utils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Map;
import java.util.Properties;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Loads the map to migrate ActionScript.
 */
public class FlexMigrationMapLoader {

  private static final Logger LOG = Logger.getInstance(FlexMigrationMapLoader.class);

  @Nullable
  static SortedMap<String, MigrationMapEntry> load(Project project, VirtualFile migrationMap) {
    InputStream is;
    try {
      is = migrationMap.getInputStream();
    } catch (IOException e) {
      error("Failed to load migration map", e);
      return null;
    }
    Properties properties = new Properties();
    try {
      properties.load(is);
    } catch (IOException e) {
      error("Failed to load migration map", e);
      return null;
    } finally {
      try {
        is.close();
      } catch (IOException e) {
        // ignore
      }
    }

    // use sorted map for reproducible results
    SortedMap<String, MigrationMapEntry> map = new TreeMap<String, MigrationMapEntry>();
    for (String source : properties.stringPropertyNames()) {
      map.put(source, new MigrationMapEntry(source, properties.getProperty(source), false));
    }
    addEntriesReplaceConfigClasses(project, map);

    LOG.info("Migration Map: (" + map.size() + " entries): " + map.values());
    return map;
  }

  private static void error(String message, Exception cause) {
    String s = cause == null ? message : message +  ": " + cause.toString();
    Notifications.Bus.notify(new Notification("jangaroo", "Ext AS migration map", s, NotificationType.ERROR));
  }

  /**
   * @param project the project
   * @param migrationMap migration map to add entries to
   */
  private static void addEntriesReplaceConfigClasses(Project project, SortedMap<String, MigrationMapEntry> migrationMap) {
    JSClass javaScriptObjectClass = getJavaScriptObjectClass(project);
    if (javaScriptObjectClass == null) {
      error("joo.JavaScriptObject not found in project libraries", null);
      return;
    }

    Map<String, String> configToTargetClasses = getConfigToTargetClassMap(project, javaScriptObjectClass);

    for (Map.Entry<String, String> entry : configToTargetClasses.entrySet()) {
      String source = entry.getKey();
      String target = entry.getValue();
      if (migrationMap.containsKey(source)) {
        error("Migration map contains config class: " + source, null);
      } else {
        if (migrationMap.containsKey(target)) {
          MigrationMapEntry existingEntry = migrationMap.get(target);
          target = existingEntry.getNewName();
        }
        migrationMap.put(source, new MigrationMapEntry(source, target, true));
      }
    }
  }

  @NotNull
  private static Map<String, String> getConfigToTargetClassMap(Project project, JSClass configBaseClass) {
    GlobalSearchScope scope = ProjectScope.getAllScope(project);
    Query<JSClass> cfgClasses = JSClassSearch.searchClassInheritors(configBaseClass, true, scope);

    Map<String, String> configToTargetClasses = new HashMap<String, String>();
    for (JSClass cfgClass : cfgClasses) {
      JSAttributeList attributeList = cfgClass.getAttributeList();
      if (attributeList != null) {
        JSAttribute[] attributes = attributeList.getAttributes();
        for (JSAttribute attribute : attributes) {
          if ("ExtConfig".equals(attribute.getName())) {
            JSAttributeNameValuePair targetPair = attribute.getValueByName("target");
            if (targetPair != null) {
              String targetClassName = targetPair.getSimpleValue();
              configToTargetClasses.put(cfgClass.getQualifiedName(), targetClassName);
            }
            break;
          }
        }
      }
    }
    return configToTargetClasses;
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
