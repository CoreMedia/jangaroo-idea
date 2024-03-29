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
package net.jangaroo.ide.idea.ui;

import net.jangaroo.ide.idea.jps.JoocConfigurationBean;

import javax.swing.*;

import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import net.jangaroo.jooc.config.PublicApiViolationsMode;

import static net.jangaroo.ide.idea.jps.util.IdeaFileUtils.toIdeaUrl;
import static net.jangaroo.ide.idea.jps.util.IdeaFileUtils.toPath;

//import com.intellij.openapi.fileChooser.FileChooserFactory;
//import com.intellij.openapi.fileChooser.FileTextField;
//import com.intellij.openapi.Disposable;


/**
 * Jangaroo configuration on its Facet Tab.
 */
public class JangarooFacetEditorTabUI {
  private JCheckBox verboseCheckBox;
  private JPanel rootComponent;
  private JCheckBox enableAssertionsCheckBox;
  private ButtonGroup whiteSpaceButtonGroup;
  private JRadioButton keepDebugSourceRadioButton;
  private JRadioButton keepNewLinesOnlyRadioButton;
  private JRadioButton suppressWhiteSpaceRadioButton;
  private JCheckBox allowDuplicateVariableCheckBox;
  private TextFieldWithBrowseButton outputDirTextField;
  private TextFieldWithBrowseButton apiOutputDirTextField;
  private TextFieldWithBrowseButton testOutputDirTextField;
  private JCheckBox showCompilerInfoMessages;
  private JangarooSdkComboBoxWithBrowseButton jangarooSdkComboBoxWithBrowseButton;
  private ButtonGroup publicApiViolationsButtonGroup;
  private JRadioButton publicApiViolationsErrorRadioButton;
  private JRadioButton publicApiViolationsWarnRadioButton;
  private JRadioButton publicApiViolationsAllowRadioButton;
  private JTextField extNamespaceTextField;
  private JTextField extSassNamespaceTextField;

  private static final FileChooserDescriptor COMPILER_JAR_CHOOSER_DESCRIPTOR = FileChooserDescriptorFactory.createSingleLocalFileDescriptor();
  private static final FileChooserDescriptor OUTPUT_DIRECTORY_CHOOSER_DESCRIPTOR = FileChooserDescriptorFactory.createSingleFolderDescriptor();
  private static final FileChooserDescriptor API_OUTPUT_DIRECTORY_CHOOSER_DESCRIPTOR = FileChooserDescriptorFactory.createSingleFolderDescriptor();
  private static final FileChooserDescriptor TEST_OUTPUT_DIRECTORY_CHOOSER_DESCRIPTOR = FileChooserDescriptorFactory.createSingleFolderDescriptor();

  static {
    COMPILER_JAR_CHOOSER_DESCRIPTOR.setTitle("Choose Jangaroo Compiler JAR Location.");
    COMPILER_JAR_CHOOSER_DESCRIPTOR.setDescription("Choose the file location of the Jangaroo compiler JAR. This allows to use different versions of the Jangaroo compiler (0.9 and up) with the same Jangaroo IDEA plugin.");
    OUTPUT_DIRECTORY_CHOOSER_DESCRIPTOR.setTitle("Choose Jangaroo Output Directory");
    OUTPUT_DIRECTORY_CHOOSER_DESCRIPTOR.setDescription("Choose the directory where Jangaroo should place JavaScript files containing compiled ActionScript classes.");
    API_OUTPUT_DIRECTORY_CHOOSER_DESCRIPTOR.setTitle("Choose Jangaroo API Output Directory");
    API_OUTPUT_DIRECTORY_CHOOSER_DESCRIPTOR.setDescription("Choose the directory where Jangaroo should place generated ActionScript API files.");
    TEST_OUTPUT_DIRECTORY_CHOOSER_DESCRIPTOR.setTitle("Choose Jangaroo Test Output Directory");
    TEST_OUTPUT_DIRECTORY_CHOOSER_DESCRIPTOR.setDescription("Choose the directory where Jangaroo should place JavaScript files containing compiled ActionScript test classes.");
  }

  public JangarooFacetEditorTabUI() {
    outputDirTextField.addBrowseFolderListener(null,null, null, OUTPUT_DIRECTORY_CHOOSER_DESCRIPTOR,
      TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT);
    apiOutputDirTextField.addBrowseFolderListener(null,null, null, API_OUTPUT_DIRECTORY_CHOOSER_DESCRIPTOR,
      TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT);
    testOutputDirTextField.addBrowseFolderListener(null,null, null, TEST_OUTPUT_DIRECTORY_CHOOSER_DESCRIPTOR,
      TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT);
  }

  public JPanel getRootComponent() {
    return rootComponent;
  }

/*
  // Reactivate when I found out how to deal with Disposables:
  private void createUIComponents() {
    FileTextField outputDirFileTextField = FileChooserFactory.getInstance().createFileTextField(OUTPUT_DIRECTORY_CHOOSER_DESCRIPTOR, new Disposable() {
      public void dispose() {
        // ignore
      }
    });
    //Disposer.register(this, (Disposable)outputDirFileTextField);
    outputDirTextField = new TextFieldWithBrowseButton(outputDirFileTextField.getField());
    outputDirTextField.addBrowseFolderListener(null,null, null, OUTPUT_DIRECTORY_CHOOSER_DESCRIPTOR,
      TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT);

    FileTextField mergedOutputFileFileTextField = FileChooserFactory.getInstance().createFileTextField(MERGED_OUTPUT_FILE_CHOOSER_DESCRIPTOR, new Disposable() {
      public void dispose() {
        // ignore
      }
    });
    mergedOutputFileTextField = new TextFieldWithBrowseButton(mergedOutputFileFileTextField.getField());
    mergedOutputFileTextField.addBrowseFolderListener(null, null, null, MERGED_OUTPUT_FILE_CHOOSER_DESCRIPTOR,
      TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT);
  }
  */

  public void setData(JoocConfigurationBean data) {
    jangarooSdkComboBoxWithBrowseButton.setSelectedSdkRaw(data.jangarooSdkName);
    verboseCheckBox.setSelected(data.verbose);
    enableAssertionsCheckBox.setSelected(data.enableAssertions);
    whiteSpaceButtonGroup.setSelected(
      (   data.isDebugSource() ? keepDebugSourceRadioButton
        : data.isDebugLines()  ? keepNewLinesOnlyRadioButton
        : suppressWhiteSpaceRadioButton).getModel(), true);
    allowDuplicateVariableCheckBox.setSelected(data.allowDuplicateLocalVariables);
    outputDirTextField.setText(toPath(data.outputDirectory));
    apiOutputDirTextField.setText(toPath(data.apiOutputDirectory));
    testOutputDirTextField.setText(toPath(data.testOutputDirectory));
    showCompilerInfoMessages.setSelected(data.showCompilerInfoMessages);
    publicApiViolationsButtonGroup.setSelected(
      (   data.publicApiViolationsMode == PublicApiViolationsMode.ERROR ? publicApiViolationsErrorRadioButton
        : data.publicApiViolationsMode == PublicApiViolationsMode.WARN ? publicApiViolationsWarnRadioButton
        : publicApiViolationsAllowRadioButton).getModel(), true);
    extNamespaceTextField.setText(data.extNamespace);
    extSassNamespaceTextField.setText(data.extSassNamespace);
  }

  public JoocConfigurationBean getData(JoocConfigurationBean data) {
    data.jangarooSdkName = jangarooSdkComboBoxWithBrowseButton.getSelectedSdkRaw();
    data.verbose = verboseCheckBox.isSelected();
    data.enableAssertions = enableAssertionsCheckBox.isSelected();
    ButtonModel debugSelection = whiteSpaceButtonGroup.getSelection();
    data.debugLevel =
        keepDebugSourceRadioButton. getModel().equals(debugSelection) ? JoocConfigurationBean.DEBUG_LEVEL_SOURCE
      : keepNewLinesOnlyRadioButton.getModel().equals(debugSelection) ? JoocConfigurationBean.DEBUG_LEVEL_LINES
                                                                      : JoocConfigurationBean.DEBUG_LEVEL_NONE;
    data.allowDuplicateLocalVariables = allowDuplicateVariableCheckBox.isSelected();
    data.outputDirectory = toIdeaUrl(outputDirTextField.getText());
    data.apiOutputDirectory = toIdeaUrl(apiOutputDirTextField.getText());
    data.testOutputDirectory = toIdeaUrl(testOutputDirTextField.getText());
    data.showCompilerInfoMessages = showCompilerInfoMessages.isSelected();
    ButtonModel publicApiViolationsSelection = publicApiViolationsButtonGroup.getSelection();
    data.publicApiViolationsMode =
        publicApiViolationsErrorRadioButton.getModel().equals(publicApiViolationsSelection) ? PublicApiViolationsMode.ERROR
      : publicApiViolationsWarnRadioButton .getModel().equals(publicApiViolationsSelection) ? PublicApiViolationsMode.WARN
                                                                                            : PublicApiViolationsMode.ALLOW;
    data.extNamespace = extNamespaceTextField.getText();
    data.extSassNamespace = extSassNamespaceTextField.getText();
    return data;
  }

  public boolean isModified(JoocConfigurationBean data) {
    return !getData(new JoocConfigurationBean()).equals(data);
  }

}
