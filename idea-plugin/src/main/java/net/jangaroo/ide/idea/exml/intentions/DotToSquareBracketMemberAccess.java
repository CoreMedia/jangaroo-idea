package net.jangaroo.ide.idea.exml.intentions;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.lang.javascript.JavaScriptSupportLoader;
import com.intellij.lang.javascript.psi.JSExpression;
import com.intellij.lang.javascript.psi.JSIndexedPropertyAccessExpression;
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
public class DotToSquareBracketMemberAccess extends PsiElementBaseIntentionAction implements IntentionAction {

  public DotToSquareBracketMemberAccess() {
    setText("Change dot to square-bracket member access");
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

    JSReferenceExpression referenceExpression = PsiTreeUtil.getParentOfType(element, JSReferenceExpression.class);
    if (referenceExpression != null) {
      JSExpression qualifier = referenceExpression.getQualifier();
      PsiElement referenceNameElement = referenceExpression.getReferenceNameElement();
      if (qualifier != null && referenceNameElement instanceof LeafPsiElement) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    PsiElement reformatElement = null;
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
    if (reformatElement != null) {
      CodeStyleManager.getInstance(project).reformat(reformatElement, true);
    }
  }
}
