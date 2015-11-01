# Emacs

To bring some of Emacs keybindings to intellij-rust, enable `Emacs` keymap in
the settings. This will also enable Emacs tab and Emacs selection.


## Further customization

Crucial thing that is missing from Emacs keymap is `M-x`. To enable it change
`Find Action` binding to `M-x` in `File > Settings > Keymap`. If you want more
Emacs editing commands and a more uniform handling of UI panels (`C-g`, `C-x 1`,
`C-x 0`) consider [this plugin](https://plugins.jetbrains.com/plugin/7906), but
be warned that it is still in beta.
