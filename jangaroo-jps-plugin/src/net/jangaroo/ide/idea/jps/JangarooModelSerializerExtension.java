package net.jangaroo.ide.idea.jps;

import com.intellij.openapi.util.JDOMUtil;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
  private static final JpsElementChildRole<JoocConfigurationBean> JOOC_CONFIG = JpsElementChildRoleBase.create("Jangaroo Compiler Configuration");
  // Constant defined in com.intellij.facet.FacetManagerImpl, but we don't want the dependency:
  private static final String FACET_MANAGER_COMPONENT_NAME = "FacetManager";

  @Nullable
  public static JoocConfigurationBean getJoocSettings(@NotNull JpsModule module) {
    return module.getContainer().getChild(JOOC_CONFIG);
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
  }

  private Element findFacetConfiguration(Element rootTag, String facetId) {
    Element facetManagerElement = JDomSerializationUtil.findComponent(rootTag, FACET_MANAGER_COMPONENT_NAME);
    for (Element facet : JDOMUtil.getChildren(facetManagerElement, JpsFacetSerializer.FACET_TAG)) {
      if (facetId.equals(facet.getAttributeValue(JpsFacetSerializer.TYPE_ATTRIBUTE))) {
        return facet.getChild(JpsFacetSerializer.CONFIGURATION_TAG);
      }
    }
    return null;
  }

}
