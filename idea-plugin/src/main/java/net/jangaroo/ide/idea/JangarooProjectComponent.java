package net.jangaroo.ide.idea;

import com.intellij.compiler.server.CustomBuilderMessageHandler;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBusConnection;
import net.jangaroo.ide.idea.jps.JangarooBuilder;

import java.io.File;

/**
 * Listen for source-generating-compiler's file invalidation messages.
 */
public class JangarooProjectComponent extends AbstractProjectComponent {

  private MessageBusConnection messageBusConnection;

  public JangarooProjectComponent(Project project) {
    super(project);
  }

  @Override
  public void initComponent() {
    super.initComponent();
    messageBusConnection = myProject.getMessageBus().connect();
    messageBusConnection.subscribe(CustomBuilderMessageHandler.TOPIC, new FileInvalidationListener());
  }

  @Override
  public void disposeComponent() {
    messageBusConnection.disconnect();
    super.disposeComponent();
  }

  private class FileInvalidationListener implements CustomBuilderMessageHandler {
    @Override
    public void messageReceived(String builderId, String messageType, String messageText) {
      if (JangarooBuilder.BUILDER_NAME.equals(builderId) &&
        JangarooBuilder.FILE_INVALIDATION_BUILDER_MESSAGE.equals(messageType)) {
        VirtualFile virtualFile = VfsUtil.findFileByIoFile(new File(messageText), true);
        if (virtualFile != null) {
          virtualFile.refresh(true, false);
        }
      }
    }
  }
}
