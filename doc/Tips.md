**Bold text** denotes the names of actions you can type in the **Find Action**
  dialog (`Ctrl+Shift+A` or quickly press `Shift` twice for **Find Anything**).
  Shortcuts are given for the default Windows/Linux keymap. To learn shortcuts
    for your keymap consult `Help > Keymap reference`. Shortcut for an action is
  also displayed in the **Find Action** dialog.

## Tips

* Use hippie expand (aka dumb completion) for completion based solely on the
  words from the file.

* You can use run configuration to run different Rust tools. For example, you
  can create a run configuration to launch `cargo fmt`.


## Features

* **Goto Declaration** for local variables and items.
* **Goto Class/Symbol** (`Ctrl+N`, `Ctrl+Shift+N`).
* **Goto Super Module** (`Ctrl+U`).
* **File Structure**, aka imenu (`Ctrl+F12`, `Alt+7`).
* `Basic completion`.
* `Expand selection` (`Ctrl+W`).
* `Live templates` aka snippets (`Ctrl+J`).
* Run configurations.

## Intentions

Use `Alt+Enter` to invoke an intention.

* **Expand Module**: inside `foo.rs` file invoke this action to get `foo/mod.rs`.
* **Contract Module**: the opposite of **Expand Module**.
* **Extract Inline Module**: move `mod foo { ... }` to a separate file.
* **Create Module File**: if you have unresolved `mod foo;` this intention will create
  `foo.rs`. It's a convenient way to add a new Rust file: just type `mod bar;` and
  press `Alt+Enter`.

## Live Templates

Use `Ctrl+J` to view the list of templates applicable in the current context.

* In Rust File:
  - `p`, `pd`, `ppd` -- `print!` a (debug representation) of a value,
  - `a`, `ae` -- `assert!` / `assert_eq!`,
  - `tmod` -- test module boilerplate,
  - `loop`, `while`, `for` surround templates.

* In Rust structure:
  - `f` -- field name and type.
  - `pf` -- `pub` filed.


## Editors

We also have guides for [Emacs](Emacs.md) and [Vim](Vim.md) users :)
