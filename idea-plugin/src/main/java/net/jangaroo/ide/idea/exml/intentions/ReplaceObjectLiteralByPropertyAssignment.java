package net.jangaroo.ide.idea.exml.intentions;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.lang.javascript.JavaScriptSupportLoader;
import com.intellij.lang.javascript.psi.JSAssignmentExpression;
import com.intellij.lang.javascript.psi.JSExpression;
import com.intellij.lang.javascript.psi.JSFunction;
import com.intellij.lang.javascript.psi.JSLocalVariable;
import com.intellij.lang.javascript.psi.JSNewExpression;
import com.intellij.lang.javascript.psi.JSObjectLiteralExpression;
import com.intellij.lang.javascript.psi.JSProperty;
import com.intellij.lang.javascript.psi.JSType;
import com.intellij.lang.javascript.psi.ecmal4.JSClass;
import com.intellij.lang.javascript.psi.ecmal4.JSQualifiedNamedElement;
import com.intellij.lang.javascript.psi.impl.JSChangeUtil;
import com.intellij.lang.javascript.psi.resolve.JSInheritanceUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
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
public class ReplaceObjectLiteralByPropertyAssignment extends PsiElementBaseIntentionAction implements IntentionAction {

  public ReplaceObjectLiteralByPropertyAssignment() {
    setText("Replace EXML config class object literals by property assignments");
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

    JSLocalVariable localVariable = PsiTreeUtil.getParentOfType(element, JSLocalVariable.class, true);
    if (localVariable != null && localVariable.getName() != null && localVariable.getType() != null) {
      JSExpression initializer = localVariable.getInitializer();
      if (initializer instanceof JSNewExpression) {
        JSExpression[] arguments = ((JSNewExpression)initializer).getArguments();
        return arguments.length == 1 && arguments[0] instanceof JSObjectLiteralExpression;
      }
    }
    return false;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement psiElement) throws IncorrectOperationException {
    JSLocalVariable localVariable = PsiTreeUtil.getParentOfType(psiElement, JSLocalVariable.class, true);
    if (localVariable == null) {
      return;
    }
    String localVariableName = localVariable.getName();
    JSType type = localVariable.getType();
    if (type == null) {
      return;
    }
    JSExpression newExpression = localVariable.getInitializer();
    if (!(newExpression instanceof JSNewExpression)) {
      return;
    }
    JSExpression[] arguments = ((JSNewExpression)newExpression).getArguments();
    if (arguments.length != 1 || !(arguments[0] instanceof JSObjectLiteralExpression)) {
      return;
    }
    JSObjectLiteralExpression objectLiteralExpression = (JSObjectLiteralExpression)arguments[0];
    JSProperty[] properties = objectLiteralExpression.getProperties();
    JSClass configClass = type.resolveClass();
    for (JSProperty property : properties) {
      String propertyName = property.getName();
      JSQualifiedNamedElement member = configClass == null ? null : JSInheritanceUtil.findMember(propertyName, configClass,
        JSInheritanceUtil.SearchedMemberType.Methods, JSFunction.FunctionKind.SETTER, true);
      String accessPattern = member == null ? "%s['%s']" : "%s.%s";
      String code = String.format(accessPattern + "=null;%n", localVariableName, propertyName);
      PsiElement propertyAssignment = JSChangeUtil.createStatementFromText(project, code, JavaScriptSupportLoader.ECMA_SCRIPT_L4).getPsi();
      JSAssignmentExpression assignment = (JSAssignmentExpression)propertyAssignment.getFirstChild();
      assignment.getROperand().replace(property.getValue());
      JSChangeUtil.doAddAfter(localVariable.getParent(), propertyAssignment, null);
    }
    arguments[0].delete();
    CodeStyleManager.getInstance(project).reformat(localVariable.getParent(), true);
  }
}
