package net.jangaroo.ide.idea.jps;

import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsDummyElement;
import org.jetbrains.jps.model.JpsElementFactory;
import org.jetbrains.jps.model.serialization.library.JpsSdkPropertiesSerializer;

/**
 * Serialize Jangaroo SDKs for JPS.
 */
public class JpsJangarooSdkPropertiesSerializer extends JpsSdkPropertiesSerializer<JpsDummyElement> {

  JpsJangarooSdkPropertiesSerializer() {
    super(JpsJangarooSdkType.JANGAROO_SDK_TYPE_ID, JpsJangarooSdkType.INSTANCE);
  }

  @NotNull
  @Override
  public JpsDummyElement loadProperties(@Nullable Element propertiesElement) {
    return JpsElementFactory.getInstance().createDummyElement();
  }

  @Override
  public void saveProperties(@NotNull JpsDummyElement properties, @NotNull Element element) {
    // no need to implement until IDEA is updated to use JPS as well!
  }
}
