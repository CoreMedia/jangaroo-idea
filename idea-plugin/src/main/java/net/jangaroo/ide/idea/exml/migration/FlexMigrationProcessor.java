package net.jangaroo.ide.idea.exml.migration;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.impl.scopes.LibraryScope;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMigration;
import com.intellij.psi.impl.migration.PsiMigrationManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.SortedMap;

class FlexMigrationProcessor extends SequentialRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance(FlexMigrationProcessor.class);

  private static final String EXT3_LIBRARY = "Maven: net.jangaroo:ext-as:2.0.15";
  private static final String EXT6_LIBRARY = "Maven: net.jangaroo:ext-as:6.0.1-SNAPSHOT";
  private static final String MIGRATION_MAP = "ext-as-3.4-migration-map.properties";

  private SortedMap<String, MigrationMapEntry> migrationMap;
  private static final String REFACTORING_NAME = RefactoringBundle.message("migration.title");
  private PsiMigration myPsiMigration;

  private GlobalSearchScope projectExt3Scope;
  private GlobalSearchScope projectExt6Scope;

  public FlexMigrationProcessor(Project project) {
    super(project);
    myPsiMigration = startMigration(project);
  }

  @NotNull
  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages) {
    return new FlexMigrationUsagesViewDescriptor();
  }

  private PsiMigration startMigration(Project project) {
    GlobalSearchScope ext3Scope = loadLibraryScope(project, EXT3_LIBRARY);
    GlobalSearchScope ext6Scope = loadLibraryScope(project, EXT6_LIBRARY);
    if (ext3Scope != null && ext6Scope != null) {
      GlobalSearchScope projectScope = ProjectScope.getProjectScope(project);
      projectExt3Scope = ext3Scope.uniteWith(projectScope);
      migrationMap = FlexMigrationMapLoader.loadMigrationMap(projectExt3Scope, ext6Scope, MIGRATION_MAP);
      projectExt6Scope = ext6Scope.union(projectScope);
    }
    return PsiMigrationManager.getInstance(project).startMigration();
  }

  private static GlobalSearchScope loadLibraryScope(@NotNull Project project, @NotNull String libraryName) {
    LibraryTable libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project);
    Library library = libraryTable.getLibraryByName(libraryName);
    if (library == null) {
      Notifications.Bus.notify(new Notification("jangaroo", "Required project library not found",
        String.format("The project library '%s' must be added to the project.", libraryName),
        NotificationType.ERROR));
      return null;
    }
    LOG.info("Library '" + libraryName + "' found: " + library);
    return new LibraryScope(project, library);
  }

  @Override
  protected void refreshElements(@NotNull PsiElement[] elements) {
    myPsiMigration = startMigration(myProject);
  }

  @NotNull
  protected UsageInfo[] findUsages() {
    try {
      if (migrationMap == null || projectExt3Scope == null) {
        return UsageInfo.EMPTY_ARRAY;
      }
      ArrayList<UsageInfo> usagesVector = new ArrayList<UsageInfo>();
      for (MigrationMapEntry entry : migrationMap.values()) {
        UsageInfo[] usages = FlexMigrationUtil.findClassOrMemberUsages(myProject, projectExt3Scope, entry.getOldName());

        for (UsageInfo usage : usages) {
          usagesVector.add(new MigrationUsageInfo(usage, entry));
        }
      }
      return usagesVector.toArray(new UsageInfo[usagesVector.size()]);
    } finally {
      myPsiMigration.finish();
      myPsiMigration = null;
    }
  }

  protected boolean preprocessUsages(@NotNull Ref<UsageInfo[]> refUsages) {
    if (refUsages.get().length == 0) {
      Messages.showInfoMessage(myProject, RefactoringBundle.message("migration.no.usages.found.in.the.project"), REFACTORING_NAME);
      return false;
    }
    setPreviewUsages(true);
    return super.preprocessUsages(refUsages);
  }


  @Override
  protected void startRefactoring(UsageInfo[] usageInfos) {
    myPsiMigration = startMigration(myProject);
  }

  @Override
  protected Comparator<? super UsagesInFile> getRefactoringIterationComparator() {
    // iterate over files in lexicographical order of their paths,
    // if they have the same path - as it's the case for MXML fragments - refactor fragments with imports first
    return new Comparator<UsagesInFile>() {
      @Override
      public int compare(UsagesInFile o1, UsagesInFile o2) {
        int result = comparePaths(o1.getFile(), o2.getFile());
        if (result != 0) {
          return result;
        }
        int imports1 = countImports(o1.getUsages());
        int imports2 = countImports(o2.getUsages());
        return imports1 > imports2 ? -1 : imports1 == imports2 ? 0 : 1;
      }

      private int comparePaths(PsiFile o1, PsiFile o2) {
        String path1 = o1.getVirtualFile().getCanonicalPath();
        String path2 = o2.getVirtualFile().getCanonicalPath();
        return path1 == null && path2 == null ? 0 : path1 == null ? -1 : path2 == null ? 1 : path1.compareTo(path2);
      }

      private int countImports(Collection<UsageInfo> usages) {
        int result = 0;
        for (UsageInfo usage : usages) {
          if (FlexMigrationUtil.isImport(usage)) {
            ++result;
          }
        }
        return result;
      }
    };
  }

  @Override
  protected void performRefactoringIteration(UsagesInFile usagesInFile) {
    if (projectExt6Scope == null) {
      return;
    }

    // migrate import usages first, hoping this avoids unnecessary fully-qualified names
    ArrayList<UsageInfo> usages = new ArrayList<UsageInfo>(usagesInFile.getUsages());
    Collections.sort(usages, ImportUsageFirstComparator.INSTANCE);

    for (MigrationMapEntry entry : migrationMap.values()) {
      FlexMigrationUtil.doClassMigration(myProject, projectExt6Scope, entry, usages);
    }
  }

  @Override
  protected void endRefactoring() {
    myPsiMigration.finish();
  }

  protected String getCommandName() {
    return REFACTORING_NAME;
  }

  public static class MigrationUsageInfo extends UsageInfo {
    MigrationMapEntry mapEntry;

    public MigrationUsageInfo(UsageInfo info, MigrationMapEntry mapEntry) {
      super(info.getElement(), info.getRangeInElement().getStartOffset(), info.getRangeInElement().getEndOffset());
      this.mapEntry = mapEntry;
    }
  }
}
