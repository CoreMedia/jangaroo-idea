package net.jangaroo.ide.idea.jps;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.BuildTargetLoader;
import org.jetbrains.jps.builders.BuildTargetType;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.java.JpsJavaModuleType;
import org.jetbrains.jps.model.module.JpsModuleSourceRoot;
import org.jetbrains.jps.model.module.JpsTypedModule;

import java.util.ArrayList;
import java.util.List;

/**
 * Created with IntelliJ IDEA. User: fwienber Date: 26.11.13 Time: 11:41 To change this template use File | Settings |
 * File Templates.
 */
public class JangarooBuildTargetType extends BuildTargetType<JangarooBuildTarget> {

  public static final JangarooBuildTargetType INSTANCE = new JangarooBuildTargetType();

  public JangarooBuildTargetType() {
    super("jangaroo");
  }

  @NotNull
  @Override
  public List<JangarooBuildTarget> computeAllTargets(@NotNull JpsModel model) {
    List<JangarooBuildTarget> targets = new ArrayList<JangarooBuildTarget>();
    JpsProject project = model.getProject();
    for (JpsTypedModule module : project.getModules(JpsJavaModuleType.INSTANCE)) {
      for (JpsModuleSourceRoot sourceRoot : module.getSourceRoots()) {
        if (sourceRoot.getUrl().endsWith("/joo")) {
          targets.add(new JangarooBuildTarget(model, module.getName()));
          break;
        }
      }
    }
    return targets;
  }

  @NotNull
  @Override
  public BuildTargetLoader<JangarooBuildTarget> createLoader(@NotNull JpsModel model) {
    return new JangarooBuildTargetLoader(model);
  }


  private static class JangarooBuildTargetLoader extends BuildTargetLoader<JangarooBuildTarget> {
    private JpsModel model;

    public JangarooBuildTargetLoader(JpsModel model) {
      this.model = model;
    }

    @Nullable
    @Override
    public JangarooBuildTarget createTarget(@NotNull String targetId) {
      return new JangarooBuildTarget(model, targetId);
    }
  }
}
