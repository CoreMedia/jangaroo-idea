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

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

/**
 * Jangaroo Project Component.
 */
public class JoocProjectComponent implements ProjectComponent {
  private Project project;
  private JangarooCompiler jooc;

  public JoocProjectComponent(Project project) {
    this.project = project;
  }

  public void initComponent() {
    jooc = new JangarooCompiler();
  }

  public void disposeComponent() {
    jooc = null;
  }

  @NotNull
  public String getComponentName() {
    return "JoocProjectComponent";
  }

  public void projectOpened() {
    CompilerManager compilerManager = CompilerManager.getInstance(project);

    FileType actionscript = FileTypeManager.getInstance().getFileTypeByExtension("as");
    FileType javascript = FileTypeManager.getInstance().getFileTypeByExtension("js");

    compilerManager.addCompilableFileType(actionscript);
    compilerManager.addTranslatingCompiler(jooc,
      Collections.<FileType>singleton(actionscript),
      Collections.<FileType>singleton(javascript));
  }

  public void projectClosed() {
    CompilerManager compilerManager = CompilerManager.getInstance(project);
    compilerManager.removeCompiler(jooc);
  }
}
