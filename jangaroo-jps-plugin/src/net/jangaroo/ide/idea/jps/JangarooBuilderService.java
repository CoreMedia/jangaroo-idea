package net.jangaroo.ide.idea.jps;

import net.jangaroo.ide.idea.jps.exml.ExmlBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildTargetType;
import org.jetbrains.jps.incremental.BuilderService;
import org.jetbrains.jps.incremental.ModuleLevelBuilder;

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
  public List<? extends ModuleLevelBuilder> createModuleLevelBuilders() {
    return Arrays.asList(new JangarooBuilder(), new ExmlBuilder());
  }

}
