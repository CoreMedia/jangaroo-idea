package net.jangaroo.ide.idea;

import com.intellij.compiler.impl.BuildTargetScopeProvider;
import com.intellij.facet.FacetManager;
import com.intellij.flex.build.FlexBuildTargetType;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerFilter;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.api.CmdlineProtoUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.jetbrains.jps.api.CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.TargetTypeBuildScope;

/**
 * Created with IntelliJ IDEA. User: fwienber Date: 26.11.13 Time: 13:38 To change this template use File | Settings |
 * File Templates.
 */
public class JangarooBuildTargetScopeProvider extends BuildTargetScopeProvider {
  @NotNull
  @Override
  public List<TargetTypeBuildScope> getBuildTargetScopes(@NotNull CompileScope compileScope, @NotNull CompilerFilter compilerFilter, @NotNull Project project, boolean forceBuild) {
    List<String> targetIds = new ArrayList<String>();
    for (Module module : compileScope.getAffectedModules()) {
      if (FacetManager.getInstance(module).getFacetByType(JangarooFacetType.ID) != null) {
        targetIds.add(module.getName());
      }
    }
    return Collections.singletonList(CmdlineProtoUtil.createTargetsScope("jangaroo", targetIds, forceBuild));
  }
}
