package net.jangaroo.ide.idea.exml.migration;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;

/**
 * Invoke migration from Ext AS 3.4 to Ext AS 6.x.
 */
public class MigrateExtJsAction extends AnAction {
  @Override
  public void actionPerformed(AnActionEvent anActionEvent) {
    Project myProject = getEventProject(anActionEvent);
    if (myProject != null) {
      new FlexMigrationProcessor(myProject).run();
    }
  }
}
