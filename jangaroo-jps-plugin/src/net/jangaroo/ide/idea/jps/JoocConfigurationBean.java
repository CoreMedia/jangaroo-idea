/*
 * Copyright 2009 CoreMedia AG
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS
 * IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package net.jangaroo.ide.idea.jps;

import net.jangaroo.jooc.config.PublicApiViolationsMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.ex.JpsElementBase;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;

import static net.jangaroo.ide.idea.jps.util.IdeaFileUtils.toPath;

/**
 * IDEA serialization adapter of JoocConfiguration.
 */
public class JoocConfigurationBean extends JpsElementBase<JoocConfigurationBean> {
  public static final int DEBUG_LEVEL_NONE = 0;
  public static final int DEBUG_LEVEL_LINES = 50;
  public static final int DEBUG_LEVEL_SOURCE = 100;

  public String jangarooSdkName;
  public int debugLevel = DEBUG_LEVEL_SOURCE;
  public boolean verbose = false;
  public boolean enableAssertions = true;
  public boolean allowDuplicateLocalVariables = false;
  public String outputPrefix;
  public String outputDirectory = "target/jangaroo-output/joo/classes";
  public String outputFilePrefix;
  public String apiOutputDirectory = "target/jangaroo-output/META-INF/joo-api";
  public String testOutputDirectory = "target/jangaroo-test-output/joo/classes";
  public boolean showCompilerInfoMessages = false;
  public PublicApiViolationsMode publicApiViolationsMode;
  public String extNamespace;
  public String extSassNamespace;

  public String jangarooSdk;

  public JoocConfigurationBean() {
  }

  public JoocConfigurationBean(JoocConfigurationBean source) {
    jangarooSdk = source.jangarooSdk;
  }

  @NotNull
  @Override
  public JoocConfigurationBean createCopy() {
    return new JoocConfigurationBean(this);
  }

  @Override
  public void applyChanges(@NotNull JoocConfigurationBean jpsJangarooSettings) {
  }

  public boolean isDebug() {
    return debugLevel > DEBUG_LEVEL_NONE;
  }

  public boolean isDebugLines() {
    return debugLevel >= DEBUG_LEVEL_LINES;
  }

  public boolean isDebugSource() {
    return debugLevel >= DEBUG_LEVEL_SOURCE;
  }

  public File getOutputDirectory() {
    File outputDir = new File(toPath(outputDirectory));
    if (!outputDir.isAbsolute() && outputPrefix != null && outputPrefix.length() > 0) {
      outputDir = new File(outputPrefix + outputDir.getPath());
    }
    return outputDir;
  }

  public File getApiOutputDirectory() {
    return apiOutputDirectory == null || apiOutputDirectory.length() == 0 ? null : new File(toPath(apiOutputDirectory));
  }

  public File getTestOutputDirectory() {
    File testOutputDir = new File(toPath(testOutputDirectory));
    if (!testOutputDir.isAbsolute() && outputPrefix != null && outputPrefix.length() > 0) {
      testOutputDir = new File(outputPrefix + testOutputDir.getPath());
    }
    return testOutputDir;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    JoocConfigurationBean that = (JoocConfigurationBean)o;

    boolean[] flags = getFlags();
    boolean[] thatFlags = that.getFlags();
    for (int i = 0; i < flags.length; i++) {
      if (flags[i] != thatFlags[i])
        return false;
    }
    //noinspection StringEquality
    return jangarooSdkName == that.jangarooSdkName
      && debugLevel==that.debugLevel
      && (outputPrefix==null ? that.outputPrefix==null : outputPrefix.equals(that.outputPrefix))
      && outputDirectory.equals(that.outputDirectory)
      && (outputFilePrefix == null ? that.outputFilePrefix == null : outputFilePrefix.equals(that.outputFilePrefix))
      && publicApiViolationsMode == that.publicApiViolationsMode
      && (extNamespace==null ? that.extNamespace==null : extNamespace.equals(that.extNamespace))
      && (extSassNamespace==null ? that.extSassNamespace==null : extSassNamespace.equals(that.extSassNamespace));
  }

  @Override
  public int hashCode() {
    int result = jangarooSdkName.hashCode();
    for (boolean flag : getFlags()) {
      result = 31 * result + (flag ? 1 : 0);
    }
    result = 31 * result + debugLevel;
    result = 31 * result + outputDirectory.hashCode();
    result = 31 * result + (outputFilePrefix == null ? 0 : outputFilePrefix.hashCode());
    result = 31 * result + publicApiViolationsMode.hashCode();
    result = 31 * result + (extNamespace == null ? 0 : extNamespace.hashCode());
    result = 31 * result + (extSassNamespace == null ? 0 : extSassNamespace.hashCode());
    return result;
  }

  private boolean[] getFlags() {
    return new boolean[]{verbose, enableAssertions,
      allowDuplicateLocalVariables, showCompilerInfoMessages};
  }

}
