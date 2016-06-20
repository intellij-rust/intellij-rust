**Bold text** denotes the names of actions you can type in the **Find Action**
  dialog (`Ctrl+Shift+A` or quickly press `Shift` twice for **Find Anything**).
  Shortcuts are given for the default Windows/Linux keymap. To learn shortcuts
    for your keymap consult `Help > Keymap reference`. Shortcut for an action is
  also displayed in the **Find Action** dialog.

--------------------------------------------------------------------------------


## Features

### Goto Declaration

Use `Ctrl+B` or `Ctlr + mouse click` to jump to definition. We don't do type
inference yet, so this will not work for fields and methods. Plain functions,
types and modules are fully supported.

### Code completion

`Ctrl+space` invokes completion. A decent amount of completion intelligence is
already implemented, but there is definitely a lot more to do. Here are some
examples of what works and what does not work (as of June 20).


```Rust
use std::collections::HashMap;
                        //^ completes to HashMap or HashSet

struct S {
    mapping: HashMap<i32, i32>
            //^ knows that HashMap is in scope, so completes
}

fn main() {
    let mut thing = S { mapping: HashMap::default() };
                                          //^ no completion here yet :(

    //v completes `thing`
    thing.mapping.insert(92, 92);
          //^ completes `mapping`
                  //^ but does not know about `insert` yet :(
}
```

Remember, if the smart completion does not work for your particular case,
you can always invoke "dumb completion" via `Alt+/`. It merely suggests
identifiers already present in the file, but works surprisingly good.

### Goto Class/Symbol

`Ctrl+N` finds any `struct` or `enum` by name. `Ctrl+Shift+N` finds any symbol
(functions, traits, modules, methods, fields) by name. This works for items in
your crate, your crate's dependencies or the standard library.

### Goto Super Module

`Ctrl+U` brings you to the parent of the current module.

### File Structure

`Ctrl+F12` brings up an overview of the current file. It is usefull for
navigation. `Alt+7` presents the same information in the "project structure"
style.

### Quick Documentation

`Ctrl+Q` shows docs for the thing at caret.


### Expand selection

Press `Ctrl+W` repeatedly to select larger semantic constructs at caret.

### Live templates

Use `Ctrl+J` to view the list of templates applicable in the current context.

* In Rust File:
  - `p`, `pd`, `ppd` -- `print!` a (debug representation) of a value,
  - `a`, `ae` -- `assert!` / `assert_eq!`,
  - `tfn` -- test function boilerplate,
  - `tmod` -- test module boilerplate,
  - `loop`, `while`, `for` surround templates.

* In Rust struct:
  - `f` -- field name and type.
  - `pf` -- `pub` field.

### Formatter

`Ctrl+Alt+l` reformats the file. We don't support `rustfmt` yet but we plan to.
At the moment you can use Run Configuration to execute `cargo fmt`.

### Run configurations

Use **Edit Configurations** action to add `Cargo Command` run configuration.
`Ctrl+Shift+F10` automatically creates a run configuration in the following
contexts:

* In the file with `fn main() {}` to execute the corresponding binary target
  with `cargo run`.

* Inside the function with a `#[test]` attribute to execute a single test.

* Inside a module with test functions to run all the tests from the module.

## Intentions

Use `Alt+Enter` to invoke an intention.

* **Expand module**: invoke this action inside `foo.rs` file to get `foo/mod.rs`.
* **Contract module**: the opposite of **Expand Module**.
* **Extract inline module**: move `mod foo { ... }` to a separate file.
* **Create module file**: if you have unresolved `mod foo;` this intention will create
  `foo.rs`. It's a convenient way to add a new Rust file: just type `mod bar;` and
  press `Alt+Enter`.
* **Add derive clause**: invoke this action inside struct or enum, to add traits to the `#[derive(..)]`.

## Editors

### Emacs

To bring some of Emacs keybindings to intellij-rust, enable `Emacs` keymap in
the settings. This will also enable Emacs tab and Emacs selection.

Crucial thing that is missing from Emacs keymap is `M-x`. To enable it change
`Find Action` binding to `M-x` in `File > Settings > Keymap`. If you want more
Emacs editing commands and a more uniform handling of UI panels (`C-g`, `C-x 1`,
`C-x 0`) consider [this plugin](https://plugins.jetbrains.com/plugin/7906), but
be warned that it is still in beta.

### Vim

To bring your favorite modal editing to intellij-rust, please use
[IdeaVim plugin](https://github.com/JetBrains/ideavim).
