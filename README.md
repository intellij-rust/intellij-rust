# Rust plugin for the IntelliJ Platform

[![official JetBrains project](https://jb.gg/badges/official.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)
[![JetBrains plugins][plugin-version-svg]][plugin-repo]
[![JetBrains plugins][plugin-downloads-svg]][plugin-repo]

| Build Status |                                                                              |
|--------------|------------------------------------------------------------------------------|
| Check        | [![Check Status][check-status-svg]][check-status]                            |
| Stable       | [![Stable Build Status][stable-build-status-svg]][stable-build-status]       |
| Beta         | [![Beta Build Status][beta-build-status-svg]][beta-build-status]             |
| Nightly      | [![Nightly Build Status][nightly-build-status-svg]][nightly-build-status]    |

## Installation & Usage

For installation options, see the [Quick Start Guide](https://plugins.jetbrains.com/plugin/8182-rust/docs/rust-quick-start.html#install-update).
If you want to jump straight in, open `Settings > Plugins > Marketplace` in your IDE,
search for _Rust_ and install the plugin. To open an existing project, use **File | Open** and point to the directory containing `Cargo.toml`. 
For creating projects, use the **Rust** template.

All the plugin's features are described in [documentation](https://plugins.jetbrains.com/plugin/8182-rust/docs).
New features are regularly announced in [changelogs](https://intellij-rust.github.io/thisweek/).


## Compatible IDEs

The plugin is compatible with all IntelliJ-based IDEs starting from the version 2022.2, with the following differences in the sets of the available features:


|                        | Open-source and Educational IDEs<sup>*</sup> | [CLion] | [IntelliJ IDEA] Ultimate, [GoLand] | [PyCharm] Professional | [WebStorm], [PhpStorm], other commercial IDEs |
|------------------------|----------------------------------------------|---------|------------------------------------|------------------------|-----------------------------------------------|
| [Language support]     | +                                            | +       | +                                  | +                      | +                                             |
| [Cargo support]        | +                                            | +       | +                                  | +                      | +                                             |
| [Code coverage]        | +                                            | +       | +                                  | +                      | +                                             |
| [Detecting duplicates] | -                                            | +       | +                                  | +                      | +                                             |
| [Debugger]             | -                                            | +       | +**                                | +**                    | -                                             |
| [Run targets]          | -                                            | +       | +                                  | -                      | -                                             |
| [Profiler]             | -                                            | +       | -                                  | -                      | -                                             |
| [Valgrind Memcheck]    | -                                            | +       | -                                  | -                      | -                                             |


\* [IntelliJ IDEA] Community Edition, [PyCharm] Community Edition, [PyCharm Edu], [IntelliJ IDEA Edu], and third-party IntelliJ-based IDEs.

\** Requires the
[Native Debugging Support](https://plugins.jetbrains.com/plugin/12775-native-debugging-support) plugin. 
LLDB only

## TOML

If you are looking for the TOML plugin, see [toml] directory in [intellij-community](https://github.com/JetBrains/intellij-community) repository.

## Contributing

You're encouraged to contribute to the plugin if you've found any
issues or missing functionality that you would want to see. Check out
[CONTRIBUTING.md] to learn how to set up the project and [ARCHITECTURE.md] to
understand the high-level structure of the codebase. If you are not sure where to start, consider the issues tagged with [help wanted].

[intellij-rust.github.io]: https://intellij-rust.github.io/
[website]: https://intellij-rust.github.io/docs/faq.html
[help wanted]: https://github.com/intellij-rust/intellij-rust/labels/help%20wanted
[CONTRIBUTING.md]: CONTRIBUTING.md
[ARCHITECTURE.md]: ARCHITECTURE.md
[toml]: https://github.com/JetBrains/intellij-community/tree/master/plugins/toml

<!-- Badges -->

[plugin-repo]: https://plugins.jetbrains.com/plugin/8182-rust
[plugin-version-svg]: https://img.shields.io/jetbrains/plugin/v/8182-rust.svg
[plugin-downloads-svg]: https://img.shields.io/jetbrains/plugin/d/8182-rust.svg

[check-status]: https://github.com/intellij-rust/intellij-rust/actions?query=workflow%3Acheck+event%3Apush+branch%3Amaster
[check-status-svg]: https://github.com/intellij-rust/intellij-rust/workflows/check/badge.svg?branch=master&event=push

[stable-build-status]: https://github.com/intellij-rust/intellij-rust/actions?query=workflow%3A%22rust+release%22+event%3Aworkflow_dispatch
[stable-build-status-svg]: https://github.com/intellij-rust/intellij-rust/workflows/rust%20release/badge.svg?event=workflow_dispatch

[beta-build-status]: https://github.com/intellij-rust/intellij-rust/actions?query=workflow%3A%22rust+release%22+event%3Aschedule
[beta-build-status-svg]: https://github.com/intellij-rust/intellij-rust/workflows/rust%20release/badge.svg?event=schedule

[nightly-build-status]: https://github.com/intellij-rust/intellij-rust/actions?query=workflow%3A%22rust+nightly%22
[nightly-build-status-svg]: https://github.com/intellij-rust/intellij-rust/workflows/rust%20nightly/badge.svg


[IntelliJ IDEA]: https://www.jetbrains.com/idea/
[CLion]: https://www.jetbrains.com/clion/
[PyCharm]: https://www.jetbrains.com/pycharm/
[GoLand]: https://www.jetbrains.com/go/
[WebStorm]: https://www.jetbrains.com/webstorm/
[PhpStorm]: https://www.jetbrains.com/phpstorm/
[PyCharm Edu]: https://www.jetbrains.com/pycharm-edu/
[IntelliJ IDEA Edu]: https://www.jetbrains.com/idea-edu/
[Detecting duplicates]: https://www.jetbrains.com/help/idea/analyzing-duplicates.html
[Language support]: https://plugins.jetbrains.com/plugin/8182-rust/docs/rust-syntax-highlighting.html
[Cargo support]: https://plugins.jetbrains.com/plugin/8182-rust/docs/rust-new-cargo-projects.html
[Code coverage]: https://plugins.jetbrains.com/plugin/8182-rust/docs/rust-code-coverage.html
[Debugger]: https://plugins.jetbrains.com/plugin/8182-rust/docs/rust-debugging.html
[Run targets]: https://plugins.jetbrains.com/plugin/8182-rust/docs/rust-run-targets.html
[Profiler]: https://plugins.jetbrains.com/plugin/8182-rust/docs/rust-profiler.html
[Valgrind Memcheck]: https://plugins.jetbrains.com/plugin/8182-rust/docs/rust-valgrind.html
