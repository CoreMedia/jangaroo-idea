package net.jangaroo.ide.idea.exml;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.lang.properties.PropertiesFileType;
import com.intellij.navigation.ChooseByNameContributor;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.CommonProcessors;
import com.intellij.util.indexing.FindSymbolParameters;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Configure EXML files to appear in "Goto classes".
 */
public class GotoExmlClassContributor implements ChooseByNameContributor {

  @NotNull
  @Override
  public String[] getNames(Project project, boolean includeNonProjectItems) {
    ProjectFileIndex projectFileIndex = ProjectFileIndex.SERVICE.getInstance(project);
    Set<String> names = new HashSet<String>();
    Collection<VirtualFile> xmlFiles = FileTypeIndex.getFiles(XmlFileType.INSTANCE, GlobalSearchScope.projectScope(project));
    for (VirtualFile file : xmlFiles) {
      if ("exml".equals(file.getExtension()) && projectFileIndex.isInSourceContent(file)) {
        names.add(file.getName());
      }
    }
    return names.toArray(new String[names.size()]);
  }

  @NotNull
  @Override
  public NavigationItem[] getItemsByName(String name, String pattern, Project project, boolean includeNonProjectItems) {
    CommonProcessors.CollectProcessor<NavigationItem> processor = new CommonProcessors.CollectProcessor<NavigationItem>();
    FindSymbolParameters parameters = FindSymbolParameters.wrap(pattern, project, includeNonProjectItems);
    FilenameIndex.processFilesByName(
      name, false, processor, parameters.getSearchScope(), parameters.getProject(), parameters.getIdFilter()
    );
    return processor.toArray(new NavigationItem[processor.getResults().size()]);
  }
}
