**Bold text** denotes the names of actions you can type in the **Find Action**
  dialog (`Ctrl+Shift+A` or quickly press `Shift` twice for **Find Anything**).
  Shortcuts are given for the default Windows/Linux keymap. To learn shortcuts
  for your keymap consult `Help > Keymap reference`. Shortcut for an action is
  also displayed in the **Find Action** dialog.

After you have installed the plugin and a recent version of Cargo, you can
**import project**. "Importing" means that the IDE will talk with Cargo to learn
about project structure and dependencies. You can do this from the welcome
screen.

![welcome](https://cloud.githubusercontent.com/assets/1711539/14211294/e0ce72c8-f835-11e5-9bfd-061098d70243.png)

Select `Cargo.toml`.

![select-cargo-toml](https://cloud.githubusercontent.com/assets/1711539/14211300/e89a41a8-f835-11e5-8564-ae7237a5dba3.png)

Next you should tell the IDE which version of Cargo it should use. To do this
you need to set up a Rust SDK. **S**oftware **D**evelopment **K**it is an IDE
term which means a set of tools used to write programs in a particular language.
In case of Rust, the SDK is basically `cargo` and `rustc` programms. Press the
"New..." button. The IDE should find an SDK automatically, but you can manually
point it to the location of Cargo. **Important**: you need Cargo from at least
1.8.0 toolchain, which is currently in beta. You can use any version of `rustc`.

![add-sdk-1](https://cloud.githubusercontent.com/assets/1711539/14211305/f36b5040-f835-11e5-9fb3-1d3e05052b05.png)

![add-sdk-2](https://cloud.githubusercontent.com/assets/1711539/14211306/f385c9a2-f835-11e5-8673-4af824d12720.png)

![add-skd-3](https://cloud.githubusercontent.com/assets/1711539/14211307/f3aaaa9c-f835-11e5-986f-fa54e96149fd.png)

Wait until Cargo downloads all project dependencies. To check that everything is
working, try **Goto Symbol** (`Ctrl+Alt+Shift+N`) and type something.

![go-to-symbol](https://cloud.githubusercontent.com/assets/1711539/14211909/2b504076-f839-11e5-86b5-a848c5504522.png)

To execute Cargo tasks from within the IDE, you need to set up a [Run
Configuration](https://www.jetbrains.com/idea/help/creating-and-editing-run-debug-configurations.html).
**Edit configurations** (`Alt+Shift+F10`) and add a "Cargo command" config. Be
sure to click the green plus sign, and **not** the "Defaults" :) You can also
use `Alt+Insert` shortcut here.

![add-run-configuration](https://cloud.githubusercontent.com/assets/1711539/14211919/33d29e60-f839-11e5-8c08-c8d09cbbf4ee.png)

Fill in the name of a command and additional arguments.

![edit-run-configuration](https://cloud.githubusercontent.com/assets/1711539/14211918/33ce8e56-f839-11e5-92c2-8c96bf365699.png)

You should be able to compile and **Run** (`Shift+f10`) your application from the IDE now:

![running](https://cloud.githubusercontent.com/assets/1711539/14211917/33cb0c54-f839-11e5-8026-d4fd7a7b44fd.png)

# Updating

In general updating the plugin should just work even if we change things we
cache or persist. However given the current rate of change we do not test for
this and so it is possible for data from the previous versions to confuse the
newer version of the plugin. You can **Refresh all external projects** to force a
reimport of Cargo project. You can use **Invalidata caches/Restart** to rebuild indices.

# Tips

Check out [tips](Tips.md) for some neat tricks!