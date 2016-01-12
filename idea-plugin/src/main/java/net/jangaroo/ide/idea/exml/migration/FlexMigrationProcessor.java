package net.jangaroo.ide.idea.exml.migration;

import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.impl.scopes.LibraryScope;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMigration;
import com.intellij.psi.impl.migration.PsiMigrationManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.RefactoringHelper;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.SortedMap;

class FlexMigrationProcessor extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance(FlexMigrationProcessor.class);

  private static final String EXT3_LIBRARY = "Maven: net.jangaroo:ext-as:2.1.0-SNAPSHOT";
  private static final String EXT6_LIBRARY = "Maven: net.jangaroo:ext-as:6.0.1-1-SNAPSHOT";
  private static final String MIGRATION_MAP = "ext-as-3.4-migration-map.properties";

  private SortedMap<String, MigrationMapEntry> migrationMap;
  private static final String REFACTORING_NAME = RefactoringBundle.message("migration.title");
  private PsiMigration myPsiMigration;

  private GlobalSearchScope projectExt3Scope;
  private GlobalSearchScope ext6Scope;

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
    ext6Scope = loadLibraryScope(project, EXT6_LIBRARY);
    if (ext3Scope != null && ext6Scope != null) {
      projectExt3Scope = ext3Scope.uniteWith(ProjectScope.getProjectScope(project));
      migrationMap = FlexMigrationMapLoader.loadMigrationMap(projectExt3Scope, ext6Scope, MIGRATION_MAP);
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
    return true;
  }

  protected void performRefactoring(@NotNull UsageInfo[] usages) {
    final PsiMigration psiMigration = startMigration(myProject);
    LocalHistoryAction a = LocalHistory.getInstance().startAction(getCommandName());

    try {
      if (ext6Scope == null) {
        return;
      }
      GlobalSearchScope searchScope = ext6Scope.uniteWith(ProjectScope.getProjectScope(myProject));

      // migrate import usages first in order to avoid unnecessary fully-qualified names
      Arrays.sort(usages, ImportUsageFirstComparator.INSTANCE);

      // todo[ahu]: show progress bar (instead of incremental logging)
      int entryCount = migrationMap.size();
      int currentEntry = 0;
      LOG.info("Starting migration with " + entryCount + " replacement rules");
      for (MigrationMapEntry entry : migrationMap.values()) {
        FlexMigrationUtil.doClassMigration(myProject, searchScope, entry, usages);
        LOG.info("Migrated " + (++currentEntry) + '/' + entryCount + " replacement rules");
      }

      for(RefactoringHelper helper: Extensions.getExtensions(RefactoringHelper.EP_NAME)) {
        Object preparedData = helper.prepareOperation(usages);
        //noinspection unchecked
        helper.performOperation(myProject, preparedData);
      }
    } finally {
      a.finish();
      psiMigration.finish();
    }
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
