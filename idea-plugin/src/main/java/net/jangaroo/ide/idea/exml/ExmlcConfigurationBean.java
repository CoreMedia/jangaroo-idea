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
package net.jangaroo.ide.idea.exml;

import net.jangaroo.ide.idea.util.IdeaFileUtils;

/**
 * IDEA serialization adapter of JoocConfiguration. 
 */
public class ExmlcConfigurationBean {
  private static final String DEFAULT_SOURCE_DIRECTORY = "src/main/joo";
  private static final String DEFAULT_GENERATED_SOURCES_DIRECTORY = "target/generated-sources/joo";
  private static final String DEFAULT_GENERATED_RESOURCES_DIRECTORY = "target/generated-resources/joo";

  private String compilerJarFileName;

  private String propertiesCompilerJarFileName;

  /**
   * Source directory to scan for files to compile.
   */
  private String sourceDirectory;

  /**
   * The namespace of the component suite
   */
  private String configClassPackage;

  /**
   * The namespace of the component suite
   */
  private String namespace;

  /**
   * The default namespace prefix of the component suite
   */
  private String namespacePrefix;

  /**
   * Output directory for all ActionScript3 files generated out of exml components
   */
  private String generatedSourcesDirectory;

  /**
   * The file name of the XSD Schema that will be generated for this component suite.
   */
  private String xsd;

  /**
   * The directory where to generate the XSD Schema for this component suite.
   */
  private String generatedResourcesDirectory;

  private boolean showCompilerInfoMessages;

  public ExmlcConfigurationBean() {
  }

  public void init(String outputPrefix, String moduleName) {
    if (outputPrefix != null) {
      sourceDirectory = IdeaFileUtils.toIdeaUrl(outputPrefix + "/" + DEFAULT_SOURCE_DIRECTORY);
      generatedSourcesDirectory = IdeaFileUtils.toIdeaUrl(outputPrefix + "/" + DEFAULT_GENERATED_SOURCES_DIRECTORY);
      generatedResourcesDirectory = IdeaFileUtils.toIdeaUrl(outputPrefix + "/" + DEFAULT_GENERATED_RESOURCES_DIRECTORY);
    }
    if (moduleName != null) {
      namespace = moduleName;
      namespacePrefix = moduleName;
      xsd = moduleName + ".xsd";
    }
  }

  public String getCompilerJarFileName() {
    return compilerJarFileName;
  }

  public void setCompilerJarFileName(String compilerJarFileName) {
    this.compilerJarFileName = compilerJarFileName;
  }

  public String getPropertiesCompilerJarFileName() {
    return propertiesCompilerJarFileName;
  }

  public void setPropertiesCompilerJarFileName(String propertiesCompilerJarFileName) {
    this.propertiesCompilerJarFileName = propertiesCompilerJarFileName;
  }

  public boolean isShowCompilerInfoMessages() {
    return showCompilerInfoMessages;
  }

  public void setShowCompilerInfoMessages(boolean showCompilerInfoMessages) {
    this.showCompilerInfoMessages = showCompilerInfoMessages;
  }

  public String getSourceDirectory() {
    return sourceDirectory;
  }

  public void setSourceDirectory(String sourceDirectory) {
    this.sourceDirectory = sourceDirectory;
  }

  public String getConfigClassPackage() {
    return configClassPackage;
  }

  public void setConfigClassPackage(String configClassPackage) {
    this.configClassPackage = configClassPackage;
  }

  public String getNamespace() {
    return namespace;
  }

  public void setNamespace(String namespace) {
    this.namespace = namespace;
  }

  public String getNamespacePrefix() {
    return namespacePrefix;
  }

  public void setNamespacePrefix(String namespacePrefix) {
    this.namespacePrefix = namespacePrefix;
  }

  public String getGeneratedSourcesDirectory() {
    return generatedSourcesDirectory;
  }

  public void setGeneratedSourcesDirectory(String generatedSourcesDirectory) {
    this.generatedSourcesDirectory = generatedSourcesDirectory;
  }

  public String getXsd() {
    return xsd;
  }

  public void setXsd(String xsd) {
    this.xsd = xsd;
  }

  public String getGeneratedResourcesDirectory() {
    return generatedResourcesDirectory;
  }

  public void setGeneratedResourcesDirectory(String generatedResourcesDirectory) {
    this.generatedResourcesDirectory = generatedResourcesDirectory;
  }

  public String getXsdFilename() {
    return getGeneratedResourcesDirectory() + "/" + getConfigClassPackage() + ".xsd";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ExmlcConfigurationBean that = (ExmlcConfigurationBean)o;

    if (generatedResourcesDirectory != null ? !generatedResourcesDirectory.equals(that.generatedResourcesDirectory) : that.generatedResourcesDirectory != null)
      return false;
    if (generatedSourcesDirectory != null ? !generatedSourcesDirectory.equals(that.generatedSourcesDirectory) : that.generatedSourcesDirectory != null)
      return false;
    if (namespace != null ? !namespace.equals(that.namespace) : that.namespace != null)
      return false;
    if (namespacePrefix != null ? !namespacePrefix.equals(that.namespacePrefix) : that.namespacePrefix != null)
      return false;
    if (sourceDirectory != null ? !sourceDirectory.equals(that.sourceDirectory) : that.sourceDirectory != null)
      return false;
    //noinspection SimplifiableIfStatement
    if (xsd != null ? !xsd.equals(that.xsd) : that.xsd != null) return false;

    return showCompilerInfoMessages == that.showCompilerInfoMessages;
  }

  @Override
  public int hashCode() {
    int result = sourceDirectory != null ? sourceDirectory.hashCode() : 0;
    result = 31 * result + (namespace != null ? namespace.hashCode() : 0);
    result = 31 * result + (namespacePrefix != null ? namespacePrefix.hashCode() : 0);
    result = 31 * result + (generatedSourcesDirectory != null ? generatedSourcesDirectory.hashCode() : 0);
    result = 31 * result + (xsd != null ? xsd.hashCode() : 0);
    result = 31 * result + (generatedResourcesDirectory != null ? generatedResourcesDirectory.hashCode() : 0);
    result = 31 * result + (showCompilerInfoMessages ? 1 : 0);
    return result;
  }

}
