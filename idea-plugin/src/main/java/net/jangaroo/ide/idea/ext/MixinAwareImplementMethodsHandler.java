package net.jangaroo.ide.idea.ext;

import com.intellij.lang.javascript.generation.JavaScriptImplementMethodsHandlerForFlex;
import com.intellij.lang.javascript.psi.ecmal4.JSClass;
import com.intellij.lang.javascript.validation.fixes.BaseCreateMembersFix;
import com.intellij.psi.PsiElement;

/**
 * Handler for generating method implementations so that methods of an implemented Ext Mixin interface are implemented
 * as native methods.
 */
public class MixinAwareImplementMethodsHandler extends JavaScriptImplementMethodsHandlerForFlex {

  @Override
  protected BaseCreateMembersFix createFix(PsiElement clazz) {
    return new MixinAwareImplementMethodsFix((JSClass)clazz);
  }

}
