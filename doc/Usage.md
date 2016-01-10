# Usage

Please note that most of the features are missing at the moment.

* There is no cargo support, you have to use command line to build your
  project. You can start a shell from the IDE with `Alt+F12`.
* There is no intelligent completion. You can use `expand word` (`Alt+/` by
  default) to complete based on the words present in the file.
* Formatting support is very basic, so `reformat code` will not do what you
  want.


## Features

* `Go to definition` for local variables and items.
* `Go to class/symbol` (`Ctrl+N`, `Ctrl+Shift+N`)
* `File structure`, aka imenu (`Ctrl+F12`, `Alt+7`).
* `Basic completion`
* `Expand selection` (`Ctrl+W`).
* `Live templates` aka snippets (`Ctrl+J`).

## Intentions

Use `Alt+Enter` to invoke an intention.

* `ExpandModule`: inside `foo.rs` file invoke this action to get `foo/mod.rs`. 

## Tips

You may find [external tools](https://www.jetbrains.com/idea/help/external-tools.html) 
useful to invoke cargo commands. You can create tools for `cargo run` and `cargo fmt` 
and assign shortcuts to them.  

## Editors

We also have guides for [Emacs](Emacs.md) and [Vim](Vim.md) users :)
