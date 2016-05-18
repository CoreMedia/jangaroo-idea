package net.jangaroo.ide.idea.jps;

import com.intellij.flex.FlexCommonBundle;
import com.intellij.flex.FlexCommonUtils;
import com.intellij.flex.model.JpsFlexProjectLevelCompilerOptionsExtension;
import com.intellij.flex.model.bc.JpsFlexBCDependencyEntry;
import com.intellij.flex.model.bc.JpsFlexBuildConfiguration;
import com.intellij.flex.model.bc.JpsFlexDependencyEntry;
import com.intellij.flex.model.bc.JpsFlexModuleOrProjectCompilerOptions;
import com.intellij.flex.model.bc.JpsLibraryDependencyEntry;
import com.intellij.flex.model.bc.LinkageType;
import com.intellij.flex.model.bc.impl.JpsFlexBCState;
import com.intellij.flex.model.bc.impl.JpsFlexCompilerOptionsImpl;
import com.intellij.flex.model.run.JpsBCBasedRunnerParameters;
import com.intellij.flex.model.run.JpsFlashRunConfigurationType;
import com.intellij.flex.model.run.JpsFlexUnitRunConfigurationType;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.PathUtilRt;
import com.intellij.util.xmlb.XmlSerializer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.BuildRootDescriptor;
import org.jetbrains.jps.builders.BuildRootIndex;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.BuildTargetRegistry;
import org.jetbrains.jps.builders.TargetOutputIndex;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.indices.IgnoredFileIndex;
import org.jetbrains.jps.indices.ModuleExcludeIndex;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.java.JavaSourceRootProperties;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsLibraryRoot;
import org.jetbrains.jps.model.library.JpsOrderRootType;
import org.jetbrains.jps.model.module.JpsTypedModuleSourceRoot;
import org.jetbrains.jps.model.runConfiguration.JpsRunConfigurationType;
import org.jetbrains.jps.model.runConfiguration.JpsTypedRunConfiguration;
import org.jetbrains.jps.util.JpsPathUtil;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class JangarooBuildTarget extends BuildTarget<BuildRootDescriptor> {
  @NotNull
  private final JpsFlexBuildConfiguration bc;
  @NotNull
  private final String id;
  private final boolean tests;

  private JangarooBuildTarget(@NotNull JpsFlexBuildConfiguration bc, @NotNull String id, boolean tests) {
    super(JangarooBuildTargetType.INSTANCE);
    this.bc = bc;
    this.id = id;
    this.tests = tests;
  }

  @NotNull
  public static JangarooBuildTarget create(@NotNull JpsFlexBuildConfiguration bc, boolean tests) {
    String id = FlexCommonUtils.getBuildTargetId(bc.getModule().getName(), bc.getName(), tests);
    return new JangarooBuildTarget(bc, id, tests);
  }

  @Nullable
  public static JangarooBuildTarget create(JpsProject project, @NotNull JpsRunConfigurationType<? extends JpsBCBasedRunnerParameters<?>> runConfigType, @NotNull String runConfigName) {
    assert runConfigType instanceof JpsFlashRunConfigurationType || runConfigType instanceof JpsFlexUnitRunConfigurationType : runConfigType;

    String runConfigTypeId = runConfigType instanceof JpsFlashRunConfigurationType ? "FlashRunConfigurationType" : "FlexUnitRunConfigurationType";
    JpsTypedRunConfiguration runConfig = FlexCommonUtils.findRunConfiguration(project, runConfigType, runConfigName);
    JpsFlexBuildConfiguration bc = runConfig == null ? null : ((JpsBCBasedRunnerParameters)runConfig.getProperties()).getBC(project);
    String id = FlexCommonUtils.getBuildTargetIdForRunConfig(runConfigTypeId, runConfigName);
    return bc == null ? null : new JangarooBuildTarget(bc, id, false);
  }

  @NotNull
  public String getId() {
    return id;
  }

  public boolean isTests() {
    return tests;
  }

  @NotNull
  public JpsFlexBuildConfiguration getBC() {
    return bc;
  }

  public Collection<BuildTarget<?>> computeDependencies(BuildTargetRegistry targetRegistry, TargetOutputIndex outputIndex) {
    ArrayList<BuildTarget<?>> result = new ArrayList<BuildTarget<?>>();
    List<JpsFlexDependencyEntry> entries = bc.getDependencies().getEntries();
    for (JpsFlexDependencyEntry entry : entries) {
      if (entry instanceof JpsFlexBCDependencyEntry) {
        JpsFlexBuildConfiguration dependencyBC = ((JpsFlexBCDependencyEntry)entry).getBC();
        if (dependencyBC != null) {
          result.add(create(dependencyBC, entry.getLinkageType() == LinkageType.Test));
        }
      }
    }

    result.trimToSize();
    return result;
  }

  @NotNull
  public List<BuildRootDescriptor> computeRootDescriptors(JpsModel model, ModuleExcludeIndex index, IgnoredFileIndex ignoredFileIndex, BuildDataPaths dataPaths) {
    ArrayList<BuildRootDescriptor> result = new ArrayList<BuildRootDescriptor>();

    Iterable<JpsTypedModuleSourceRoot<JavaSourceRootProperties>> moduleSourceRoots = bc.getModule().getSourceRoots(isTests() ? JavaSourceRootType.TEST_SOURCE : JavaSourceRootType.SOURCE);
    for (JpsTypedModuleSourceRoot<JavaSourceRootProperties> sourceRoot : moduleSourceRoots) {
      File srcRoot = sourceRoot.getFile();
      result.add(new JangarooSourceRootDescriptor(this, srcRoot));
    }
    if (isTests()) {
      // add sources to class path, not to source path:
      Iterable<JpsTypedModuleSourceRoot<JavaSourceRootProperties>> moduleProductionSourceRoots = bc.getModule().getSourceRoots(JavaSourceRootType.SOURCE);
      for (JpsTypedModuleSourceRoot<JavaSourceRootProperties> sourceRoot : moduleProductionSourceRoots) {
        result.add(new JangarooSourceRootDescriptor(this, sourceRoot.getFile()));
      }
    }

    for (JpsFlexDependencyEntry dependencyEntry : bc.getDependencies().getEntries()) {
      if (dependencyEntry.getLinkageType() != LinkageType.Test || isTests()) {
        if (dependencyEntry instanceof JpsFlexBCDependencyEntry) {
          JpsFlexBuildConfiguration buildConfiguration = ((JpsFlexBCDependencyEntry)dependencyEntry).getBC();
          if (buildConfiguration != null) {
            result.add(new JangarooSourceRootDescriptor(this, new File(buildConfiguration.getActualOutputFilePath())));
          }
        } else if (dependencyEntry instanceof JpsLibraryDependencyEntry) {
          JpsLibrary library = ((JpsLibraryDependencyEntry)dependencyEntry).getLibrary();
          if (library != null) {

            for (JpsLibraryRoot libraryRoot : library.getRoots(JpsOrderRootType.COMPILED)) {
              result.add(new JangarooSourceRootDescriptor(this, JpsPathUtil.urlToFile(libraryRoot.getUrl())));
            }
          }
        }
      }
    }

    return result;
  }

  public boolean isUnderSourceRoot(File sourceFile) {
    Iterable<JpsTypedModuleSourceRoot<JavaSourceRootProperties>> sourceRoots = getBC().getModule().getSourceRoots(isTests() ? JavaSourceRootType.TEST_SOURCE : JavaSourceRootType.SOURCE);
    for (JpsTypedModuleSourceRoot<JavaSourceRootProperties> sourceRoot : sourceRoots) {
      if (FileUtil.isAncestor(sourceRoot.getFile(), sourceFile, false)) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  public BuildRootDescriptor findRootDescriptor(String rootId, BuildRootIndex rootIndex) {
    for (BuildRootDescriptor descriptor : rootIndex.getTargetRoots(this, null)) {
      if (descriptor.getRootId().equals(rootId)) {
        return descriptor;
      }
    }
    return null;
  }

  @NotNull
  public String getPresentableName() {
    return FlexCommonBundle.message("bc.0.module.1", bc.getName(), bc.getModule().getName());
  }

  @NotNull
  public Collection<File> getOutputRoots(CompileContext context) {
    return Collections.singleton(new File(PathUtilRt.getParentPath(bc.getActualOutputFilePath())));
  }

  public void writeConfiguration(ProjectDescriptor pd, PrintWriter out) {
    out.println("id: " + id);
    out.println(JDOMUtil.writeElement(XmlSerializer.serialize(JpsFlexBCState.getState(bc)), "\n"));
    JpsFlexModuleOrProjectCompilerOptions moduleOptions = bc.getModule().getProperties().getModuleLevelCompilerOptions();
    out.println(JDOMUtil.writeElement(XmlSerializer.serialize(((JpsFlexCompilerOptionsImpl)moduleOptions).getState()), "\n"));
    JpsFlexModuleOrProjectCompilerOptions projectOptions = JpsFlexProjectLevelCompilerOptionsExtension.getProjectLevelCompilerOptions(bc.getModule().getProject());
    out.println(JDOMUtil.writeElement(XmlSerializer.serialize(((JpsFlexCompilerOptionsImpl)projectOptions).getState()), "\n"));
  }

  public String toString() {
    return id;
  }

  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o != null && getClass() == o.getClass()) {
      JangarooBuildTarget target = (JangarooBuildTarget)o;
      return id.equals(target.id);
    }
    return false;
  }

  public int hashCode() {
    return id.hashCode();
  }

}
