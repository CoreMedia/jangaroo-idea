package net.jangaroo.ide.idea.exml.migration;

import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.refactoring.migration.MigrationMap;

/**
 * Store one FlexMigrationManager per project.
 */
public class FlexMigrationManager extends AbstractProjectComponent {

  private static final String MIGRATION_MAP = "Ext AS 3_4 -_ 6_0.xml";
  private MigrationMap migrationMap;

  public static FlexMigrationManager getInstance(Project project) {
    return project.getComponent(FlexMigrationManager.class);
  }

  public FlexMigrationManager(Project project) {
    super(project);
  }

  @Override
  public void initComponent() {
    migrationMap = FlexMigrationMapLoader.loadMigrationMap(MIGRATION_MAP);
  }

  public void showMigrationDialog() {
    if (migrationMap == null) {
      return;
    }
    new FlexMigrationProcessor(myProject, migrationMap).run();
  }
}
