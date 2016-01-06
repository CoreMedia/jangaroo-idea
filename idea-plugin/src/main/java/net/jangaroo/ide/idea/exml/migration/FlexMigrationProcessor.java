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
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.RefactoringHelper;
import com.intellij.refactoring.migration.MigrationMap;
import com.intellij.refactoring.migration.MigrationMapEntry;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

class FlexMigrationProcessor extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance("#net.jangaroo.ide.idea.exml.migration.FlexMigrationProcessor");

  private static final String EXT3_LIBRARY = "Maven: net.jangaroo:ext-as:2.1.0-SNAPSHOT";
  private static final String EXT6_LIBRARY = "Maven: net.jangaroo:ext-as:6.0.1-1-SNAPSHOT";
  private static final String MIGRATION_MAP = "ext-as-3.4-migration-map.properties";

  private MigrationMap migrationMap;
  private static final String REFACTORING_NAME = RefactoringBundle.message("migration.title");
  private PsiMigration myPsiMigration;

  private GlobalSearchScope ext3SearchScope;
  private GlobalSearchScope ext6SearchScope;

  public FlexMigrationProcessor(Project project) {
    super(project);
    myPsiMigration = startMigration(project);
  }

  @NotNull
  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages) {
    return new FlexMigrationUsagesViewDescriptor();
  }

  private PsiMigration startMigration(Project project) {
    ext3SearchScope = loadLibraryScope(project, EXT3_LIBRARY);
    ext6SearchScope = loadLibraryScope(project, EXT6_LIBRARY);
    if (ext6SearchScope != null) {
      migrationMap = FlexMigrationMapLoader.loadMigrationMap(myProject, ext6SearchScope, MIGRATION_MAP);
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
      if (migrationMap == null || ext3SearchScope == null) {
        return UsageInfo.EMPTY_ARRAY;
      }
      ArrayList<UsageInfo> usagesVector = new ArrayList<UsageInfo>();
      for (int i = 0; i < migrationMap.getEntryCount(); i++) {
        MigrationMapEntry entry = migrationMap.getEntryAt(i);
        UsageInfo[] usages = FlexMigrationUtil.findClassOrMemberUsages(myProject, ext3SearchScope, entry.getOldName());

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
    GlobalSearchScope searchScope = ext6SearchScope;
    if (searchScope == null) {
      return;
    }

    final PsiMigration psiMigration = startMigration(myProject);
    LocalHistoryAction a = LocalHistory.getInstance().startAction(getCommandName());

    try {
      for (int i = 0; i < migrationMap.getEntryCount(); i++) {
        MigrationMapEntry entry = migrationMap.getEntryAt(i);
        if (entry.getType() == MigrationMapEntry.CLASS) {
          FlexMigrationUtil.doClassMigration(myProject, searchScope, entry, usages);
        }
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
    public MigrationMapEntry mapEntry;

    public MigrationUsageInfo(UsageInfo info, MigrationMapEntry mapEntry) {
      super(info.getElement(), info.getRangeInElement().getStartOffset(), info.getRangeInElement().getEndOffset());
      this.mapEntry = mapEntry;
    }
  }
}
