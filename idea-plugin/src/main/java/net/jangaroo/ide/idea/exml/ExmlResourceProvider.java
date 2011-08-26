package net.jangaroo.ide.idea.exml;

import com.intellij.javaee.ResourceRegistrar;
import com.intellij.javaee.StandardResourceProvider;
import net.jangaroo.exml.ExmlConstants;

/**
 * Adds XML Schema for EXML 0.1.
 */
public class ExmlResourceProvider implements StandardResourceProvider {

  public void registerResources(ResourceRegistrar resourceRegistrar) {
    resourceRegistrar.addStdResource(ExmlConstants.EXML_NAMESPACE_URI, ExmlConstants.EXML_SCHEMA_LOCATION, getClass());
    resourceRegistrar.addStdResource(ExmlConstants.EXML_UNTYPED_NAMESPACE_URI, ExmlConstants.EXML_UNTYPED_SCHEMA_LOCATION, getClass());
  }

}
