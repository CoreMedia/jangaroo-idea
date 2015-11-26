package net.jangaroo.ide.idea.exml.intentions;

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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CodeStyleManager;
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
public class SquareBracketToDotMemberAccess extends PsiElementBaseIntentionAction implements IntentionAction {

  public SquareBracketToDotMemberAccess() {
    setText("Change square-bracket to dot member access");
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
      if ((indexExpression instanceof JSLiteralExpression) && ((JSLiteralExpression)indexExpression).isQuotedLiteral()) {
        return true;
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
      String propertyName = StringUtil.unquoteString(indexExpression.getText());
      JSReferenceExpression replacementReferenceExpression = (JSReferenceExpression)JSChangeUtil.createExpressionFromText(project, "$$$." + propertyName, JavaScriptSupportLoader.ECMA_SCRIPT_L4).getPsi();
      assert replacementReferenceExpression.getQualifier() != null;
      replacementReferenceExpression.getQualifier().replace(qualifier);
      reformatElement = indexedPropertyAccessExpression.replace(replacementReferenceExpression);
    }
    if (reformatElement != null) {
      CodeStyleManager.getInstance(project).reformat(reformatElement, true);
    }
  }
}
