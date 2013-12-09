package net.jangaroo.ide.idea.jps;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.BuildRootDescriptor;
import org.jetbrains.jps.builders.BuildRootIndex;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.BuildTargetRegistry;
import org.jetbrains.jps.builders.TargetOutputIndex;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.indices.IgnoredFileIndex;
import org.jetbrains.jps.indices.ModuleExcludeIndex;
import org.jetbrains.jps.model.JpsModel;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Created with IntelliJ IDEA. User: fwienber Date: 26.11.13 Time: 11:37 To change this template use File | Settings |
 * File Templates.
 */
public class JangarooBuildTarget extends BuildTarget<BuildRootDescriptor> {
  private JpsModel model;
  private String id;

  public JangarooBuildTarget(@NotNull JpsModel model, @NotNull String id) {
    super(JangarooBuildTargetType.INSTANCE);
    this.model = model;
    this.id = id;
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public Collection<BuildTarget<?>> computeDependencies(BuildTargetRegistry targetRegistry, TargetOutputIndex outputIndex) {
    return Collections.emptyList();
  }

  @NotNull
  @Override
  public List<BuildRootDescriptor> computeRootDescriptors(JpsModel model, ModuleExcludeIndex index, IgnoredFileIndex ignoredFileIndex, BuildDataPaths dataPaths) {
    return Collections.emptyList();
  }

  @Nullable
  @Override
  public BuildRootDescriptor findRootDescriptor(String rootId, BuildRootIndex rootIndex) {
    return null;
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return "Jangaroo Build Target " + model.toString() + " / " + getId();
  }

  @NotNull
  @Override
  public Collection<File> getOutputRoots(CompileContext context) {
    return Collections.emptyList();
  }
}
