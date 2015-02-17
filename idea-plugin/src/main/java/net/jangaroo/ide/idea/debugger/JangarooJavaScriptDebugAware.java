package net.jangaroo.ide.idea.debugger;

import com.intellij.lang.javascript.ActionScriptFileType;
import com.intellij.openapi.fileTypes.FileType;
import com.jetbrains.javascript.debugger.JavaScriptDebugAware;
import org.jetbrains.annotations.Nullable;

/**
 * Make ActionScript files JavaScript debug-aware.
 */
public class JangarooJavaScriptDebugAware extends JavaScriptDebugAware {
  @Nullable
  @Override
  public FileType getFileType() {
    return ActionScriptFileType.INSTANCE;
  }
}
