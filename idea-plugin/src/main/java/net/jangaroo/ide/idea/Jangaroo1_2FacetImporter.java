package net.jangaroo.ide.idea;

import com.intellij.util.PairConsumer;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.util.Collection;

/**
 * A Facet-from-Maven Importer for the Jangaroo Facet type.
 */
public class Jangaroo1_2FacetImporter extends JangarooFacetImporter {

  @Override
  protected boolean isApplicableVersion(int majorVersion) {
    return 0 <= majorVersion && majorVersion <= 2;
  }

  @Override
  public void getSupportedPackagings(Collection<String> result) {
    super.getSupportedPackagings(result);
    result.add("war");
  }

  @Override
  public void collectSourceRoots(MavenProject mavenProject, PairConsumer<String, JpsModuleSourceRootType<?>> result) {
    super.collectSourceRoots(mavenProject, result);
    result.consume("src/main/joo-api", JavaSourceRootType.SOURCE); // must be a source folder in IDEA for references to API-only classes to work
  }

}
