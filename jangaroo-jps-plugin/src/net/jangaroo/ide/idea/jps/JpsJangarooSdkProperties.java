package net.jangaroo.ide.idea.jps;

import com.intellij.openapi.projectRoots.SdkAdditionalData;

import java.util.List;

/**
 * POJO of Jangaroo SDK properties, to be serialized for JPS usage.
 */
public class JpsJangarooSdkProperties implements SdkAdditionalData {

  private List<String> jarPaths;

  public JpsJangarooSdkProperties(List<String> jarPaths) {
    this.jarPaths = jarPaths;
  }

  @SuppressWarnings("CloneDoesntCallSuperClone")
  @Override
  public Object clone() throws CloneNotSupportedException {
    return new JpsJangarooSdkProperties(jarPaths);
  }

  public List<String> getJarPaths() {
    return jarPaths;
  }

}
