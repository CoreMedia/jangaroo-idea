package net.jangaroo.ide.idea.util;

import net.jangaroo.exml.api.Exmlc;
import net.jangaroo.jooc.api.Jooc;
import net.jangaroo.properties.api.Propc;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.utils.MavenArtifactUtil;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.Map;

import static net.jangaroo.ide.idea.JangarooFacetImporter.*;

/**
 * Load Jangaroo compilers of a specified version, using a custom class loader.
 */
public class CompilerLoader {

  private static final Map<MavenId,ClassLoader> CLASS_LOADER_BY_ARTIFACT_CACHE = new HashMap<MavenId, ClassLoader>();

  public static Jooc loadJooc(String version) throws FileNotFoundException, ClassNotFoundException, InstantiationException, IllegalAccessException {
    ClassLoader jooClassLoader = getJoocClassLoader(version);
    Class<?> joocClass = jooClassLoader.loadClass("net.jangaroo.jooc.Jooc");
    return (Jooc)joocClass.newInstance();
  }

  public static Exmlc loadExmlc(String version) throws FileNotFoundException, ClassNotFoundException, InstantiationException, IllegalAccessException {
    ClassLoader jooClassLoader = getClassLoaderForArtifact(JANGAROO_GROUP_ID, "exml-compiler", version, getJoocClassLoader(version));
    Class<?> exmlcClass = jooClassLoader.loadClass("net.jangaroo.exml.compiler.Exmlc");
    return (Exmlc)exmlcClass.newInstance();
  }

  public static Propc loadPropc(String version) throws FileNotFoundException, ClassNotFoundException, InstantiationException, IllegalAccessException {
    ClassLoader jooClassLoader = getClassLoaderForArtifact(JANGAROO_GROUP_ID, "properties-compiler", version, CompilerLoader.class.getClassLoader());
    Class<?> propcClass = jooClassLoader.loadClass("net.jangaroo.properties.PropertyClassGenerator");
    return (Propc)propcClass.newInstance();
  }

  private static ClassLoader getJoocClassLoader(String version) throws FileNotFoundException {
    return getClassLoaderForArtifact(JANGAROO_GROUP_ID, "jangaroo-compiler", version, CompilerLoader.class.getClassLoader());
  }

  private synchronized static ClassLoader getClassLoaderForArtifact(String groupId, String artifactId, String version, ClassLoader parentClassLoader) throws FileNotFoundException {
    MavenId mavenId = new MavenId(groupId, artifactId, version);
    ClassLoader classLoader = CLASS_LOADER_BY_ARTIFACT_CACHE.get(mavenId);
    if (classLoader == null) {
      classLoader = createClassLoaderForArtifact(mavenId, parentClassLoader);
      CLASS_LOADER_BY_ARTIFACT_CACHE.put(mavenId, classLoader);
    }
    return classLoader;
  }

  private static ClassLoader createClassLoaderForArtifact(MavenId mavenId, ClassLoader parentClassLoader) throws FileNotFoundException {
    //File localRepository = new File(System.getenv("M2_REPO"));
    return new URLClassLoader(new URL[]{
      artifact(mavenId)
    }, parentClassLoader);
  }

  private static URL artifact(MavenId mavenId) throws FileNotFoundException {
    File localRepository = MavenUtil.resolveLocalRepository(null, null, null);
    try {
      @SuppressWarnings({"ConstantConditions" })
      File jarFile = MavenArtifactUtil.getArtifactFile(localRepository, mavenId, "jar");
      if (!jarFile.exists()) {
        throw new FileNotFoundException("JAR for artifact " + mavenId + " not found at " + jarFile.getAbsolutePath());
      }
      return jarFile.toURI().toURL();
    } catch (MalformedURLException e) {
      // should not happen:
      throw new IllegalStateException(e);
    }
  }
}
