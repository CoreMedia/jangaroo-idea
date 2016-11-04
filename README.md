# Jangaroo plugin for JetBrains IDEs
Adds support for [Jangaroo](http://www.jangaroo.net/) open source tooling to IntelliJ IDEA.

See [JetBrains Plugin Repository](https://plugins.jetbrains.com/plugin/6465) for further details.

## Installing
This plugin can be installed using your IDE's plugin manager.
 - In _Preferences... | Plugins_, choose `Browse repositories ...`
 - Search for `Jangaroo 0.9` and then select `Download and Install`

## Issues/Feature Requests
Issues and feature requests are managed at [http://jangaroo.myjetbrains.com/youtrack/issues/IDEA](http://jangaroo.myjetbrains.com/youtrack/issues/IDEA)

## Development Setup

- Open top-level `jangaroo-idea.ipr` in IDEA
- Open _File | Project Structure ..._ and make sure that you have the IDEA plugin SDK set.
- If it's red, do the following:
  - click _New | IntelliJ IDEA Plugin SDK_
  - select your IDEA installation home dir and press _Ok_
  - chose Java SDK 1.8 (1.7 may also work)
  - the new plugin SDK is selected in the combo box.
  - Click _Edit_ to add some JARs for used plugins
  - On the _Classpath_ tab, click _Add_
  - select the following additional JARs from the IDEA installation:
    - `plugins/JavaScriptLanguage/lib/javascript-openapi.jar`
    - `plugins/JavaScriptLanguage/lib/JavaScriptLanguage.jar`
    - `plugins/maven/lib/maven.jar`
    - `plugins/maven/lib/maven-server-api.jar`
    - `plugins/maven-ext/lib/maven-ext.jar`
    - `plugins/JavaEE/lib/javaee-impl.jar`
    - `plugins/flex/lib/flex-shared.jar`
    - `plugins/flex/lib/FlexSupport.jar`
    - `plugins/flex/lib/flex-jps-plugin.jar`
    - `plugins/properties/lib/properties.jar`
    - `plugins/JavaScriptDebugger/lib/JavaScriptDebugger.jar`
- Reimport all Maven projects
- Check that everything builds (`Ctrl-F9`, Make Project)
- For testing your changes, start a new IDEA instance by running or debugging the run configuration "Jangaroo IDEA Plugin".
  - When starting for the first time, you have to set up the second IDEA instance like any newly installed IDEA. You should minimize the number of plugins for a fast development round-trip.
