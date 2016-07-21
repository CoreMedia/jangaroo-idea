package net.jangaroo.ide.idea.exml.migration;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;

/**
 * Migrate usages of ActionScript properties classes to ResourceManaer resource bundles.
 */
public class MigrateExtProperties extends AnAction {
  @Override
  public void actionPerformed(AnActionEvent anActionEvent) {
    Project myProject = getEventProject(anActionEvent);
    if (myProject != null) {
      new FlexMigrationProcessor(myProject, false, false, true).run();
    }
  }
}
