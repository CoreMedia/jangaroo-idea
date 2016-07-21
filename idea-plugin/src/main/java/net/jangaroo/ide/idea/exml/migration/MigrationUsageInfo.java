package net.jangaroo.ide.idea.exml.migration;

import com.google.common.base.Preconditions;
import com.intellij.lang.javascript.psi.ecmal4.JSClass;
import com.intellij.lang.javascript.psi.resolve.JSResolveUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.usageView.UsageInfo;

class MigrationUsageInfo extends UsageInfo {

  private final MigrationMapEntry entry;
  private final boolean isInExtComponentClass;

  MigrationUsageInfo(PsiReference reference, MigrationMapEntry entry) {
    super(reference);
    this.entry = Preconditions.checkNotNull(entry);
    this.isInExtComponentClass = isComponentClass(JSResolveUtil.getClassOfContext(reference.getElement()));
  }

  MigrationUsageInfo(PsiElement element, MigrationMapEntry entry) {
    super(element);
    this.entry = Preconditions.checkNotNull(entry);
    this.isInExtComponentClass = isComponentClass(JSResolveUtil.getClassOfContext(element));
  }

  boolean isInExtComponentClass() {
    return isInExtComponentClass;
  }

  MigrationMapEntry getEntry() {
    return entry;
  }

  private static boolean isComponentClass(JSClass jsClass) {
    if (jsClass == null) {
      return false;
    }
    if ("ext.Component".equals(jsClass.getQualifiedName())) {
      return true;
    }
    for (JSClass superClass : jsClass.getSuperClasses()) {
      if (isComponentClass(superClass)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    MigrationUsageInfo that = (MigrationUsageInfo)o;
    return isInExtComponentClass == that.isInExtComponentClass && entry.equals(that.entry);

  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + entry.hashCode();
    result = 31 * result + (isInExtComponentClass ? 1 : 0);
    return result;
  }

}
