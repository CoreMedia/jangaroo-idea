package net.jangaroo.ide.idea.jps;

import org.jdom.Element;
import org.jetbrains.jps.model.module.JpsModuleReference;
import org.jetbrains.jps.model.serialization.artifact.JpsPackagingElementSerializer;
import org.jetbrains.jps.model.serialization.facet.JpsFacetSerializer;

/**
 * Deserialize a JangarooPackagingOutputElement to a JpsJangarooPackagingOutputElement.
 */
public class JpsResourcesModuleOutputPackagingElementSerializer extends JpsPackagingElementSerializer<JpsResourcesModuleOutputPackagingElement> {
  public JpsResourcesModuleOutputPackagingElementSerializer() {
    super("jangaroo-compiler-output", JpsResourcesModuleOutputPackagingElement.class);
  }

  @Override
  public JpsResourcesModuleOutputPackagingElement load(Element element) {
    JpsModuleReference reference = JpsFacetSerializer.createModuleReference(element.getChild("option").getAttributeValue("value"));
    return new JpsResourcesModuleOutputPackagingElementImpl(reference);
  }

  @Override
  public void save(JpsResourcesModuleOutputPackagingElement element, Element tag) {
    // not yet used
    throw new IllegalStateException("not implemented");
  }
}
