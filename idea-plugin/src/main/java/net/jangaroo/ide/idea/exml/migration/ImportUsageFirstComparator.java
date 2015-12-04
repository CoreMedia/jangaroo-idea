package net.jangaroo.ide.idea.exml.migration;

import com.intellij.lang.javascript.psi.ecmal4.JSImportStatement;
import com.intellij.psi.PsiElement;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;

/**
 * Compares {@link UsageInfo} with import usages being smaller than other usages.
 */
class ImportUsageFirstComparator implements Comparator<UsageInfo> {

  static final ImportUsageFirstComparator INSTANCE = new ImportUsageFirstComparator();

  private ImportUsageFirstComparator() {
  }

  @Override
  public int compare(UsageInfo o1, UsageInfo o2) {
    boolean isImport1 = isImport(o1.getElement());
    boolean isImport2 = isImport(o2.getElement());
    return isImport1 == isImport2 ? 0 : isImport1 ? -1 : 1;
  }

  private static boolean isImport(@Nullable PsiElement element) {
    return element != null && element.getParent() instanceof JSImportStatement;
  }
}
