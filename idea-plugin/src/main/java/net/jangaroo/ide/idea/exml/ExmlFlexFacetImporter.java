package net.jangaroo.ide.idea.exml;

import com.intellij.lang.javascript.flex.FlexModuleType;
import com.intellij.openapi.module.ModuleType;
import net.jangaroo.ide.idea.JangarooFacetImporter;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Use another module type for importing Jangaroo 3 EXML modules.
 */
public class ExmlFlexFacetImporter extends ExmlFacetImporter {

  @Override
  public void getSupportedPackagings(Collection<String> result) {
    // do *not* call super, this subclass does not support "war" packaging!
    result.add(JangarooFacetImporter.JANGAROO_PACKAGING_TYPE);
  }

  @NotNull
  @Override
  public ModuleType getModuleType() {
    return FlexModuleType.getInstance();
  }

}
