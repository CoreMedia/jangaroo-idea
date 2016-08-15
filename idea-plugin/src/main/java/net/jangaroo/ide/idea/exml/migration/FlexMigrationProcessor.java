package net.jangaroo.ide.idea.exml.migration;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.intellij.notification.Notification;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.libraries.NewLibraryConfiguration;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.ExistingLibraryEditor;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.NewLibraryEditor;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMigration;
import com.intellij.psi.impl.migration.PsiMigrationManager;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.utils.library.RepositoryAttachHandler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Pattern;

import static com.intellij.notification.NotificationType.ERROR;

class FlexMigrationProcessor extends SequentialRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance(FlexMigrationProcessor.class);

  private static final Pattern EXT3_LIBRARY_PATTERN = Pattern.compile("Maven: net.jangaroo:ext-as:2.0.[1-9][0-9]*(-joo)?");
  private static final String EXT6_LIBRARY = "net.jangaroo:ext-as:6.0.1-41";
  private static final String MIGRATION_MAP = "ext-as-3.4-migration-map.properties";
  private static final String REFACTORING_NAME = RefactoringBundle.message("migration.title");

  private List<Library> extLibraries = ImmutableList.of();
  private NewLibraryConfiguration ext6LibraryConfig;
  private SortedMap<String, MigrationMapEntry> migrationMap = new TreeMap<String, MigrationMapEntry>();
  private Map<Library, ListMultimap<OrderRootType, String>> originalRoots = ImmutableMap.of();

  public FlexMigrationProcessor(Project project, boolean migrateApi, boolean migrateConfigClasses,
                                boolean migrateProperties) {
    super(project);
    initialize(migrateApi, migrateConfigClasses, migrateProperties);
  }

  private void initialize(boolean migrateApi, boolean migrateConfigClasses, boolean migrateProperties) {
    VirtualFile migrationMapFile = null;

    if (migrateApi || migrateConfigClasses) {
      extLibraries = getLibraries(EXT3_LIBRARY_PATTERN);
      if (extLibraries.isEmpty()) {
        return;
      }

      ext6LibraryConfig = RepositoryAttachHandler.resolveAndDownload(myProject, EXT6_LIBRARY, false, false, null, null);
      if (ext6LibraryConfig == null) {
        error("Required library not found", "The library '" + EXT6_LIBRARY + "' not found in Maven repositories.");
        return;
      }

      migrationMapFile = getMigrationMapFileFromLibrary(ext6LibraryConfig);
    }

    migrationMap = FlexMigrationMapLoader.load(myProject, migrationMapFile, migrateApi, migrateConfigClasses,
      migrateProperties);
  }

  private List<Library> getLibraries(Pattern namePattern) {
    LibraryTable libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(myProject);
    List<Library> libraries = new ArrayList<Library>();
    for (Library library : libraryTable.getLibraries()) {
      String libraryName = library.getName();
      if (libraryName != null && namePattern.matcher(libraryName).matches()) {
        libraries.add(library);
      }
    }
    if (libraries.isEmpty()) {
      error("Required library not found",
        "Library that matches pattern '" + namePattern + "' must be added to the project.");
    } else {
      LOG.info("Libraries '" + namePattern + "' found (" + libraries.size() + "): " + libraries);
    }
    return libraries;
  }

  private static VirtualFile getMigrationMapFileFromLibrary(NewLibraryConfiguration libraryConfig) {
    NewLibraryEditor editor = new NewLibraryEditor(libraryConfig.getLibraryType(), libraryConfig.getProperties());
    libraryConfig.addRoots(editor);
    List<VirtualFile> roots = Arrays.asList(editor.getFiles(OrderRootType.CLASSES));
    for (VirtualFile root : roots) {
      String path = root.getPath();
      if (MIGRATION_MAP.equals(path)) {
        return root;
      }
      if ("jar".equals(root.getExtension())) {
        if (!path.endsWith(URLUtil.JAR_SEPARATOR)) {
          path += URLUtil.JAR_SEPARATOR;
        }
        VirtualFile result = JarFileSystem.getInstance().findLocalVirtualFileByPath(path + MIGRATION_MAP);
        if (result != null) {
          return result;
        }
      }
    }
    error("Migration map file not found",
      "Ext AS migration map file '" + MIGRATION_MAP + "' not found in " + EXT6_LIBRARY);
    return null;
  }

  private static void error(String title, String text) {
    Notifications.Bus.notify(new Notification("jangaroo", title, text, ERROR));
  }

  @NotNull
  protected UsageViewDescriptor createUsageViewDescriptor(@NotNull UsageInfo[] usages) {
    return new FlexMigrationUsagesViewDescriptor();
  }

  @NotNull
  protected UsageInfo[] findUsages() {
    if (migrationMap.isEmpty()) {
      return UsageInfo.EMPTY_ARRAY;
    }

    PsiMigration migration = PsiMigrationManager.getInstance(myProject).startMigration();
    try {
      ArrayList<MigrationUsageInfo> allUsages = new ArrayList<MigrationUsageInfo>();
      for (MigrationMapEntry entry : migrationMap.values()) {
        allUsages.addAll(FlexMigrationUtil.findClassOrMemberUsages(myProject, entry));
      }
      return allUsages.toArray(new UsageInfo[allUsages.size()]);
    } finally {
      migration.finish();
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
  protected void execute(@NotNull final UsageInfo[] usages) {
    if (migrationMap.isEmpty()) {
      return;
    }

    if (!extLibraries.isEmpty() && ext6LibraryConfig != null) {

      // we're going to temporarily modify the Ext AS libraries so that they point to Ext 6 instead of Ext 3.4

      // remember the original Ext 3.4 files so that we can restore the library after migration in #endRefactoring
      originalRoots = new HashMap<Library, ListMultimap<OrderRootType, String>>();
      for (Library extLibrary : extLibraries) {
        ListMultimap<OrderRootType, String> roots = ArrayListMultimap.create();
        for (OrderRootType orderRootType : OrderRootType.getAllTypes()) {
          roots.putAll(orderRootType, Arrays.asList(extLibrary.getUrls(orderRootType)));
        }
        originalRoots.put(extLibrary, roots);
      }

      // replace Ext 3.4 files with Ext 6 files
      for (Library extLibrary : extLibraries) {
        final ExistingLibraryEditor libraryEditor = new ExistingLibraryEditor(extLibrary, null);
        libraryEditor.removeAllRoots();
        ext6LibraryConfig.addRoots(libraryEditor);
        DumbService.getInstance(myProject).runWhenSmart(
          new Runnable() {
            @Override
            public void run() {
              ApplicationManager.getApplication().runWriteAction(new Runnable() {
                public void run() {
                  libraryEditor.commit();
                }
              });
            }
          }
        );
        LOG.info("Replaced library roots of " + extLibrary);
      }
    }

    // execute the actual migration as soon as Idea has completed "Indexing" after library change
    DumbService.getInstance(myProject).runWhenSmart(
      new Runnable() {
        @Override
        public void run() {
          FlexMigrationProcessor.super.execute(usages);
        }
      }
    );
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
    // migrate import usages first, hoping this avoids unnecessary fully-qualified names
    Collection<UsageInfo> usages1 = usagesInFile.getUsages();
    ArrayList<MigrationUsageInfo> usages = Lists.newArrayList(Iterables.filter(usages1, MigrationUsageInfo.class));
    Collections.sort(usages, ImportUsageFirstComparator.INSTANCE);

    for (MigrationMapEntry entry : migrationMap.values()) {
      FlexMigrationUtil.doClassMigration(myProject, entry, usages);
    }
  }

  @Override
  protected void endRefactoring() {
    // restore the original files of the Ext AS library
    for (Map.Entry<Library, ListMultimap<OrderRootType, String>> entry : originalRoots.entrySet()) {
      Library library = entry.getKey();
      ListMultimap<OrderRootType, String> roots = entry.getValue();
      final ExistingLibraryEditor editor = new ExistingLibraryEditor(library, null);
      editor.removeAllRoots();
      for (Map.Entry<OrderRootType, String> root : roots.entries()) {
        editor.addRoot(root.getValue(), root.getKey());
      }
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          editor.commit();
        }
      });
      LOG.info("Restored library roots of " + library + " with " + roots);
    }
  }


  protected String getCommandName() {
    return REFACTORING_NAME;
  }

}
