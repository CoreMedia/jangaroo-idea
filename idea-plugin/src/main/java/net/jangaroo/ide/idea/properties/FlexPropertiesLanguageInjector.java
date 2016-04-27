package net.jangaroo.ide.idea.properties;

import com.intellij.lang.javascript.JavaScriptSupportLoader;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.InjectedLanguagePlaces;
import com.intellij.psi.LanguageInjector;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

/**
 * Inject annotations like Embed('/resources/image.png') into Flex properties files.
 */
public class FlexPropertiesLanguageInjector implements LanguageInjector {

  private static final Pattern ANNOTATION_PATTERN = Pattern.compile(
    "\\s*[A-Z][a-zA-Z0-9_]*\\(.*"
  );

  @Override
  public void getLanguagesToInject(@NotNull PsiLanguageInjectionHost psiLanguageInjectionHost,
                                   @NotNull InjectedLanguagePlaces injectedLanguagePlaces) {
    PsiFile psiFile = psiLanguageInjectionHost.getContainingFile();
    if (psiFile instanceof PropertiesFile &&
      psiLanguageInjectionHost instanceof IProperty) {
      String value = ((IProperty)psiLanguageInjectionHost).getValue();
      if (value != null && ANNOTATION_PATTERN.matcher(value).matches()) {
        PsiElement valueElement = psiLanguageInjectionHost.getLastChild();
        injectedLanguagePlaces.addPlace(JavaScriptSupportLoader.ECMA_SCRIPT_L4,
          TextRange.from(valueElement.getStartOffsetInParent(), valueElement.getTextLength()), "[", "]");
      }
    }
  }
}
