package net.jangaroo.ide.idea.debugger;

import com.intellij.lang.javascript.ActionScriptFileType;
import com.intellij.lang.javascript.flex.debug.FlexBreakpointType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.xdebugger.breakpoints.XLineBreakpointType;
import com.jetbrains.javascript.debugger.JavaScriptDebugAware;
import org.jetbrains.annotations.Nullable;

/**
 * Make ActionScript files JavaScript debug-aware.
 */
public class JangarooJavaScriptDebugAware extends JavaScriptDebugAware {
  @Nullable
  @Override
  public LanguageFileType getFileType() {
    return ActionScriptFileType.INSTANCE;
  }

  @Nullable
  @Override
  public Class<? extends XLineBreakpointType<?>> getBreakpointTypeClass() {
    return FlexBreakpointType.class;
  }
}
