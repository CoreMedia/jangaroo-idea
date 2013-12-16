package net.jangaroo.ide.idea.jps;

import com.intellij.openapi.util.JDOMUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElementFactory;
import org.jetbrains.jps.model.JpsSimpleElement;
import org.jetbrains.jps.model.serialization.library.JpsSdkPropertiesSerializer;

import java.util.ArrayList;
import java.util.List;

/**
 * Serialize Jangaroo SDKs for JPS.
 */
public class JangarooSdkPropertiesSerializer extends JpsSdkPropertiesSerializer<JpsSimpleElement<JpsJangarooSdkProperties>> {

  public static final String JANGAROO_SDK_TYPE_ID = "Jangaroo SDK";

  public JangarooSdkPropertiesSerializer() {
    super(JANGAROO_SDK_TYPE_ID, JpsJangarooSdkType.INSTANCE);
  }

  @NotNull
  @Override
  public JpsSimpleElement<JpsJangarooSdkProperties> loadProperties(@Nullable Element propertiesElement) {
    List<Element> jarPathElements = JDOMUtil.getChildren(propertiesElement, "option");
    ArrayList<String> jarPaths = new ArrayList<String>();
    for (Element jarPathElement : jarPathElements) {
      jarPaths.add(jarPathElement.getAttributeValue("value"));
    }
    return JpsElementFactory.getInstance().createSimpleElement(new JpsJangarooSdkProperties(jarPaths));
  }

  @Override
  public void saveProperties(@NotNull JpsSimpleElement<JpsJangarooSdkProperties> properties, @NotNull Element element) {
    System.out.println("gotcha!");
    // no need to implement until IDEA is updated to use JPS as well!
  }
}
