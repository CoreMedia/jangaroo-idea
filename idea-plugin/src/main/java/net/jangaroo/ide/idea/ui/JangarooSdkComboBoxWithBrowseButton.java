package net.jangaroo.ide.idea.ui;

import com.intellij.ide.DataManager;

import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;

import com.intellij.openapi.projectRoots.ui.ProjectJdksEditor;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Icons;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JList;

import net.jangaroo.ide.idea.JangarooSdkType;
import org.jetbrains.annotations.Nullable;

import static net.jangaroo.ide.idea.JangarooSdkUtils.getSdkType;

public class JangarooSdkComboBoxWithBrowseButton extends ComboboxWithBrowseButton {
  public static final Condition<Sdk> JANGAROO_SDK = new Condition<Sdk>() {
    public boolean value(Sdk sdk) {
      return sdk != null && getSdkType(sdk) instanceof JangarooSdkType;
    }
  };

  private final Condition<Sdk> mySdkEvaluator;
  private ModuleSdk myModuleSdk = new ModuleSdk();
  private boolean myShowModuleSdk = false;

  public JangarooSdkComboBoxWithBrowseButton() {
    this(JANGAROO_SDK);
  }

  public JangarooSdkComboBoxWithBrowseButton(Condition<Sdk> sdkEvaluator) {
    mySdkEvaluator = sdkEvaluator;
    rebuildSdkListAndSelectSdk(null);

    final JComboBox sdkCombo = getComboBox();
    sdkCombo.setRenderer(new DefaultListCellRenderer() {
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if ((value instanceof JangarooSdkComboBoxWithBrowseButton.ModuleSdk)) {
          Sdk sdk = ((JangarooSdkComboBoxWithBrowseButton.ModuleSdk)value).mySdk;
          if (sdk == null) {
            if (sdkCombo.isEnabled()) {
              setText("<html><font color='red'>Module SDK [not set]</font></html>");
              setIcon(Icons.ERROR_INTRODUCTION_ICON);
            } else {
              setText("Module SDK [not set]");
              setIcon(IconLoader.getDisabledIcon(Icons.ERROR_INTRODUCTION_ICON));
            }
          } else {
            setText("Module SDK [" + sdk.getName() + "]");
            setIcon(getSdkType(((JangarooSdkComboBoxWithBrowseButton.ModuleSdk)value).mySdk).getIcon());
          }
        } else if ((value instanceof String)) {
          if (sdkCombo.isEnabled()) {
            setText("<html><font color='red'>" + value + " [Invalid]</font></html>");
            setIcon(Icons.ERROR_INTRODUCTION_ICON);
          } else {
            setText(value + " [Invalid]");
            setIcon(IconLoader.getDisabledIcon(Icons.ERROR_INTRODUCTION_ICON));
          }
        } else if ((value instanceof Sdk)) {
          setText(((Sdk)value).getName());
          setIcon(getSdkType(((Sdk)value)).getIcon());
        } else if (sdkCombo.isEnabled()) {
          setText("<html><font color='red'>[none]</font></html>");
        } else {
          setText("[none]");
        }

        return this;
      }
    });
    addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        Project project = PlatformDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext());
        if (project == null) {
          project = ProjectManager.getInstance().getDefaultProject();
        }
        ProjectJdksEditor editor = new ProjectJdksEditor(null, project, JangarooSdkComboBoxWithBrowseButton.this);
        editor.show();
        if (editor.isOK()) {
          Sdk selectedSdk = editor.getSelectedJdk();
          if (mySdkEvaluator.value(selectedSdk)) {
            rebuildSdkListAndSelectSdk(selectedSdk);
          } else {
            rebuildSdkListAndSelectSdk(null);
            if (selectedSdk != null)
              Messages.showErrorDialog(JangarooSdkComboBoxWithBrowseButton.this,
                "Jangaroo SDK " + selectedSdk.getName() + " cannot be selected.", "Select Jangaroo SDK");
          }
        }
      }
    });
  }

  private void rebuildSdkListAndSelectSdk(@Nullable Sdk selectedSdk) {
    String previousSelectedSdkName = getSelectedSdkRaw();
    List<Sdk> sdkList = new ArrayList<Sdk>();

    if (myShowModuleSdk) {
      sdkList.add(myModuleSdk.mySdk);
    }

    Sdk[] sdks = ProjectJdkTable.getInstance().getAllJdks();
    for (Sdk sdk : sdks) {
      if (mySdkEvaluator.value(sdk)) {
        sdkList.add(sdk);
      }
    }

    if (!sdkList.isEmpty()) {
      getComboBox().setModel(new DefaultComboBoxModel(ArrayUtil.toObjectArray(sdkList)));
      if (selectedSdk != null) {
        setSelectedSdkRaw(selectedSdk.getName(), false);
      } else if (previousSelectedSdkName != null)
        setSelectedSdkRaw(previousSelectedSdkName, false);
    } else {
      getComboBox().setModel(new DefaultComboBoxModel(new Object[]{null}));
    }
  }

  public void addComboboxListener(final Listener listener) {
    getComboBox().addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        listener.stateChanged();
      }
    });
    getComboBox().addPropertyChangeListener("model", new PropertyChangeListener() {
      public void propertyChange(PropertyChangeEvent evt) {
        listener.stateChanged();
      }
    });
  }

  @Nullable
  public Sdk getSelectedSdk() {
    Object selectedItem = getComboBox().getSelectedItem();

    if ((selectedItem instanceof ModuleSdk)) {
      return ((ModuleSdk)selectedItem).mySdk;
    }
    if ((selectedItem instanceof Sdk)) {
      return (Sdk)selectedItem;
    }

    return null;
  }

  public String getSelectedSdkRaw() {
    Object selectedItem = getComboBox().getSelectedItem();

    if ((selectedItem instanceof ModuleSdk)) {
      return "Module SDK";
    }
    if ((selectedItem instanceof Sdk)) {
      return ((Sdk)selectedItem).getName();
    }
    if ((selectedItem instanceof String)) {
      return (String)selectedItem;
    }

    return "";
  }

  public void setSelectedSdkRaw(String sdkName) {
    setSelectedSdkRaw(sdkName, true);
  }

  private void setSelectedSdkRaw(String sdkName, boolean addErrorItemIfSdkNotFound) {
    JComboBox combo = getComboBox();

    if ("Module SDK".equals(sdkName)) {
      combo.setSelectedItem(myModuleSdk);
      return;
    }

    for (int i = 0; i < combo.getItemCount(); i++) {
      Object item = combo.getItemAt(i);
      if (((item instanceof Sdk)) && (((Sdk)item).getName().equals(sdkName))) {
        combo.setSelectedItem(item);
        return;
      }

    }

    if (addErrorItemIfSdkNotFound) {
      List<Object> items = new ArrayList<Object>();
      items.add(sdkName);
      for (int i = 0; i < combo.getItemCount(); i++) {
        Object item = combo.getItemAt(i);
        if (!(item instanceof String)) {
          items.add(item);
        }
      }
      combo.setModel(new DefaultComboBoxModel(ArrayUtil.toObjectArray(items)));
    }
  }

  public void showModuleSdk(boolean showModuleSdk) {
    if (myShowModuleSdk != showModuleSdk) {
      myShowModuleSdk = showModuleSdk;
      Object selectedItem = getComboBox().getSelectedItem();
      rebuildSdkListAndSelectSdk(null);
      if ((selectedItem instanceof String))
        setSelectedSdkRaw((String)selectedItem, true);
    }
  }

  public void setModuleSdk(Sdk sdk) {
    if (sdk != myModuleSdk.mySdk)
      myModuleSdk.mySdk = sdk;
  }

  private static class ModuleSdk {
    private Sdk mySdk;
  }

  public static abstract interface Listener {
    public abstract void stateChanged();
  }
}
