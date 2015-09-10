package net.jangaroo.ide.idea.exml;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.lang.javascript.JavaScriptSupportLoader;
import com.intellij.lang.javascript.psi.JSExpression;
import com.intellij.lang.javascript.psi.JSIndexedPropertyAccessExpression;
import com.intellij.lang.javascript.psi.JSLiteralExpression;
import com.intellij.lang.javascript.psi.JSReferenceExpression;
import com.intellij.lang.javascript.psi.impl.JSChangeUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * This intention replaces an invocation of an EXML config class constructor using an object literal
 * by a no-arg constructor invocation, followed by assigning each property separately.
 * The advantage is that in contrast to the object literal, the usage of properties is type-safe
 * and can be refactored.
 */
public class ToggleUnTypedPropertyAccess extends PsiElementBaseIntentionAction implements IntentionAction {

  public ToggleUnTypedPropertyAccess() {
    setText("Toggle between typed (obj.prop) and untyped (obj['prop']) property access.");
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    if (!element.isWritable()) return false;

    JSIndexedPropertyAccessExpression indexedPropertyAccessExpression = PsiTreeUtil.getParentOfType(element, JSIndexedPropertyAccessExpression.class, true);
    if (indexedPropertyAccessExpression != null) {
      JSExpression indexExpression = indexedPropertyAccessExpression.getIndexExpression();
      if ((indexExpression instanceof JSLiteralExpression) && ((JSLiteralExpression)indexExpression).getValue() instanceof String) {
        return true;
      }
    } else {
      JSReferenceExpression referenceExpression = PsiTreeUtil.getParentOfType(element, JSReferenceExpression.class);
      if (referenceExpression != null) {
        PsiElement referenceNameElement = referenceExpression.getReferenceNameElement();
        if (referenceNameElement instanceof LeafPsiElement) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    PsiElement reformatElement = null;
    JSIndexedPropertyAccessExpression indexedPropertyAccessExpression = PsiTreeUtil.getParentOfType(element, JSIndexedPropertyAccessExpression.class, true);
    if (indexedPropertyAccessExpression != null) {
      JSExpression qualifier = indexedPropertyAccessExpression.getQualifier();
      JSLiteralExpression indexExpression = (JSLiteralExpression)indexedPropertyAccessExpression.getIndexExpression();
      String propertyName = (String)indexExpression.getValue();
      JSReferenceExpression replacementReferenceExpression = (JSReferenceExpression)JSChangeUtil.createExpressionFromText(project, "$$$." + propertyName, JavaScriptSupportLoader.ECMA_SCRIPT_L4).getPsi();
      assert replacementReferenceExpression.getQualifier() != null;
      replacementReferenceExpression.getQualifier().replace(qualifier);
      reformatElement = indexedPropertyAccessExpression.replace(replacementReferenceExpression);
    } else {
      JSReferenceExpression referenceExpression = PsiTreeUtil.getParentOfType(element, JSReferenceExpression.class);
      if (referenceExpression != null) {
        JSExpression qualifier = referenceExpression.getQualifier();
        PsiElement propertyName = referenceExpression.getReferenceNameElement();
        if (propertyName != null) {
          JSIndexedPropertyAccessExpression replacementIndexedPropertyAccessExpression = (JSIndexedPropertyAccessExpression)JSChangeUtil.createExpressionFromText(project, "$$$['" + propertyName.getText() + "']", JavaScriptSupportLoader.ECMA_SCRIPT_L4).getPsi();
          assert replacementIndexedPropertyAccessExpression.getQualifier() != null;
          replacementIndexedPropertyAccessExpression.getQualifier().replace(qualifier);
          reformatElement = referenceExpression.replace(replacementIndexedPropertyAccessExpression);
        }
      }
    }
    if (reformatElement != null) {
      CodeStyleManager.getInstance(project).reformat(reformatElement, true);
    }
  }
}
