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
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.IntermediateOutputCompiler;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import net.jangaroo.ide.idea.AbstractCompiler;
import net.jangaroo.ide.idea.exml.ExmlCompiler;
import net.jangaroo.ide.idea.exml.ExmlFacetType;
import net.jangaroo.ide.idea.exml.ExmlcConfigurationBean;
import net.jangaroo.ide.idea.util.OutputSinkItem;
import net.jangaroo.jooc.Jooc;
import net.jangaroo.properties.PropertyClassGenerator;
import net.jangaroo.utils.FileLocations;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
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
  protected String getOutputFileSuffix() {
    return Jooc.AS_SUFFIX;
  }

  @Override
  protected OutputSinkItem compile(CompileContext context, Module module, List<VirtualFile> files) {
    ExmlcConfigurationBean exmlcConfigurationBean = ExmlCompiler.getExmlConfig(module);
    FileLocations exmlConfiguration = new FileLocations();
    updateFileLocations(exmlConfiguration, module, files);
    String generatedSourcesDirectory = exmlcConfigurationBean.getGeneratedSourcesDirectory();
    exmlConfiguration.setOutputDirectory(new File(generatedSourcesDirectory));
    PropertyClassGenerator generator = new PropertyClassGenerator(exmlConfiguration);
    Map<File,Set<File>> outputFileMap = generator.generate();
    OutputSinkItem outputSinkItem = null;
    for (Map.Entry<File, Set<File>> entry : outputFileMap.entrySet()) {
      int sourceFileIndex = exmlConfiguration.getSourceFiles().indexOf(entry.getKey());
      if (sourceFileIndex == -1) {
        throw new IllegalStateException("Compiler returned mapping for unknown source file " + entry.getKey().getAbsolutePath());
      }
      if (outputSinkItem == null) {
        outputSinkItem = createGeneratedSourcesOutputSinkItem(context, generatedSourcesDirectory);
      }
      outputSinkItem.addOutputItem(files.get(sourceFileIndex), entry.getValue().iterator().next());
    }
    return outputSinkItem;
  }
}