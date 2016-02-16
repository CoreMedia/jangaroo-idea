package net.jangaroo.ide.idea.ext;

import com.intellij.lang.javascript.generation.JavaScriptImplementMethodsHandler;
import com.intellij.lang.javascript.psi.ecmal4.JSClass;
import com.intellij.lang.javascript.validation.fixes.BaseCreateMethodsFix;

/**
 * Handler for generating method implementations so that methods of an implemented Ext Mixin interface are implemented
 * as native methods.
 */
public class MixinAwareImplementMethodsHandler extends JavaScriptImplementMethodsHandler {

  @Override
  protected BaseCreateMethodsFix createFix(JSClass clazz) {
    return new MixinAwareImplementMethodsFix(clazz);
  }

}
