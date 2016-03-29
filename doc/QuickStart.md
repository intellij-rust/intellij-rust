**Bold text** denotes the names of actions you can type in the **Find Action**
  dialog (`Ctrl+Shift+A`).

After you have installed the plugin and a recent version of Cargo, you can
**import project**. You can do this from the welcome screen.

![Welcome screen](imgs/welcome.png)

Select `Cargo.toml`.

![Select project](imgs/select_cargo_toml.png)

Plugin should find Cargo automatically, but you can specify the path to folder
with Cargo via `Cargo home` setting:

![Import project](imgs/import_project.png)

Wait until Cargo downloads all project dependencies. To check that everything is
working, try **Goto Symbol** (`Ctrl+Alt+Shift+N`) and type something.

![Go to symbol](imgs/go_to_symbol.png)

To execute Cargo tasks from within IDE, you need to set up a [Rust
SDK](https://www.jetbrains.com/idea/help/sdk.html) and a [Run
Configuration](https://www.jetbrains.com/idea/help/creating-and-editing-run-debug-configurations.html).
At the moment you also need to update the Kotlin plugin to the latest version.

SDK is setup in the **Project Structure** (`Ctrl+Alt+Shift+S`) dialog:

![Project Structure](imgs/project_sdk.png)

Again, IDE should automatically pick up the path to the directory with Cargo.

![SDK is ready](imgs/sdk_is_set_up.png)

Next, **Edit configurations** (`Alt+Shift+F10`) and add a "Cargo command" config.

![Add Run Configuration](imgs/add_run_config.png)

Fill in the name of command and additional arguments.

![Edit Run Configuration](imgs/edit_run_config.png)

You should be able to compile and run your application from IDE now:

![App is running](imgs/running.png)

# Updating

In general updating the plugin should just work even if we change things we
cache or persist. However given the current rate of change we do not test for
this and so it is possible for data from the previous versions to confuse the
newer version of the plugin. You can **Refresh all external projects** to force a
reimport of Cargo project. You can use **Invalidata caches/Restart** to rebuild indices.

# Tips

Check out [tips](Tips.md) for some neat tricks!