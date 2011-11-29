package net.jangaroo.ide.idea.util;

import net.jangaroo.exml.api.Exmlc;
import net.jangaroo.jooc.api.Jooc;
import net.jangaroo.properties.api.Propc;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Load Jangaroo compilers of a specified version, using a custom class loader.
 */
public class CompilerLoader {

  private static final Map<List<String>,ClassLoader> CLASS_LOADER_BY_JAR_FILES_CACHE = new HashMap<List<String>, ClassLoader>();

  public static Jooc loadJooc(List<String> jarFileNames) throws FileNotFoundException, ClassNotFoundException, InstantiationException, IllegalAccessException {
    return (Jooc)instantiateClass("net.jangaroo.jooc.Jooc", jarFileNames);
  }

  public static Exmlc loadExmlc(List<String> jarFileNames) throws FileNotFoundException, ClassNotFoundException, InstantiationException, IllegalAccessException {
    return (Exmlc)instantiateClass("net.jangaroo.exml.compiler.Exmlc", jarFileNames);
  }

  public static Propc loadPropc(List<String> jarFileNames) throws FileNotFoundException, ClassNotFoundException, InstantiationException, IllegalAccessException {
    return (Propc)instantiateClass("net.jangaroo.properties.PropertyClassGenerator", jarFileNames);
  }

  private static Object instantiateClass(String mainClassName, List<String> jarFileNames) throws FileNotFoundException, ClassNotFoundException, InstantiationException, IllegalAccessException {
    ClassLoader jooClassLoader = getClassLoader(jarFileNames);
    Class<?> joocClass = jooClassLoader.loadClass(mainClassName);
    return joocClass.newInstance();
  }

  private static ClassLoader getClassLoader(List<String> jarFileNames) throws FileNotFoundException {
    return anyEndsWith(jarFileNames, "-SNAPSHOT.jar")
      ? createClassLoader(jarFileNames)
      : getCachedClassLoader(jarFileNames);
  }

  private static boolean anyEndsWith(List<String> strings, String suffix) {
    for (String string : strings) {
      if (string.endsWith(suffix)) {
        return true;
      }
    }
    return false;
  }

  private synchronized static ClassLoader getCachedClassLoader(List<String> jarFileNames) throws FileNotFoundException {
    ClassLoader classLoader = CLASS_LOADER_BY_JAR_FILES_CACHE.get(jarFileNames);
    if (classLoader == null) {
      classLoader = createClassLoader(jarFileNames);
      CLASS_LOADER_BY_JAR_FILES_CACHE.put(jarFileNames, classLoader);
    }
    return classLoader;
  }

  private static ClassLoader createClassLoader(List<String> jarFileNames) throws FileNotFoundException {
    URL[] urls = new URL[jarFileNames.size()];
    for (int i = 0; i < jarFileNames.size(); i++) {
       urls[i] = toURL(jarFileNames.get(i));
      
    }
    return new URLClassLoader(urls, CompilerLoader.class.getClassLoader());
  }

  private static URL toURL(String jarFileName) throws FileNotFoundException {
    File jarFile = new File(jarFileName);
    if (!jarFile.exists()) {
      throw new FileNotFoundException("JAR file not found: " + jarFile.getAbsolutePath());
    }
    try {
      return jarFile.toURI().toURL();
    } catch (MalformedURLException e) {
      // should not happen:
      throw new IllegalStateException(e);

    }
  }

}
