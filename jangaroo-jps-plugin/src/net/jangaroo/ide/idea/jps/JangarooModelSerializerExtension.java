package net.jangaroo.ide.idea.jps;

import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.serialization.JpsModelSerializerExtension;

/**
 * Created with IntelliJ IDEA. User: fwienber Date: 12.12.13 Time: 23:10 To change this template use File | Settings |
 * File Templates.
 */
public class JangarooModelSerializerExtension extends JpsModelSerializerExtension {

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
    for (Element component : rootTag.getChildren("component")) {
      if ("FacetManager".equals(component.getAttributeValue("name"))) {
        for (Element facet : component.getChildren("facet")) {
          if ("jangaroo".equals(facet.getAttributeValue("type"))) {
            return facet.getChild("configuration");
          }
        }
      }
    }
    return null;
  }

}
