**Bold text** denotes the names of actions you can type in the **Find Action**
  dialog (`Ctrl+Shift+A` or quickly press `Shift` twice for **Find Anything**).
  Shortcuts are given for the default Windows/Linux keymap. To learn shortcuts
    for your keymap consult `Help > Keymap reference`. Shortcut for an action is
  also displayed in the **Find Action** dialog.

--------------------------------------------------------------------------------



## Features

* **Goto Declaration** for local variables and items (`Ctrl+B`, `Ctrl` + mouse click).
* **Goto Class/Symbol** (`Ctrl+N`, `Ctrl+Shift+N`).
* **Goto Super Module** (`Ctrl+U`).
* **File Structure** (`Ctrl+F12`, `Alt+7`).
* `Code completion`, though it is limited at the moment. You can use `Alt+/` completion as a workaround.
* `Expand selection` (`Ctrl+W`).
* `Live templates` aka snippets (`Ctrl+J`).
* Initial formatter support (`Ctrl+Alt+l`).
* Run configurations.

## Intentions

Use `Alt+Enter` to invoke an intention.

* **Expand module**: invoke this action inside `foo.rs` file to get `foo/mod.rs`.
* **Contract module**: the opposite of **Expand Module**.
* **Extract inline module**: move `mod foo { ... }` to a separate file.
* **Create module file**: if you have unresolved `mod foo;` this intention will create
  `foo.rs`. It's a convenient way to add a new Rust file: just type `mod bar;` and
  press `Alt+Enter`.
* **Add derive clause**: invoke this action inside struct or enum, to add traits to the `#[derive(..)]`.

## Live Templates

Use `Ctrl+J` to view the list of templates applicable in the current context.

* In Rust File:
  - `p`, `pd`, `ppd` -- `print!` a (debug representation) of a value,
  - `a`, `ae` -- `assert!` / `assert_eq!`,
  - `tfn` -- test function boilerplate,
  - `tmod` -- test module boilerplate,
  - `loop`, `while`, `for` surround templates.

* In Rust structure:
  - `f` -- field name and type.
  - `pf` -- `pub` field.


## Tips

* Use hippie expand `Alt+/` (aka dumb completion) for completion based solely on the
  words from the file.

* You can use run configuration to run different Rust tools. For example, you
  can create a run configuration to launch `cargo fmt`.

## Editors

We also have guides for [Emacs](Emacs.md) and [Vim](Vim.md) users :)
