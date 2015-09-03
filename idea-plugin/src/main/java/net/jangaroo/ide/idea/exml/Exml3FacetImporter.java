package net.jangaroo.ide.idea.exml;

import com.intellij.lang.javascript.flex.FlexModuleType;
import com.intellij.openapi.module.ModuleType;
import org.jetbrains.annotations.NotNull;

/**
 * Use another module type for importing Jangaroo 3 EXML modules.
 */
public class Exml3FacetImporter extends ExmlFacetImporter {

  @Override
  protected boolean isApplicableVersion(int majorVersion) {
    return majorVersion == 3;
  }

  @NotNull
  @Override
  public ModuleType getModuleType() {
    return FlexModuleType.getInstance();
  }

}
