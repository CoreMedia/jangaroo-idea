package net.jangaroo.ide.idea.exml.migration;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Ordering;
import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.wm.impl.status.StatusBarUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.listeners.RefactoringListenerManager;
import com.intellij.refactoring.listeners.impl.RefactoringListenerManagerImpl;
import com.intellij.refactoring.listeners.impl.RefactoringTransaction;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.SequentialModalProgressTask;
import com.intellij.util.SequentialTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Abstract {@link BaseRefactoringProcessor} that shows a progress bar when refactoring the code.
 *
 * <p>It does not hold a write lock for the complete refactoring but splits up the work and acquires the write lock
 * once for each changed file. This way it can update a progress bar between processing files despite
 * <a href="https://youtrack.jetbrains.com/issue/IDEABKL-2756">IDEABKL-2756</a>.
 */
public abstract class SequentialRefactoringProcessor extends BaseRefactoringProcessor {

  private static final Logger LOG = Logger.getInstance(SequentialRefactoringProcessor.class);

  private RefactoringTransaction myTransaction;

  protected SequentialRefactoringProcessor(@NotNull Project project) {
    super(project);
  }

  @Override
  protected RefactoringTransaction getTransaction() {
    return myTransaction;
  }

  @Nullable
  @Override
  protected final String getRefactoringId() {
    // to keep things simple, overwritten methods do not handle non-null refactoring IDs as the superclass does
    return null;
  }

  @Override
  protected final void performPsiSpoilingRefactoring() {
    // to keeps things simple, overwritten methods do not call this method as the superclass does
  }

  protected final void performRefactoring(@NotNull UsageInfo[] usageInfos) {
    // unused. #performRefactoringIteration(PsiFile, UsageInfo[]) is called instead
  }

  protected void execute(@NotNull final UsageInfo[] usages) {
    CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
      @Override
      public void run() {
        Collection<UsageInfo> usageInfos = new LinkedHashSet<UsageInfo>(Arrays.asList(usages));
        doRefactoring(usageInfos);
        if (isGlobalUndoAction()) CommandProcessor.getInstance().markCurrentCommandAsGlobal(myProject);
      }
    }, getCommandName(), null, getUndoConfirmationPolicy());
  }

  private void doRefactoring(@NotNull final Collection<UsageInfo> usageInfoSet) {
    for (Iterator<UsageInfo> iterator = usageInfoSet.iterator(); iterator.hasNext();) {
      UsageInfo usageInfo = iterator.next();
      final PsiElement element = usageInfo.getElement();
      if (element == null || !isToBeChanged(usageInfo)) {
        iterator.remove();
      }
    }

    final UsageInfo[] writableUsageInfos = usageInfoSet.toArray(new UsageInfo[usageInfoSet.size()]);
    if (writableUsageInfos.length == 0) {
      if (!isPreviewUsages(writableUsageInfos)) {
        StatusBarUtil.setStatusBarInfo(myProject, RefactoringBundle.message("statusBar.noUsages"));
      }
      return;
    }

    LocalHistoryAction action = LocalHistory.getInstance().startAction(getCommandName());
    try {
      PsiDocumentManager.getInstance(myProject).commitAllDocuments();
      RefactoringListenerManager instance = RefactoringListenerManager.getInstance(myProject);
      myTransaction = ((RefactoringListenerManagerImpl)instance).startTransaction();
      doRefactoring(writableUsageInfos);
      myTransaction.commit();
    } finally {
      action.finish();
    }
  }

  private void doRefactoring(final UsageInfo[] writableUsageInfos) {
    // this code follows the pattern used in com.intellij.codeInsight.actions.AbstractLayoutCodeProcessor
    // to display a progress bar for a sequence of write actions
    // note that Idea cannot show a progress bar for a single write action, see IDEABKL-2756

    final List<UsagesInFile> usagesInFiles = getUsagesInFiles(writableUsageInfos);

    final ProgressWindow progressWindow = new ProgressWindow(true, myProject);
    progressWindow.setTitle(getCommandName());
    progressWindow.setText("Migrating");
    final ModalityState modalityState = ModalityState.current();

    Runnable runnable = new Runnable() {
      @Override
      public void run() {
        final Runnable writeRunnable = new Runnable() {
          @Override
          public void run() {
            CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
              @Override
              public void run() {
                CommandProcessor.getInstance().markCurrentCommandAsGlobal(myProject);
                try {
                  SequentialModalProgressTask progressTask = new SequentialModalProgressTask(myProject, getCommandName());
                  progressTask.setMinIterationTime(100);
                  SequentialRefactoringTask refactoringTask = new SequentialRefactoringTask(progressTask, usagesInFiles);
                  progressTask.setTask(refactoringTask);
                  startRefactoring(writableUsageInfos);
                  try {
                    ProgressManager.getInstance().run(progressTask);
                  } finally {
                    endRefactoring();
                  }
                  StatusBarUtil.setStatusBarInfo(myProject,
                    RefactoringBundle.message("statusBar.refactoring.result", writableUsageInfos.length));
                } catch (IndexNotReadyException ignored) {
                }
              }
            }, getCommandName(), null);
          }
        };


        ApplicationManager.getApplication().invokeLater(writeRunnable, modalityState, myProject.getDisposed());
      }
    };

    ApplicationManager.getApplication().executeOnPooledThread(runnable);
  }

  @NotNull
  private List<UsagesInFile> getUsagesInFiles(UsageInfo[] writableUsageInfos) {
    final List<UsagesInFile> usagesInFiles = new ArrayList<UsagesInFile>();
    ImmutableMap<PsiFile, Collection<UsageInfo>> usageInfosByFile = Multimaps.index(
      ImmutableList.copyOf(writableUsageInfos), new Function<UsageInfo, PsiFile>() {
        @Override
        public PsiFile apply(UsageInfo usageInfo) {
          return usageInfo.getFile();
        }
      }).asMap();
    for (Map.Entry<PsiFile, Collection<UsageInfo>> entry : usageInfosByFile.entrySet()) {
      usagesInFiles.add(new UsagesInFile(entry.getKey(), entry.getValue()));
    }
    Collections.sort(usagesInFiles, getRefactoringIterationComparator());
    return usagesInFiles;
  }

  protected Comparator<? super UsagesInFile> getRefactoringIterationComparator() {
    return Ordering.allEqual();
  }

  protected abstract void startRefactoring(UsageInfo[] usageInfos);

  protected abstract void performRefactoringIteration(UsagesInFile usagesInFile);

  protected abstract void endRefactoring();


  private class SequentialRefactoringTask implements SequentialTask {
    private final SequentialModalProgressTask compositeTask;
    private final List<UsagesInFile> usagesInFileList;

    private int processed;
    private boolean stop;

    public SequentialRefactoringTask(SequentialModalProgressTask compositeTask, List<UsagesInFile> usagesInFileList) {
      this.compositeTask = compositeTask;
      this.usagesInFileList = usagesInFileList;
    }

    @Override
    public void prepare() {
    }

    @Override
    public boolean isDone() {
      return stop || processed >= usagesInFileList.size();
    }

    @Override
    public boolean iteration() {
      if (isDone()) {
        return true;
      }


      final UsagesInFile usagesInFile = usagesInFileList.get(processed);
      final PsiFile file = usagesInFile.getFile();
      final Collection<UsageInfo> usages = usagesInFile.getUsages();

      String displayFileName = ProjectUtil.calcRelativeToProjectPath(file.getVirtualFile(), myProject);
      LOG.info("Processing " + displayFileName + " (" + usages.size() + " usages)");
      updateIndicatorText("Processing...", displayFileName);
      updateIndicatorFraction(processed);
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          performRefactoringIteration(usagesInFile);
        }
      });

      ++processed;
      return !isDone();
    }

    private void updateIndicatorFraction(int processed) {
      ProgressIndicator indicator = compositeTask.getIndicator();
      if (indicator != null) {
        indicator.setFraction((double)processed / usagesInFileList.size());
      }
    }

    private void updateIndicatorText(@NotNull String upperLabel, @NotNull String downLabel) {
      ProgressIndicator indicator = compositeTask.getIndicator();
      if (indicator != null) {
        indicator.setText(upperLabel);
        indicator.setText2(downLabel);
      }
    }

    @Override
    public void stop() {
      stop = true;
    }
  }

  protected static class UsagesInFile {
    private final PsiFile file;
    private final Collection<UsageInfo> usages;

    public UsagesInFile(PsiFile file, Collection<UsageInfo> usages) {
      this.file = file;
      this.usages = usages;
    }

    public PsiFile getFile() {
      return file;
    }

    public Collection<UsageInfo> getUsages() {
      return usages;
    }
  }

}
