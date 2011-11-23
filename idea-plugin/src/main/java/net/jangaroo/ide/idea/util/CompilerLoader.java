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
import java.util.Map;

/**
 * Load Jangaroo compilers of a specified version, using a custom class loader.
 */
public class CompilerLoader {

  private static final Map<String,ClassLoader> CLASS_LOADER_BY_JAR_FILE_NAME_CACHE = new HashMap<String, ClassLoader>();

  public static Jooc loadJooc(String compilerJarFileName) throws FileNotFoundException, ClassNotFoundException, InstantiationException, IllegalAccessException {
    ClassLoader jooClassLoader = getJoocClassLoader(compilerJarFileName);
    Class<?> joocClass = jooClassLoader.loadClass("net.jangaroo.jooc.Jooc");
    return (Jooc)joocClass.newInstance();
  }

  public static Exmlc loadExmlc(String exmlcJarFileName, String joocJarFileName) throws FileNotFoundException, ClassNotFoundException, InstantiationException, IllegalAccessException {
    ClassLoader jooClassLoader = getClassLoaderForJarFile(exmlcJarFileName, getJoocClassLoader(joocJarFileName));
    Class<?> exmlcClass = jooClassLoader.loadClass("net.jangaroo.exml.compiler.Exmlc");
    return (Exmlc)exmlcClass.newInstance();
  }

  public static Propc loadPropc(String propcJarFileName) throws FileNotFoundException, ClassNotFoundException, InstantiationException, IllegalAccessException {
    ClassLoader jooClassLoader = getClassLoaderForJarFile(propcJarFileName, CompilerLoader.class.getClassLoader());
    Class<?> propcClass = jooClassLoader.loadClass("net.jangaroo.properties.PropertyClassGenerator");
    return (Propc)propcClass.newInstance();
  }

  private static ClassLoader getJoocClassLoader(String jarFileName) throws FileNotFoundException {
    return getClassLoaderForJarFile(jarFileName, CompilerLoader.class.getClassLoader());
  }

  private synchronized static ClassLoader getClassLoaderForJarFile(String jarFileName, ClassLoader parentClassLoader) throws FileNotFoundException {
    return jarFileName.endsWith("-SNAPSHOT.jar")
      ? createClassLoaderForArtifact(jarFileName, parentClassLoader)
      : getCachedClassLoaderForArtifact(jarFileName, parentClassLoader);
  }

  private synchronized static ClassLoader getCachedClassLoaderForArtifact(String jarFileName, ClassLoader parentClassLoader) throws FileNotFoundException {
    ClassLoader classLoader = CLASS_LOADER_BY_JAR_FILE_NAME_CACHE.get(jarFileName);
    if (classLoader == null) {
      classLoader = createClassLoaderForArtifact(jarFileName, parentClassLoader);
      CLASS_LOADER_BY_JAR_FILE_NAME_CACHE.put(jarFileName, classLoader);
    }
    return classLoader;
  }

  private static ClassLoader createClassLoaderForArtifact(String jarFileName, ClassLoader parentClassLoader) throws FileNotFoundException {
    File jarFile = new File(jarFileName);
    if (!jarFile.exists()) {
      throw new FileNotFoundException("JAR file not found: " + jarFile.getAbsolutePath());
    }
    try {
      return new URLClassLoader(new URL[]{
        jarFile.toURI().toURL()
      }, parentClassLoader);
    } catch (MalformedURLException e) {
      // should not happen:
      throw new IllegalStateException(e);

    }
  }

}
