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

import com.intellij.compiler.impl.CompilerUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.compiler.TranslatingCompiler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.module.Module;
import com.intellij.util.Chunk;
import net.jangaroo.ide.idea.util.OutputSinkItem;
import net.jangaroo.jooc.config.JoocConfiguration;
import org.jetbrains.annotations.NotNull;
import net.jangaroo.jooc.Jooc;

import java.util.*;
import java.io.File;

/**
 *
 */
public class JangarooCompiler extends AbstractCompiler implements TranslatingCompiler {

  @Override
  @NotNull
  public String getDescription() {
    return "Jangaroo Compiler " + JangarooFacetImporter.JANGAROO_VERSION;
  }

  public boolean isCompilableFile(VirtualFile file, CompileContext context) {
    // Does not work due to ClassLoader problems:
    // return JavaScriptSupportLoader.ECMA_SCRIPT_L4.equals(JavaScriptSupportLoader.getLanguageDialect(file));
    return Jooc.AS_SUFFIX_NO_DOT.equals(file.getExtension())
      && !file.getPath().contains("/joo-api/") // hack: skip all files under .../joo-api
      && JangarooFacet.ofModule(context.getModuleByFile(file)) != null;
  }

  public void compile(final CompileContext context, Chunk<Module> moduleChunk, final VirtualFile[] files, final OutputSink outputSink) {
    final Collection<OutputSinkItem> outputs = new ArrayList<OutputSinkItem>();
    final Map<Module, List<VirtualFile>> filesByModule = CompilerUtil.buildModuleToFilesMap(context, files);

    ApplicationManager.getApplication().runReadAction(new Runnable() {

      public void run() {
        for (Map.Entry<Module, List<VirtualFile>> filesOfModuleEntry : filesByModule.entrySet()) {
          OutputSinkItem outputSinkItem = compile(context, filesOfModuleEntry.getKey(), filesOfModuleEntry.getValue());
          if (outputSinkItem != null) {
            outputs.add(outputSinkItem);
          }
        }
      }

    });
    for (OutputSinkItem outputSinkItem : outputs) {
      outputSinkItem.addTo(outputSink);
    }
  }

  protected OutputSinkItem compile(CompileContext context, Module module, final List<VirtualFile> files) {
    JoocConfiguration joocConfig = getJoocConfiguration(module, files);
    OutputSinkItem outputSinkItem = null;
    if (joocConfig!=null) {
      String outputDirectoryPath = joocConfig.getOutputDirectory().getPath();
      try {
        outputSinkItem = new OutputSinkItem(outputDirectoryPath);
        IdeaCompileLog ideaCompileLog = new IdeaCompileLog(context);
        getLog().info("running " + getDescription() + "...");
        new Jooc(joocConfig, ideaCompileLog).run();
        for (final VirtualFile file : files) {
          if (ideaCompileLog.hasErrors(file)) {
            outputSinkItem.addFileToRecompile(file);
          } else {
            File outputFile = computeOutputFile(context, module, outputDirectoryPath, file);
            outputSinkItem.addOutputItem(file, outputFile);
            String fileUrl = file.getUrl();
            //if (joocConfig.showCompilerInfoMessages) {
            //  context.addMessage(CompilerMessageCategory.INFORMATION, "as->js (" + outputFile.getPath() + ")", fileUrl, -1, -1);
            //}
            getLog().info("as->js: " + fileUrl + " -> " + outputFile.getPath());
          }
        }
      } catch (SecurityException e) {
        String message = "Output directory " + outputDirectoryPath + " does not exist and could not be created: " + e.getMessage();
        context.addMessage(CompilerMessageCategory.ERROR, message, null, -1, -1);
        getLog().warn(message);
      }
    }
    return outputSinkItem;
  }

  protected String getOutputFileSuffix() {
    return Jooc.OUTPUT_FILE_SUFFIX;
  }
}
