# Installation

We intentionally do not provide a download for the plugin just yet. If
you want to use the plugin, you have to build it from source.

Building:

```
$ git clone https://github.com/alexeykudinkin/intellij-rust
$ cd intellij-rust
$ ./gradlew buildPlugin
```

This creates a zip archive in `build/distributions` which you can install with
`install plugin from disk` action.

# Usage

Please note that most of the features are missing at the moment.

* There is no cargo support, you have to use command line to build your
  project. You can start a shell from the IDE with `Alt-F12`.
* There is no intelligent completion. You can use `expand word` (`Alt-/` by
  default) to complete based on the words present in the file.
* Formatting support is very basic, so `reformat code` will not do what you
  want.


Semantic features already implemented:

* `Go to definition` for local variables,
* `File structure`, aka imenu. `Ctrl-F12` and `Alt-7` by default,
* `Expand selection` bound to `Ctrl-w` by default.

We also have guides for [Emacs](Emacs.md) and [Vim](Vim.md) users :)
