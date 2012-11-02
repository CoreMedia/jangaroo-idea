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
package net.jangaroo.ide.idea;

import com.intellij.facet.FacetTypeId;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.compiler.TranslatingCompiler;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.module.Module;
import net.jangaroo.ide.idea.util.CompilerLoader;
import net.jangaroo.ide.idea.util.OutputSinkItem;
import net.jangaroo.jooc.api.CompilationResult;
import net.jangaroo.jooc.api.CompileLog;
import net.jangaroo.jooc.api.Jooc;
import net.jangaroo.jooc.config.JoocConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.FileNotFoundException;
import java.util.*;
import java.io.File;

/**
 * An IDEA wrapper for Jangaroo's ActionScript-to-JavaScript compiler "jooc".
 */
public class JangarooCompiler extends AbstractCompiler implements TranslatingCompiler {

  public static CompilationResult runJooc(CompileContext context, String jangarooSdkName, JoocConfiguration configuration, CompileLog log) {
    Jooc jooc;
    try {
      jooc = CompilerLoader.loadJooc(getJarFileNames(jangarooSdkName));
    } catch (FileNotFoundException e) {
      context.addMessage(CompilerMessageCategory.ERROR, e.getMessage(), null, -1, -1);
      return null;
    } catch (Exception e) {
      context.addMessage(CompilerMessageCategory.ERROR, jangarooSdkName +
        " not correctly set up or not compatible with this Jangaroo IDEA plugin: " + e.getMessage(),
        null, -1, -1);
      return null;
    }
    jooc.setConfig(configuration);
    jooc.setLog(log);
    return jooc.run();
  }

  @Override
  @NotNull
  public String getDescription() {
    return "Jangaroo Compiler";
  }

  @Override
  protected OutputSinkItem compile(CompileContext context, Module module, final List<VirtualFile> files, boolean forTests) {
    JoocConfiguration joocConfig = getJoocConfiguration(module, files, forTests);
    OutputSinkItem outputSinkItem = null;
    if (joocConfig!=null) {
      String outputDirectoryPath = joocConfig.getOutputDirectory().getPath();
      try {
        outputSinkItem = new OutputSinkItem(outputDirectoryPath);
        IdeaCompileLog ideaCompileLog = new IdeaCompileLog(context);
        getLog().info("running " + getDescription() + "...");
        JoocConfigurationBean joocConfigurationBean = getJoocConfigurationBean(module);
        CompilationResult result = runJooc(context, joocConfigurationBean.jangarooSdkName, joocConfig, ideaCompileLog);
        if (result == null) {
          return null;
        }
        Map<File, File> outputFileMap = result.getOutputFileMap();
        for (final VirtualFile file : files) {
          if (ideaCompileLog.hasErrors(file)) {
            outputSinkItem.addFileToRecompile(file);
          } else {
            File ioFile = VfsUtil.virtualToIoFile(file);
            File outputFile = outputFileMap.get(ioFile);
            if (outputFile != null) {
              outputSinkItem.addOutputItem(file, outputFile);
              String fileUrl = file.getUrl();
              if (joocConfigurationBean.showCompilerInfoMessages) {
                context.addMessage(CompilerMessageCategory.INFORMATION, "as->js (" + outputFile.getPath() + ")", fileUrl, -1, -1);
              }
              getLog().info("as->js: " + fileUrl + " -> " + outputFile.getPath());
            } else if (!outputFileMap.containsKey(ioFile)) {
              getLog().warn("No compiler error logged for " + file + ", but still no output file was generated / mapped.");
            }
          }
        }
        if (result.getResultCode() != CompilationResult.RESULT_CODE_OK && !ideaCompileLog.hasErrors()) {
          context.addMessage(CompilerMessageCategory.ERROR, "Compiler returned " + result.getResultCode(), null, -1, -1);
        }
      } catch (SecurityException e) {
        String message = "Output directory " + outputDirectoryPath + " does not exist and could not be created: " + e.getMessage();
        context.addMessage(CompilerMessageCategory.ERROR, message, null, -1, -1);
        getLog().warn(message);
      } catch (Exception e) {
        getLog().error("Internal error while running Jangaroo compiler.", e);
        context.addMessage(CompilerMessageCategory.ERROR, "Internal Jangaroo compiler error: " + e.getMessage(),
          null, -1, -1);
      }
    }
    return outputSinkItem;
  }

  protected String getInputFileSuffix() {
    return Jooc.AS_SUFFIX_NO_DOT;
  }

  @Override
  protected String getOutputFileSuffix() {
    return "js";
  }

  protected FacetTypeId<JangarooFacet> getFacetType() {
    return JangarooFacetType.ID;
  }
}
