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
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.refactoring.migration.MigrationMap;
import com.intellij.refactoring.migration.MigrationMapEntry;
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
import java.util.TreeMap;

/**
 * Loads the map to migrate ActionScript.
 */
public class FlexMigrationMapLoader {

  private static final Logger LOG = Logger.getInstance(FlexMigrationMapLoader.class);

  @Nullable
  static MigrationMap loadMigrationMap(GlobalSearchScope projectExt3Scope, GlobalSearchScope ext6Scope, String name) {
    Project project = projectExt3Scope.getProject();
    Collection<VirtualFile> candidates = FilenameIndex.getVirtualFilesByName(project, name, ext6Scope);
    if (candidates.isEmpty()) {
      error("Migration map '" + name + "' not found in " + ext6Scope.getDisplayName(), null);
      return null;
    }
    if (candidates.size() > 1) {
      warn("Found multiple migration maps '" + name + "' in " + ext6Scope.getDisplayName()
        + ". Will use the first of " + candidates);
    }

    InputStream is;
    try {
      is = candidates.iterator().next().getInputStream();
    } catch (IOException e) {
      error("Failed to load migration map '" + name + "' from '" + ext6Scope.getDisplayName(), e);
      return null;
    }
    Properties properties = new Properties();
    try {
      properties.load(is);
    } catch (IOException e) {
      error("Failed to load migration map '" + name + "' from '" + ext6Scope.getDisplayName(), e);
      return null;
    } finally {
      try {
        is.close();
      } catch (IOException e) {
        // ignore
      }
    }

    // use sorted map for reproducible results
    Map<String, String> map = new TreeMap<String, String>();
    for (String source : properties.stringPropertyNames()) {
      map.put(source, properties.getProperty(source));
    }
    addEntriesReplaceConfigClasses(map, projectExt3Scope);

    LOG.info("Migration Map: (" + map.size() + " entries): " + map);

    MigrationMap result = new MigrationMap();
    for (Map.Entry<String, String> entry : map.entrySet()) {
      result.addEntry(new MigrationMapEntry(entry.getKey(), entry.getValue(), MigrationMapEntry.CLASS, false));
    }
    return result;
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

  /**
   * @param migrationMap migration map to add entries to
   * @param projectExt3Scope search scope to lookup Ext AS 3.4 API and project
   */
  private static void addEntriesReplaceConfigClasses(Map<String, String> migrationMap,
                                                     GlobalSearchScope projectExt3Scope) {
    JSClass javaScriptObjectClass = getJavaScriptObjectClass(projectExt3Scope.getProject());
    if (javaScriptObjectClass == null) {
      error("joo.JavaScriptObject not found in project libraries", null);
      return;
    }

    Map<String, String> configToTargetClasses = getConfigToTargetClassMap(javaScriptObjectClass, projectExt3Scope);

    for (Map.Entry<String, String> entry : configToTargetClasses.entrySet()) {
      String source = entry.getKey();
      String target = entry.getValue();
      if (migrationMap.containsKey(source)) {
        error("Migration map contains config class: " + source, null);
      } else {
        if (migrationMap.containsKey(target)) {
          target = migrationMap.get(target);
        }
        migrationMap.put(source, target);
      }
    }
  }

  @NotNull
  private static Map<String, String> getConfigToTargetClassMap(JSClass configBaseClass, GlobalSearchScope scope) {
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
