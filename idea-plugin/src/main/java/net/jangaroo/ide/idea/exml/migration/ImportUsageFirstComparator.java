package net.jangaroo.ide.idea.exml.migration;

import com.intellij.usageView.UsageInfo;

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
    boolean isImport1 = FlexMigrationUtil.isImport(o1);
    boolean isImport2 = FlexMigrationUtil.isImport(o2);
    return isImport1 == isImport2 ? 0 : isImport1 ? -1 : 1;
  }

}
