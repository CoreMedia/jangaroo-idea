package net.jangaroo.ide.idea.jps;

import org.jetbrains.jps.model.java.JpsProductionModuleOutputPackagingElement;

/**
 * A JpsModuleOutputPackagingElement that copies only the static web resources under META-INF/resources.
 */
public interface JpsResourcesModuleOutputPackagingElement extends JpsProductionModuleOutputPackagingElement {
}
