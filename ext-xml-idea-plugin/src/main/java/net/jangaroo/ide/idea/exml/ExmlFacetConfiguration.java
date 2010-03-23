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

import com.intellij.facet.FacetConfiguration;
import com.intellij.facet.ui.FacetEditorContext;
import com.intellij.facet.ui.FacetEditorTab;
import com.intellij.facet.ui.FacetValidatorsManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

/**
 * EXML IDEA Facet configuration, so far empty.
 */
public class ExmlFacetConfiguration implements FacetConfiguration, PersistentStateComponent<ExmlcConfigurationBean> {

  private ExmlcConfigurationBean exmlcConfigurationBean = new ExmlcConfigurationBean();

  public FacetEditorTab[] createEditorTabs(FacetEditorContext facetEditorContext, FacetValidatorsManager facetValidatorsManager) {
    return new FacetEditorTab[]{ new ExmlFacetEditorTab(initExmlcConfigurationBean(facetEditorContext))};
  }

  private synchronized ExmlcConfigurationBean initExmlcConfigurationBean(@NotNull FacetEditorContext facetEditorContext) {
    if (exmlcConfigurationBean.getXsd() == null) {
      Module module = facetEditorContext.getModule();
      ModuleRootModel rootModel = facetEditorContext.getRootModel();
      VirtualFile[] contentRoots = rootModel.getContentRoots();
      if (contentRoots.length > 0) {
        exmlcConfigurationBean.init(contentRoots[0].getPath(), module.getName());
      }
    }
    return exmlcConfigurationBean;
  }

  public void readExternal(Element element) throws InvalidDataException {
    // ignore
  }

  public void writeExternal(Element element) throws WriteExternalException {
    // ignore
  }

  public ExmlcConfigurationBean getState() {
    return exmlcConfigurationBean;
  }

  public void loadState(ExmlcConfigurationBean state) {
    // copy into existing instance, as it may already be used by some JangarooFacetEditorTab:
    XmlSerializerUtil.copyBean(state, exmlcConfigurationBean);
  }
}