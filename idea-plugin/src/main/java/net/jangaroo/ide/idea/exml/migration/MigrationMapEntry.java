package net.jangaroo.ide.idea.exml.migration;

class MigrationMapEntry {

  private final String oldName;
  private final String newName;
  private final boolean mappingOfConfigClass;

  MigrationMapEntry(String oldName, String newName, boolean mappingOfConfigClass) {
    this.oldName = oldName;
    this.newName = newName;
    this.mappingOfConfigClass = mappingOfConfigClass;
  }

  public String getOldName() {
    return oldName;
  }

  public String getNewName() {
    return newName;
  }

  public boolean isMappingOfConfigClass() {
    return mappingOfConfigClass;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(oldName);
    if (mappingOfConfigClass) {
      sb.append("[ExtConfig]");
    }
    sb.append("=>");
    sb.append(newName);
    return sb.toString();
  }

}
