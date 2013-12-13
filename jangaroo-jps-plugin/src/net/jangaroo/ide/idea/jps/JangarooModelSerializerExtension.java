package net.jangaroo.ide.idea.jps;

import com.intellij.facet.FacetManagerImpl;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.serialization.JDomSerializationUtil;
import org.jetbrains.jps.model.serialization.JpsModelSerializerExtension;
import org.jetbrains.jps.model.serialization.facet.JpsFacetSerializer;

/**
 * JPS extension to extract Jangaroo Facet configuration into the corresponding JpsModule.
 */
public class JangarooModelSerializerExtension extends JpsModelSerializerExtension {

  public static final String JANGAROO_STRING_ID = "jangaroo";

  @Override
  public void loadModuleOptions(@NotNull JpsModule jpsModule, @NotNull Element rootTag) {
    // find configuration element of Jangaroo Facet:
    Element result = findJangarooFacetConfiguration(rootTag);
    if (result != null) {
      JoocConfigurationBean configuration = XmlSerializer.deserialize(result, JoocConfigurationBean.class);
      if (configuration == null) {
        configuration = new JoocConfigurationBean();
      }
      jpsModule.getContainer().setChild(JoocConfigurationBean.ROLE, configuration);
    }
  }

  private Element findJangarooFacetConfiguration(Element rootTag) {
    Element facetManagerElement = JDomSerializationUtil.findComponent(rootTag, FacetManagerImpl.COMPONENT_NAME);
    for (Element facet : JDOMUtil.getChildren(facetManagerElement, JpsFacetSerializer.FACET_TAG)) {
      if (JANGAROO_STRING_ID.equals(facet.getAttributeValue(JpsFacetSerializer.TYPE_ATTRIBUTE))) {
        return facet.getChild(JpsFacetSerializer.CONFIGURATION_TAG);
      }
    }
    return null;
  }

}
