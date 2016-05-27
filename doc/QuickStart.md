# Quick Start

**Bold text** denotes the names of actions you can type in the **Find Action**
  dialog (`Ctrl+Shift+A` or quickly press `Shift` twice for **Find Anything**).
  Shortcuts are given for the default Windows/Linux keymap. To learn shortcuts
  for your keymap consult `Help > Keymap reference`. Shortcut for an action is
  also displayed in the **Find Action** dialog.

--------------------------------------------------------------------------------

## Creating new project

Creating new project from within IDE is not supported yet, so you have to invoke
`cargo new my_project` from the command line and then open `my_project` directory.

## Opening existing project in IntelliJ IDEA

After you have
[installed](https://github.com/intellij-rust/intellij-rust#installation) the
plugin into IntelliJ IDEA you can **import** a project from source (if you use
other IDE, see [these instructions](#opening-project-all-ides)). You can import a project form
the welcome screen:

![idea-import](https://cloud.githubusercontent.com/assets/1711539/14211294/e0ce72c8-f835-11e5-9bfd-061098d70243.png)

Select the directory with the project.

![select-directory](https://cloud.githubusercontent.com/assets/1711539/14491098/85f750f6-017f-11e6-81ec-f1fcab920c8f.png)

Use "from existing sources" import.

![from-sources](https://cloud.githubusercontent.com/assets/1711539/14491096/85f346f0-017f-11e6-8f68-138a65d2cfb9.png)

Click throught the wizard steps and select a Rust toolchain:

![import-toolchain-step](https://cloud.githubusercontent.com/assets/1711539/14824500/ef917078-0bde-11e6-9d8f-802e6065fad5.png)

## Opening project (all IDEs)

Instead of importing, you can also simply **open** a project.

![py-open](https://cloud.githubusercontent.com/assets/1711539/14491095/85f23ea4-017f-11e6-9809-fb4c7cbb248e.png)

Select the directory with the project. Open any Rust file and configure toolchain:

![toolchain-notification](https://cloud.githubusercontent.com/assets/1711539/14825248/c6d42ed4-0be1-11e6-96f7-01c76e4cdf10.png)

## After your project is ready...


Wait until Cargo downloads all project dependencies. To check that everything is
working, try **Goto Symbol** (`Ctrl+Alt+Shift+N`) and type something. Note that
dependencies are present under external libraries. **Goto Symbol** should also
work for items from the external crates.

![go-to-symbol](https://cloud.githubusercontent.com/assets/1711539/14491412/44200bd0-0181-11e6-9587-10e4a07fa961.png)

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

You can change toolchain in the **settings** (`Ctrl+Alt+S`), under `Languages &
Frameworks > Rust` tab. We need Cargo from at least 1.8.0 release.

![settings](https://cloud.githubusercontent.com/assets/1711539/14491097/85f717d0-017f-11e6-98d6-0f60ee0e2016.png)

Plugin automatically watches `Cargo.toml` for changes and communicates with
Cargo to learn about project structure and dependencies. If you disable this
behavior, or if you want to force a project update, you can use **Refresh Cargo
project** action.

## Updating

In general, plugin updates should go smoothly. Though, if you experience some weird behaviour, reimport your project (using **Refresh Cargo project** action) or/and rebuild indices (using **Invalidate caches/Restart** action), prior to reporting bugs.

## Tips

Check out [features](Features.md) for some neat tricks!
