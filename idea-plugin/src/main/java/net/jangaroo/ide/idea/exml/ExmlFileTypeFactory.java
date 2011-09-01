package net.jangaroo.ide.idea.exml;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.fileTypes.FileTypeConsumer;
import com.intellij.openapi.fileTypes.FileTypeFactory;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA. User: fwienber Date: 01.09.11 Time: 00:37 To change this template use File | Settings |
 * File Templates.
 */
public class ExmlFileTypeFactory extends FileTypeFactory {

  @Override
  public void createFileTypes(@NotNull FileTypeConsumer consumer) {
    consumer.consume(XmlFileType.INSTANCE, "exml");
  }
}
