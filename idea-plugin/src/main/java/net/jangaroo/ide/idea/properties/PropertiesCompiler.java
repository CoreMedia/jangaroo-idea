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
package net.jangaroo.ide.idea.properties;

import com.intellij.compiler.make.MakeUtil;
import com.intellij.facet.FacetManager;
import com.intellij.facet.FacetTypeId;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.compiler.IntermediateOutputCompiler;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import net.jangaroo.ide.idea.AbstractCompiler;
import net.jangaroo.ide.idea.JangarooFacet;
import net.jangaroo.ide.idea.JangarooFacetType;
import net.jangaroo.ide.idea.JoocConfigurationBean;
import net.jangaroo.ide.idea.exml.ExmlCompiler;
import net.jangaroo.ide.idea.exml.ExmlFacetType;
import net.jangaroo.ide.idea.exml.ExmlcConfigurationBean;
import net.jangaroo.ide.idea.util.CompilerLoader;
import net.jangaroo.ide.idea.util.OutputSinkItem;
import net.jangaroo.jooc.api.Jooc;
import net.jangaroo.properties.api.Propc;
import net.jangaroo.utils.FileLocations;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static net.jangaroo.ide.idea.util.IdeaFileUtils.toPath;

/**
 * An IDEA wrapper for Jangaroo's properties compiler "propc".
 */
public class PropertiesCompiler extends AbstractCompiler implements IntermediateOutputCompiler {

  @NotNull
  public String getDescription() {
    return "Jangaroo Properties Compiler";
  }

  public boolean isCompilableFile(VirtualFile file, CompileContext context) {
    // TODO: check source path!
    if ("properties".equals(file.getExtension())) {
      Module module = context.getModuleByFile(file);
      if (module != null) {
        FacetManager facetManager = FacetManager.getInstance(module);
        if (facetManager != null && facetManager.getFacetByType(ExmlFacetType.ID) != null) { // must have EXML Facet!
          VirtualFile sourceRoot = MakeUtil.getSourceRoot(context, module, file);
          return !(sourceRoot != null && sourceRoot.getPath().endsWith("/webapp")); // hack: skip all files under .../webapp
        }
      }
    }
    return false;
  }

  @Override
  protected Set<String> getInputFileSuffixes() {
    return Collections.singleton("properties");
  }

  @Override
  protected String getOutputFileSuffix() {
    return Jooc.AS_SUFFIX;
  }

  private Propc getPropc(String jangarooSdkName, FileLocations compilerConfiguration, CompileContext context) {
    Propc propc = null;
    try {
      propc = CompilerLoader.loadPropc(getJarFileNames(jangarooSdkName));
      propc.setConfig(compilerConfiguration);
    } catch (FileNotFoundException e) {
      context.addMessage(CompilerMessageCategory.ERROR, e.getMessage(), null, -1, -1);
    } catch (Exception e) {
      context.addMessage(CompilerMessageCategory.ERROR, jangarooSdkName +
        " not correctly set up or not compatible with this Jangaroo IDEA plugin: " + e.getMessage(),
        null, -1, -1);
    }
    return propc;
  }

  protected OutputSinkItem compile(CompileContext context, Module module, List<VirtualFile> files, boolean forTests) {
    JoocConfigurationBean joocConfigurationBean = getJoocConfigurationBean(module);
    if (joocConfigurationBean == null) {
      return null;
    }
    ExmlcConfigurationBean exmlcConfigurationBean = ExmlCompiler.getExmlConfig(module);
    if (exmlcConfigurationBean == null) {
      return null;
    }
    FileLocations exmlConfiguration = new FileLocations();
    updateFileLocations(exmlConfiguration, module, files, forTests);
    String generatedSourcesDirectory = toPath(exmlcConfigurationBean.getGeneratedSourcesDirectory());
    exmlConfiguration.setOutputDirectory(new File(generatedSourcesDirectory));
    Propc generator = getPropc(joocConfigurationBean.jangarooSdkName, exmlConfiguration, context);

    OutputSinkItem outputSinkItem = null;
    for (VirtualFile file : files) {
      if (outputSinkItem == null) {
        outputSinkItem = createGeneratedSourcesOutputSinkItem(context, generatedSourcesDirectory);
      }
      File generatedPropertiesClass = generator.generate(VfsUtil.virtualToIoFile(file));
      getLog().info("properties->as: " + file.getUrl() + " -> " + generatedPropertiesClass.getPath());
      outputSinkItem.addOutputItem(file, generatedPropertiesClass);
    }
    return outputSinkItem;
  }

  protected FacetTypeId<JangarooFacet> getFacetType() {
    return JangarooFacetType.ID;
  }

  private static Logger getLog() {
    return Logger.getInstance("PropertiesCompiler");
  }
}
