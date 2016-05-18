package net.jangaroo.ide.idea;

import com.intellij.compiler.impl.BuildTargetScopeProvider;
import com.intellij.flex.FlexCommonUtils;
import com.intellij.lang.javascript.flex.FlexModuleType;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerFilter;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import net.jangaroo.ide.idea.jps.JangarooBuildTargetType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.api.CmdlineProtoUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.jetbrains.jps.api.CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.TargetTypeBuildScope;

/**
 * Provide a JangarooBuildTarget for each module with a Jangaroo Facet.
 */
public class JangarooBuildTargetScopeProvider extends BuildTargetScopeProvider {
  @NotNull
  @Override
  public List<TargetTypeBuildScope> getBuildTargetScopes(@NotNull CompileScope compileScope, @NotNull CompilerFilter compilerFilter, @NotNull Project project, boolean forceBuild) {
    List<String> targetIds = new ArrayList<String>();
    for (Module module : compileScope.getAffectedModules()) {
      if (FlexModuleType.getInstance().equals(ModuleType.get(module))) {
        JangarooFacet jangarooFacet = JangarooFacet.ofModule(module);
        if (jangarooFacet != null) {
          targetIds.add(FlexCommonUtils.getBuildTargetId(module.getName(), module.getName(), false));
          targetIds.add(FlexCommonUtils.getBuildTargetId(module.getName(), module.getName(), true)); // TODO: only if there are any tests!
        }
      }
    }
    return targetIds.isEmpty() ? Collections.<TargetTypeBuildScope>emptyList()
      : Collections.singletonList(CmdlineProtoUtil.createTargetsScope(JangarooBuildTargetType.INSTANCE.getTypeId(), targetIds, forceBuild));
  }
}
