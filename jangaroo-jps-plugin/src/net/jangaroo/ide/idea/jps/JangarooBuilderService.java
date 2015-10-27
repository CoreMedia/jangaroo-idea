package net.jangaroo.ide.idea.jps;

import net.jangaroo.ide.idea.jps.exml.ExmlBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildTargetType;
import org.jetbrains.jps.incremental.BuilderService;
import org.jetbrains.jps.incremental.ModuleLevelBuilder;
import org.jetbrains.jps.incremental.TargetBuilder;

import java.util.Collections;
import java.util.List;

/**
 * Factory for JangarooBuilder with target type JangarooBuildTargetType.
 */
public class JangarooBuilderService extends BuilderService {

  public JangarooBuilderService() {
  }

  @NotNull
  public List<? extends ModuleLevelBuilder> createModuleLevelBuilders() {
    return Collections.singletonList(new ExmlBuilder());
  }

  @NotNull
  @Override
  public List<? extends BuildTargetType<?>> getTargetTypes() {
    return Collections.singletonList(JangarooBuildTargetType.INSTANCE);
  }

  @NotNull
  @Override
  public List<? extends TargetBuilder<?, ?>> createBuilders() {
    return Collections.singletonList(new JangarooBuilder());
  }

}
