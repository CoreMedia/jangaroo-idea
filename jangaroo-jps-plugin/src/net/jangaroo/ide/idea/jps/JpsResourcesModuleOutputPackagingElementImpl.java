package net.jangaroo.ide.idea.jps;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.artifact.impl.elements.JpsModuleOutputPackagingElementBase;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleReference;

/**
 * A JpsModuleOutputPackagingElement that copies only the static web resources under META-INF/resources.
 */
public class JpsResourcesModuleOutputPackagingElementImpl extends JpsModuleOutputPackagingElementBase<JpsResourcesModuleOutputPackagingElementImpl>
  implements JpsResourcesModuleOutputPackagingElement {
  public JpsResourcesModuleOutputPackagingElementImpl(JpsModuleReference moduleReference) {
    super(moduleReference);
  }

  private JpsResourcesModuleOutputPackagingElementImpl(JpsResourcesModuleOutputPackagingElementImpl original) {
    super(original);
  }

  @NotNull
  @Override
  public JpsResourcesModuleOutputPackagingElementImpl createCopy() {
    return new JpsResourcesModuleOutputPackagingElementImpl(this);
  }

  @Override
  protected String getOutputUrl(@NotNull JpsModule module) {
    return JpsJavaExtensionService.getInstance().getOutputUrl(module, false) + "/META-INF/resources";
  }
}
