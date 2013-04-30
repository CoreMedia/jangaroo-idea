package net.jangaroo.ide.idea;

import org.jetbrains.idea.maven.project.MavenProject;

import java.util.Collection;
import java.util.List;

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

  public void collectSourceFolders(MavenProject mavenProject, List<String> result) {
    super.collectSourceFolders(mavenProject, result);
    result.add("src/main/joo-api"); // must be a source folder in IDEA for references to API-only classes to work
  }

}
