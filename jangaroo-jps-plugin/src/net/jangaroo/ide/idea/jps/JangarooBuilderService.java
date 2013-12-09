package net.jangaroo.ide.idea.jps;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildTargetType;
import org.jetbrains.jps.incremental.BuilderService;
import org.jetbrains.jps.incremental.TargetBuilder;

import java.util.Arrays;
import java.util.List;

/**
 * Created with IntelliJ IDEA. User: fwienber Date: 26.11.13 Time: 11:24 To change this template use File | Settings |
 * File Templates.
 */
public class JangarooBuilderService extends BuilderService {

  public JangarooBuilderService() {
    System.out.println("gotcha: created JangarooBuilderService");
  }

  @NotNull
  @Override
  public List<? extends BuildTargetType<?>> getTargetTypes() {
    return Arrays.<BuildTargetType<?>>asList(new BuildTargetType[]{ JangarooBuildTargetType.INSTANCE });
  }

  @NotNull
  @Override
  public List<? extends TargetBuilder<?, ?>> createBuilders() {
    return Arrays.<TargetBuilder<?, ?>>asList(new TargetBuilder[]{ new JangarooBuilder() });
  }
}
