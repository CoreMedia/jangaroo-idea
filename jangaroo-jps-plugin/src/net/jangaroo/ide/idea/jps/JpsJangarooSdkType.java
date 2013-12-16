package net.jangaroo.ide.idea.jps;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementTypeWithDefaultProperties;
import org.jetbrains.jps.model.JpsSimpleElement;
import org.jetbrains.jps.model.library.sdk.JpsSdkType;

import java.util.Collections;

/**
 * JPS version of Jangaroo SDK type.
 */
public class JpsJangarooSdkType extends JpsSdkType<JpsSimpleElement<JpsJangarooSdkProperties>> implements JpsElementTypeWithDefaultProperties<JpsJangarooSdkProperties> {

  public static final JpsJangarooSdkType INSTANCE = new JpsJangarooSdkType();

  @NotNull
  @Override
  public JpsJangarooSdkProperties createDefaultProperties() {
    return new JpsJangarooSdkProperties(Collections.<String>emptyList());
  }

  @Override
  public String toString() {
    return "jangaroo sdk type";
  }
}
