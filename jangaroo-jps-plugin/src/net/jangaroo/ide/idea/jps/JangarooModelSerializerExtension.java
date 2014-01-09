package net.jangaroo.ide.idea.jps;

import com.intellij.facet.FacetManagerImpl;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.util.xmlb.XmlSerializer;
import net.jangaroo.ide.idea.jps.exml.ExmlcConfigurationBean;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.serialization.JDomSerializationUtil;
import org.jetbrains.jps.model.serialization.JpsModelSerializerExtension;
import org.jetbrains.jps.model.serialization.artifact.JpsPackagingElementSerializer;
import org.jetbrains.jps.model.serialization.facet.JpsFacetSerializer;
import org.jetbrains.jps.model.serialization.library.JpsSdkPropertiesSerializer;

import java.util.Collections;
import java.util.List;

/**
 * JPS extension to extract Jangaroo Facet configuration into the corresponding JpsModule.
 */
public class JangarooModelSerializerExtension extends JpsModelSerializerExtension {

  public static final String JANGAROO_FACET_ID = "jangaroo";
  public static final String EXML_FACET_ID = "exml";
  private static final JpsElementChildRole<JoocConfigurationBean> JOOC_CONFIG = JpsElementChildRoleBase.create("Jangaroo Compiler Configuration");
  private static final JpsElementChildRole<ExmlcConfigurationBean> EXMLC_CONFIG = JpsElementChildRoleBase.create("EXML Compiler Configuration");

  @NotNull
  public static JoocConfigurationBean getJoocSettings(@NotNull JpsModule module) {
    JoocConfigurationBean settings = module.getContainer().getChild(JOOC_CONFIG);
    return settings == null ? new JoocConfigurationBean() : settings;
  }

  @NotNull
  public static ExmlcConfigurationBean getExmlcSettings(@NotNull JpsModule module) {
    ExmlcConfigurationBean settings = module.getContainer().getChild(EXMLC_CONFIG);
    return settings == null ? new ExmlcConfigurationBean() : settings;
  }

  @NotNull
  @Override
  public List<? extends JpsPackagingElementSerializer<?>> getPackagingElementSerializers() {
    return Collections.singletonList(new JpsResourcesModuleOutputPackagingElementSerializer());
  }

  @NotNull
  @Override
  public List<? extends JpsSdkPropertiesSerializer<?>> getSdkPropertiesSerializers() {
    return Collections.singletonList(new JpsJangarooSdkPropertiesSerializer());
  }

  @Override
  public void loadModuleOptions(@NotNull JpsModule jpsModule, @NotNull Element rootTag) {
    // find configuration element of Jangaroo Facet:
    Element jangarooElement = findFacetConfiguration(rootTag, JANGAROO_FACET_ID);
    if (jangarooElement != null) {
      JoocConfigurationBean configuration = XmlSerializer.deserialize(jangarooElement, JoocConfigurationBean.class);
      if (configuration == null) {
        configuration = new JoocConfigurationBean();
      }
      jpsModule.getContainer().setChild(JOOC_CONFIG, configuration);
    }
    // find configuration element of EXML Facet:
    Element exmlElement = findFacetConfiguration(rootTag, EXML_FACET_ID);
    if (exmlElement != null) {
      ExmlcConfigurationBean configuration = XmlSerializer.deserialize(exmlElement, ExmlcConfigurationBean.class);
      if (configuration == null) {
        configuration = new ExmlcConfigurationBean();
      }
      jpsModule.getContainer().setChild(EXMLC_CONFIG, configuration);
    }
  }

  private Element findFacetConfiguration(Element rootTag, String facetId) {
    Element facetManagerElement = JDomSerializationUtil.findComponent(rootTag, FacetManagerImpl.COMPONENT_NAME);
    for (Element facet : JDOMUtil.getChildren(facetManagerElement, JpsFacetSerializer.FACET_TAG)) {
      if (facetId.equals(facet.getAttributeValue(JpsFacetSerializer.TYPE_ATTRIBUTE))) {
        return facet.getChild(JpsFacetSerializer.CONFIGURATION_TAG);
      }
    }
    return null;
  }

}
