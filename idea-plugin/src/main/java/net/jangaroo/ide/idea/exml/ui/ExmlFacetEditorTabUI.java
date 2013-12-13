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
package net.jangaroo.ide.idea.exml.ui;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import net.jangaroo.exml.config.ValidationMode;
import net.jangaroo.ide.idea.exml.ExmlcConfigurationBean;

import javax.swing.*;

import static net.jangaroo.ide.idea.jps.util.IdeaFileUtils.toIdeaUrl;
import static net.jangaroo.ide.idea.jps.util.IdeaFileUtils.toPath;

/**
 * Jangaroo configuration on its Facet Tab.
 */
public class ExmlFacetEditorTabUI {
  private JPanel rootComponent;
  private TextFieldWithBrowseButton generatedSourcesDirTextField;
  private TextFieldWithBrowseButton generatedTestSourcesDirTextField;
  private TextFieldWithBrowseButton generatedResourcesDirTextField;
  private JCheckBox showCompilerInfoMessages;
  private JTextField configClassesPackageTextField;
  private ButtonGroup validationModeButtonGroup;
  private JRadioButton validationModeErrorRadioButton;
  private JRadioButton validationModeWarnRadioButton;
  private JRadioButton validationModeOffRadioButton;

  private static final FileChooserDescriptor GENERATED_SOURCE_DIRECTORY_CHOOSER_DESCRIPTOR = FileChooserDescriptorFactory.createSingleFolderDescriptor();
  private static final FileChooserDescriptor GENERATED_TEST_SOURCE_DIRECTORY_CHOOSER_DESCRIPTOR = FileChooserDescriptorFactory.createSingleFolderDescriptor();
  private static final FileChooserDescriptor GENERATED_RESOURCE_DIRECTORY_CHOOSER_DESCRIPTOR = FileChooserDescriptorFactory.createSingleFolderDescriptor();

  static {
    GENERATED_SOURCE_DIRECTORY_CHOOSER_DESCRIPTOR.setTitle("Choose EXML Generated Sources Directory");
    GENERATED_SOURCE_DIRECTORY_CHOOSER_DESCRIPTOR.setDescription("Choose the directory where EXML should store the *.as files created from *.exml. "+
      "This should be a source directory, so that the Jangaroo Language plugin finds and compiles these classes.");
    GENERATED_TEST_SOURCE_DIRECTORY_CHOOSER_DESCRIPTOR.setTitle("Choose EXML Generated Test Sources Directory");
    GENERATED_TEST_SOURCE_DIRECTORY_CHOOSER_DESCRIPTOR.setDescription("Choose the directory where EXML should store the *.as files created from test *.exml. "+
      "This should be a test source directory, so that the Jangaroo Language plugin finds and compiles these classes.");
    GENERATED_RESOURCE_DIRECTORY_CHOOSER_DESCRIPTOR.setTitle("Choose EXML Schema Directory");
    GENERATED_RESOURCE_DIRECTORY_CHOOSER_DESCRIPTOR.setDescription("Choose the directory where EXML should store the generated *.xsd file.");
  }

  public ExmlFacetEditorTabUI() {
    generatedSourcesDirTextField.addBrowseFolderListener(null,null, null, GENERATED_SOURCE_DIRECTORY_CHOOSER_DESCRIPTOR,
      TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT);
    generatedTestSourcesDirTextField.addBrowseFolderListener(null,null, null, GENERATED_TEST_SOURCE_DIRECTORY_CHOOSER_DESCRIPTOR,
      TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT);
    generatedResourcesDirTextField.addBrowseFolderListener(null,null, null, GENERATED_RESOURCE_DIRECTORY_CHOOSER_DESCRIPTOR,
      TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT);
  }

  public JPanel getRootComponent() {
    return rootComponent;
  }

  public void setData(ExmlcConfigurationBean data) {
    generatedSourcesDirTextField.setText(toPath(data.getGeneratedSourcesDirectory()));
    generatedTestSourcesDirTextField.setText(toPath(data.getGeneratedTestSourcesDirectory()));
    generatedResourcesDirTextField.setText(toPath(data.getGeneratedResourcesDirectory()));
    configClassesPackageTextField.setText(data.getConfigClassPackage());
    showCompilerInfoMessages.setSelected(data.isShowCompilerInfoMessages());
    validationModeButtonGroup.setSelected(
      (data.validationMode == ValidationMode.ERROR ? validationModeErrorRadioButton
        : data.validationMode == ValidationMode.WARN ? validationModeWarnRadioButton
        : validationModeOffRadioButton).getModel(), true);
  }

  public ExmlcConfigurationBean getData(ExmlcConfigurationBean data) {
    data.setGeneratedSourcesDirectory(toIdeaUrl(generatedSourcesDirTextField.getText()));
    data.setGeneratedTestSourcesDirectory(toIdeaUrl(generatedTestSourcesDirTextField.getText()));
    data.setGeneratedResourcesDirectory(toIdeaUrl(generatedResourcesDirTextField.getText()));
    data.setConfigClassPackage(configClassesPackageTextField.getText());
    data.setShowCompilerInfoMessages(showCompilerInfoMessages.isSelected());
    ButtonModel validationModeSelection = validationModeButtonGroup.getSelection();
    data.validationMode =
        validationModeErrorRadioButton.getModel().equals(validationModeSelection) ? ValidationMode.ERROR
      : validationModeWarnRadioButton .getModel().equals(validationModeSelection) ? ValidationMode.WARN
                                                                                  : ValidationMode.OFF;
    return data;
  }

  public boolean isModified(ExmlcConfigurationBean data) {
    return !getData(new ExmlcConfigurationBean()).equals(data);
  }

  private void createUIComponents() {
    // you can place custom component creation code here 
  }
}