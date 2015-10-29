//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package net.jangaroo.ide.idea.jps;

import com.intellij.flex.FlexCommonBundle;
import com.intellij.flex.FlexCommonUtils;
import com.intellij.flex.model.JpsFlexProjectLevelCompilerOptionsExtension;
import com.intellij.flex.model.bc.BuildConfigurationNature;
import com.intellij.flex.model.bc.JpsAirPackagingOptions;
import com.intellij.flex.model.bc.JpsFlexBCDependencyEntry;
import com.intellij.flex.model.bc.JpsFlexBuildConfiguration;
import com.intellij.flex.model.bc.JpsFlexDependencyEntry;
import com.intellij.flex.model.bc.JpsFlexModuleOrProjectCompilerOptions;
import com.intellij.flex.model.bc.JpsLibraryDependencyEntry;
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
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.library.JpsLibrary;
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
import java.util.Iterator;
import java.util.List;

public class JangarooBuildTarget extends BuildTarget<BuildRootDescriptor> {
  @NotNull
  private final JpsFlexBuildConfiguration bc;
  @NotNull
  private final String id;

  private JangarooBuildTarget(@NotNull JpsFlexBuildConfiguration bc, @NotNull String id) {
    super(JangarooBuildTargetType.INSTANCE);
    this.bc = bc;
    this.id = id;
  }

  @NotNull
  public static JangarooBuildTarget create(@NotNull JpsFlexBuildConfiguration bc, @Nullable Boolean forcedDebugStatus) {
    String id = FlexCommonUtils.getBuildTargetId(bc.getModule().getName(), bc.getName(), forcedDebugStatus);
    if (forcedDebugStatus == null) {
      return new JangarooBuildTarget(bc, id);
    } else {
      JpsFlexBuildConfiguration bcCopy = bc.getModule().getProperties().createCopy(bc);
      String additionalOptions = FlexCommonUtils.removeOptions(bc.getCompilerOptions().getAdditionalOptions(), "debug", "compiler.debug");
      bcCopy.getCompilerOptions().setAdditionalOptions(additionalOptions + " -debug=" + forcedDebugStatus.toString());
      return new JangarooBuildTarget(bcCopy, id);
    }
  }

  @Nullable
  public static JangarooBuildTarget create(JpsProject project, @NotNull JpsRunConfigurationType<? extends JpsBCBasedRunnerParameters<?>> runConfigType, @NotNull String runConfigName) {
    assert runConfigType instanceof JpsFlashRunConfigurationType || runConfigType instanceof JpsFlexUnitRunConfigurationType : runConfigType;

    String runConfigTypeId = runConfigType instanceof JpsFlashRunConfigurationType ? "FlashRunConfigurationType" : "FlexUnitRunConfigurationType";
    JpsTypedRunConfiguration runConfig = FlexCommonUtils.findRunConfiguration(project, runConfigType, runConfigName);
    JpsFlexBuildConfiguration bc = runConfig == null ? null : ((JpsBCBasedRunnerParameters)runConfig.getProperties()).getBC(project);
    String id = FlexCommonUtils.getBuildTargetIdForRunConfig(runConfigTypeId, runConfigName);
    return bc == null ? null : new JangarooBuildTarget(bc, id);
  }

  @NotNull
  public String getId() {
    return id;
  }

  public boolean isTests() {
    return false; // TODO
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
          result.add(create(dependencyBC, null));
        }
      }
    }

    result.trimToSize();
    return result;
  }

  @NotNull
  public List<BuildRootDescriptor> computeRootDescriptors(JpsModel model, ModuleExcludeIndex index, IgnoredFileIndex ignoredFileIndex, BuildDataPaths dataPaths) {
    ArrayList<BuildRootDescriptor> result = new ArrayList<BuildRootDescriptor>();
    ArrayList<File> srcRoots = new ArrayList<File>();
    Iterator nature = bc.getModule().getSourceRoots(JavaSourceRootType.SOURCE).iterator();

    JpsTypedModuleSourceRoot i$;
    File cssPath;
    while (nature.hasNext()) {
      i$ = (JpsTypedModuleSourceRoot)nature.next();
      cssPath = JpsPathUtil.urlToFile(i$.getUrl());
      result.add(new JangarooSourceRootDescriptor(this, cssPath));
      srcRoots.add(cssPath);
    }

    if (FlexCommonUtils.isFlexUnitBC(bc)) {
      nature = bc.getModule().getSourceRoots(JavaSourceRootType.TEST_SOURCE).iterator();

      while (nature.hasNext()) {
        i$ = (JpsTypedModuleSourceRoot)nature.next();
        cssPath = JpsPathUtil.urlToFile(i$.getUrl());
        result.add(new JangarooSourceRootDescriptor(this, cssPath));
        srcRoots.add(cssPath);
      }
    }

    nature = bc.getDependencies().getEntries().iterator();

    while (nature.hasNext()) {
      JpsFlexDependencyEntry var13 = (JpsFlexDependencyEntry)nature.next();
      if (var13 instanceof JpsFlexBCDependencyEntry) {
        JpsFlexBuildConfiguration var15 = ((JpsFlexBCDependencyEntry)var13).getBC();
        if (var15 != null) {
          result.add(new JangarooSourceRootDescriptor(this, new File(var15.getActualOutputFilePath())));
        }
      } else if (var13 instanceof JpsLibraryDependencyEntry) {
        JpsLibrary var16 = ((JpsLibraryDependencyEntry)var13).getLibrary();
        if (var16 != null) {

          for (String rootUrl : var16.getRootUrls(JpsOrderRootType.COMPILED)) {
            result.add(new JangarooSourceRootDescriptor(this, JpsPathUtil.urlToFile(rootUrl)));
          }
        }
      }
    }

    BuildConfigurationNature var12 = bc.getNature();
    if (var12.isWebPlatform() && var12.isApp() && bc.isUseHtmlWrapper() && !bc.getWrapperTemplatePath().isEmpty()) {
      this.addIfNotUnderRoot(result, new File(bc.getWrapperTemplatePath()), srcRoots);
    }

    if (FlexCommonUtils.canHaveRLMsAndRuntimeStylesheets(bc)) {

      for (String var17 : bc.getCssFilesToCompile()) {
        if (!var17.isEmpty()) {
          this.addIfNotUnderRoot(result, new File(var17), srcRoots);
        }
      }
    }

    if (!bc.getCompilerOptions().getAdditionalConfigFilePath().isEmpty()) {
      this.addIfNotUnderRoot(result, new File(bc.getCompilerOptions().getAdditionalConfigFilePath()), srcRoots);
    }

    if (var12.isApp()) {
      if (var12.isDesktopPlatform()) {
        this.addAirDescriptorPathIfCustom(result, bc.getAirDesktopPackagingOptions(), srcRoots);
      } else if (var12.isMobilePlatform()) {
        if (bc.getAndroidPackagingOptions().isEnabled()) {
          this.addAirDescriptorPathIfCustom(result, bc.getAndroidPackagingOptions(), srcRoots);
        }

        if (bc.getIosPackagingOptions().isEnabled()) {
          this.addAirDescriptorPathIfCustom(result, bc.getIosPackagingOptions(), srcRoots);
        }
      }
    }

    return result;
  }

  private void addIfNotUnderRoot(List<BuildRootDescriptor> descriptors, File file, Collection<File> roots) {
    Iterator i$ = roots.iterator();

    File root;
    do {
      if (!i$.hasNext()) {
        descriptors.add(new JangarooSourceRootDescriptor(this, file));
        return;
      }

      root = (File)i$.next();
    } while (!FileUtil.isAncestor(root, file, false));

  }

  private void addAirDescriptorPathIfCustom(List<BuildRootDescriptor> descriptors, JpsAirPackagingOptions packagingOptions, Collection<File> srcRoots) {
    if (!packagingOptions.isUseGeneratedDescriptor() && !packagingOptions.getCustomDescriptorPath().isEmpty()) {
      this.addIfNotUnderRoot(descriptors, new File(packagingOptions.getCustomDescriptorPath()), srcRoots);
    }

  }

  @Nullable
  public BuildRootDescriptor findRootDescriptor(String rootId, BuildRootIndex rootIndex) {
    Iterator i$ = rootIndex.getTargetRoots(this, null).iterator();

    BuildRootDescriptor descriptor;
    do {
      if (!i$.hasNext()) {
        return null;
      }

      descriptor = (BuildRootDescriptor)i$.next();
    } while (!descriptor.getRootId().equals(rootId));

    return descriptor;
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
    if (o != null && this.getClass() == o.getClass()) {
      JangarooBuildTarget target = (JangarooBuildTarget)o;
      return id.equals(target.id);
    }
    return false;
  }

  public int hashCode() {
    return id.hashCode();
  }

}
