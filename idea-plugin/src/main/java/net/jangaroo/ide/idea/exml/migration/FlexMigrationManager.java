package net.jangaroo.ide.idea.exml.migration;

import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.refactoring.migration.MigrationDialog;
import com.intellij.refactoring.migration.MigrationMap;
import com.intellij.refactoring.migration.MigrationMapSet;

/**
 * Store one FlexMigrationManager per project.
 */
public class FlexMigrationManager extends AbstractProjectComponent {

  private final MigrationMapSet myMigrationMapSet = new MigrationMapSet();

  public static FlexMigrationManager getInstance(Project project) {
    return project.getComponent(FlexMigrationManager.class);
  }

  public FlexMigrationManager(Project project) {
    super(project);
  }

  public void showMigrationDialog() {
    final MigrationDialog migrationDialog = new MigrationDialog(myProject, myMigrationMapSet);
    migrationDialog.show();
    if (!migrationDialog.isOK()) {
      return;
    }
    MigrationMap migrationMap = migrationDialog.getMigrationMap();
    if (migrationMap == null) return;

    new FlexMigrationProcessor(myProject, migrationMap).run();
  }
}
