package net.jangaroo.ide.idea.exml.migration;

import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.migration.MigrationMap;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usageView.UsageViewDescriptor;
import org.jetbrains.annotations.NotNull;

class FlexMigrationUsagesViewDescriptor implements UsageViewDescriptor {

  @NotNull
  public PsiElement[] getElements() {
    return PsiElement.EMPTY_ARRAY;
  }

  public String getProcessedElementsHeader() {
    return null;
  }

  public String getCodeReferencesText(int usagesCount, int filesCount) {
    return RefactoringBundle.message("references.in.code.to.elements.from.migration.map", "Ext AS",
                                     UsageViewBundle.getReferencesString(usagesCount, filesCount));
  }

  public String getCommentReferencesText(int usagesCount, int filesCount) {
    return null;
  }

  public String getInfo() {
    return RefactoringBundle.message("press.the.do.migrate.button", "Ext AS");
  }

}
