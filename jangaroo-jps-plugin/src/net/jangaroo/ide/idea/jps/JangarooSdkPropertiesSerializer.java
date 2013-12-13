package net.jangaroo.ide.idea.jps;

import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsDummyElement;
import org.jetbrains.jps.model.serialization.library.JpsSdkPropertiesSerializer;

/**
 * Serialize Jangaroo SDKs for JPS.
 */
public class JangarooSdkPropertiesSerializer extends JpsSdkPropertiesSerializer<JpsDummyElement> {

  public static final String JANGAROO_SDK_TYPE_ID = "Jangaroo SDK";

  public JangarooSdkPropertiesSerializer() {
    super(JANGAROO_SDK_TYPE_ID, JpsJangarooSdkType.INSTANCE);
  }

  @NotNull
  @Override
  public JpsDummyElement loadProperties(@Nullable Element propertiesElement) {
    return JpsJangarooSdkType.INSTANCE.createDefaultProperties();
  }

  @Override
  public void saveProperties(@NotNull JpsDummyElement properties, @NotNull Element element) {
    System.out.println("gotcha!");
  }
}
