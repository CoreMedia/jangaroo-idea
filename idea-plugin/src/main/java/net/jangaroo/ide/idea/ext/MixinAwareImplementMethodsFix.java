package net.jangaroo.ide.idea.ext;

import com.intellij.lang.javascript.psi.JSFunction;
import com.intellij.lang.javascript.psi.JSParameterList;
import com.intellij.lang.javascript.psi.ecmal4.JSAttributeList;
import com.intellij.lang.javascript.psi.ecmal4.JSClass;
import com.intellij.lang.javascript.validation.fixes.ImplementMethodsFix;
import com.intellij.lang.javascript.validation.fixes.JSAttributeListWrapper;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.Nullable;

/**
 * Generates method implementations so that methods of an implemented Ext Mixin interface are implemented
 * as native methods.
 */
class MixinAwareImplementMethodsFix extends ImplementMethodsFix {
  private static final String MIXIN_INTERFACE_ANNOTATION = "Mixin";

  public MixinAwareImplementMethodsFix(JSClass clazz) {
    super(clazz);
  }

  @Override
  public String buildFunctionText(JSFunction fun, @Nullable MultiMap<String, String> types) {
    String functionText = super.buildFunctionText(fun, types);
    if (isMixinFunction(fun)) {
      String apiDoc = fun.getKind() == JSFunction.FunctionKind.SETTER ? "@private" : "@inheritDoc";
      return prependApiDoc(apiDoc, functionText);
    }
    return functionText;
  }

  private static String prependApiDoc(String apiDoc, String functionText) {
    return String.format("/** %s */\n%s", apiDoc, functionText);
  }

  @Override
  protected void adjustAttributeList(JSAttributeListWrapper attributeListWrapper, JSFunction function) {
    super.adjustAttributeList(attributeListWrapper, function);
    if (isMixinFunction(function)) {
      attributeListWrapper.overrideModifier(JSAttributeList.ModifierType.NATIVE, true);
    }
  }

  @Override
  protected String buildFunctionBodyText(String retType, JSParameterList parameterList, JSFunction func) {
    return isMixinFunction(func) ? ";" : super.buildFunctionBodyText(retType, parameterList, func);
  }

  private static boolean isMixinFunction(JSFunction function) {
    PsiElement context = function.getContext();
    if (context instanceof JSClass && ((JSClass)context).isInterface()) {
      JSAttributeList attributes = ((JSClass)context).getAttributeList();
      if (attributes != null && attributes.getAttributesByName(MIXIN_INTERFACE_ANNOTATION).length > 0) {
        return true;
      }
    }
    return false;
  }
}
