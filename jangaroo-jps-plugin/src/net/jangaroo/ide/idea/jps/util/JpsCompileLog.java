package net.jangaroo.ide.idea.jps.util;

import net.jangaroo.jooc.api.CompileLog;
import net.jangaroo.jooc.api.FilePosition;
import org.jetbrains.jps.incremental.MessageHandler;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;

/**
* A Jangaroo CompileLog that logs via JPS message handler.
*/
public class JpsCompileLog implements CompileLog {
  private MessageHandler messageHandler;
  private boolean hasErrors = false;
  private String builderName;

  public JpsCompileLog(String builderName, MessageHandler messageHandler) {
    this.messageHandler = messageHandler;
    this.builderName = builderName;
  }

  private void addMessage(BuildMessage.Kind compilerMessageCategory, String msg, FilePosition position) {
    messageHandler.processMessage(new CompilerMessage(builderName, compilerMessageCategory, msg,
      position.getFileName(), 0L, 0L, 0L, (long)position.getLine(), (long)position.getColumn()));
    if (compilerMessageCategory == BuildMessage.Kind.ERROR) {
      hasErrors = true;
    }
  }

  public void error(FilePosition position, String msg) {
    addMessage(BuildMessage.Kind.ERROR, msg, position);
  }

  public void error(String msg) {
    messageHandler.processMessage(new CompilerMessage(builderName, BuildMessage.Kind.ERROR, msg));
    hasErrors = true;
  }

  public void warning(FilePosition position, String msg) {
    addMessage(BuildMessage.Kind.WARNING, msg, position);
  }

  public void warning(String msg) {
    messageHandler.processMessage(new CompilerMessage(builderName, BuildMessage.Kind.WARNING, msg));
  }

  public boolean hasErrors() {
    return hasErrors;
  }

}
