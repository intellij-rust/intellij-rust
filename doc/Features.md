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

`Ctrl+space` invokes completion. It is limited at the moment because type
inference is not yet implemented. You can use `Alt+/` as a workaround. It
completes based on the words present in the file.

### Goto Class/Symbol

`Ctrl+N` finds any `struct` or `enum` by name. `Ctrl+Shift+N` finds any item
(functions, traits, modules) by name. This works for items in your crate, your
crate's dependencies or the standard library.

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

`Ctrl+Alt+l` reformats the file. Formatter is not fully implemented. It should
correctly add/remove spaces around operators, but it might mess up the
indentation. We don't support `rustfmt` yet but the support is planned. You can
use Run Configuration to execute `cargo fmt`.

### Run configurations

Use **Edit Configurations** action to add `Cargo Command` run configuration.

Use `Ctrl+Shift+F10` in the file with `fn main() {}` to execute
the corresponding binary target with `cargo run`.

Use `Ctrl+Shift+F10` when the caret is over a test function to execute a
specific test.

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
