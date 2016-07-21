package net.jangaroo.ide.idea.exml.migration;

class MigrationMapEntry {

  private final String oldName;
  private final String newName;
  private final MigrationMapEntryType entryType;

  MigrationMapEntry(String oldName, String newName, MigrationMapEntryType entryType) {
    this.oldName = oldName;
    this.newName = newName;
    this.entryType = entryType;
  }

  public String getOldName() {
    return oldName;
  }

  public String getNewName() {
    return newName;
  }

  public boolean isMappingOfConfigClass() {
    return entryType == MigrationMapEntryType.CONFIG_CLASS;
  }

  public boolean isMappingOfPropertiesClass() {
    return entryType == MigrationMapEntryType.PROPERTIES_CLASS;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(oldName);
    if (isMappingOfConfigClass()) {
      sb.append("[ExtConfig]");
    }
    if (isMappingOfPropertiesClass()) {
      sb.append("[ResourceBundle]");
    } else {
      sb.append("=>");
      sb.append(newName);
    }
    return sb.toString();
  }

}
