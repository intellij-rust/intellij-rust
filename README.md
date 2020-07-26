# Rust plugin for the IntelliJ Platform

[![official JetBrains project](https://jb.gg/badges/official.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)
[![Join the chat at https://gitter.im/intellij-rust/intellij-rust][gitter-chat-svg]][gitter-chat]
[![JetBrains plugins][plugin-version-svg]][plugin-repo]
[![JetBrains plugins][plugin-downloads-svg]][plugin-repo]

| Build Status |                                                                              |
|--------------|------------------------------------------------------------------------------|
| Check        | [![Check Status][check-status-svg]][check-status]                            |
| Stable       | [![Stable Build Status][stable-build-status-svg]][stable-build-status]       |
| Beta         | [![Beta Build Status][beta-build-status-svg]][beta-build-status]             |
| Nightly      | [![Nightly Build Status][nightly-build-status-svg]][nightly-build-status]    |

## Installation & Usage

Available installation options and features are described on [intellij-rust.github.io]. All new features are announced in 
the [changelog](https://intellij-rust.github.io/thisweek/).

If you want to jump straight in, open `Settings > Plugins > Marketplace` in your IDE,
search for _Rust_ and install the plugin. To open an existing project, use **File | Open** and point to the directory containing `Cargo.toml`. For creating projects, use the **Rust** template. You can find more details in the [Quick Start Guide](https://intellij-rust.github.io/docs/quick-start.html). 

## Compatible IDEs

The plugin is compatible with all IntelliJ-based IDEs starting from the version 2020.1, with the following differences in the sets of the available features:


|                        | Open-source and Educational IDEs<sup>*</sup> | [CLion] (commercial) | [IntelliJ IDEA] Ultimate (commercial) | [PyCharm] Professional, [GoLand], other commercial IDEs |
|------------------------|---|---|---|---|
| Language support       | + | + | + | + |
| Cargo support          | + | + | + | + |
| Code coverage          | + | + | + | + |
| Debugger               | - | + | +** | - |
| Profiler               | - | + | - | - |
| Valgrind Memcheck      | - | + | - | - |
| [Detecting duplicates] | - | + | + | + |


\* [IntelliJ IDEA] Community Edition, [PyCharm] Community Edition, [PyCharm Edu and IntelliJ IDEA Edu].

\** Requires the
[Native Debugging Support](https://plugins.jetbrains.com/plugin/12775-native-debugging-support) plugin. 
Available on macOS and Linux, LLDB only.

## TOML

If you are looking for the TOML plugin, see [intellij-toml] directory.

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
[intellij-toml]: intellij-toml/

<!-- Badges -->
[gitter-chat]: https://gitter.im/intellij-rust/intellij-rust
[gitter-chat-svg]: https://badges.gitter.im/Join%20Chat.svg

[plugin-repo]: https://plugins.jetbrains.com/plugin/8182-rust
[plugin-version-svg]: https://img.shields.io/jetbrains/plugin/v/8182-rust.svg
[plugin-downloads-svg]: https://img.shields.io/jetbrains/plugin/d/8182-rust.svg

[check-status]: https://github.com/intellij-rust/intellij-rust/actions?query=workflow%3Acheck+event%3Apush+branch%3Amaster
[check-status-svg]: https://github.com/intellij-rust/intellij-rust/workflows/check/badge.svg?branch=master&event=push

[stable-build-status]: https://github.com/intellij-rust/intellij-rust/actions?query=workflow%3A%22rust+release%22+event%3Arepository_dispatch
[stable-build-status-svg]: https://github.com/intellij-rust/intellij-rust/workflows/rust%20release/badge.svg?event=repository_dispatch

[beta-build-status]: https://github.com/intellij-rust/intellij-rust/actions?query=workflow%3A%22rust+release%22+event%3Aschedule
[beta-build-status-svg]: https://github.com/intellij-rust/intellij-rust/workflows/rust%20release/badge.svg?event=schedule

[nightly-build-status]: https://github.com/intellij-rust/intellij-rust/actions?query=workflow%3A%22rust+nightly%22
[nightly-build-status-svg]: https://github.com/intellij-rust/intellij-rust/workflows/rust%20nightly/badge.svg


[IntelliJ IDEA]: https://www.jetbrains.com/idea/
[CLion]: https://www.jetbrains.com/clion/
[PyCharm]: https://www.jetbrains.com/pycharm/
[GoLand]: https://www.jetbrains.com/go/
[PyCharm Edu and IntelliJ IDEA Edu]: https://www.jetbrains.com/education
[Detecting duplicates]: https://www.jetbrains.com/help/idea/analyzing-duplicates.html
