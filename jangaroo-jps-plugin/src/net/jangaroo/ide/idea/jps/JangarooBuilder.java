package net.jangaroo.ide.idea.jps;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildOutputConsumer;
import org.jetbrains.jps.builders.BuildRootDescriptor;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.ProjectBuildException;
import org.jetbrains.jps.incremental.TargetBuilder;

import java.io.IOException;
import java.util.Collections;

/**
 * Created with IntelliJ IDEA. User: fwienber Date: 26.11.13 Time: 11:36 To change this template use File | Settings |
 * File Templates.
 */
public class JangarooBuilder extends TargetBuilder<BuildRootDescriptor, JangarooBuildTarget> {
  public JangarooBuilder() {
    super(Collections.singletonList(JangarooBuildTargetType.INSTANCE));
  }

  @Override
  public void build(@NotNull JangarooBuildTarget target, @NotNull DirtyFilesHolder<BuildRootDescriptor, JangarooBuildTarget> holder, @NotNull BuildOutputConsumer outputConsumer, @NotNull CompileContext context) throws ProjectBuildException, IOException {
    System.out.println("gotcha!");
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return "Jangaroo Compiler";
  }
}
