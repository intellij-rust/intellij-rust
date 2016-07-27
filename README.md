# Rust IDE built using the IntelliJ Platform


| Build Status |                                                                              |
|--------------|------------------------------------------------------------------------------|
| TeamCity     | [![TeamCity Build Status][teamcity-build-status-svg]][teamcity-build-status] |
| Travis       | [![Travis Build Status][travis-build-status-svg]][travis-build-status]       |
| AppVeyor     | [![AppVeyor Build Status][appveyor-build-status-svg]][appveyor-build-status] |
|              | [Performance][Performance metrics]                                           |

[![Join the chat at https://gitter.im/intellij-rust/intellij-rust][gitter-chat-svg]][gitter-chat]

## Status

This is a **work in progress**, some features are implemented partially (most
notably completion), there may be performance and stability problems.

## Usage

Visit [intellij-rust.github.io] to find documentation about available
installation options and features.

If you want to jump straight in, install IntelliJ IDEA, open `Settings > Plugins > Browse repositories`,
install Rust plugin, and use **project from existing sources** action to import a Cargo-based project.

## Compatible IDEs

The plugin should be compatible with any Intellij based IDE starting from build
143.2287.1. For example, the minimum supported IDEA version is 15.0.4, the
minimum supported CLion version is 1.2. See `Help > About` menu in the IDE to
learn the build version you are using.

## Contributing

You're encouraged to contribute to the plugin in any form if you've found any
issues or missing functionality that you'd want to see. In order to get started
and to learn about plugin architecture check out [CONTRIBUTING.md] guide. Good
first bugs are tagged with [up-for-grab].

[intellij-rust.github.io]: https://intellij-rust.github.io/docs/
[up-for-grab]: https://github.com/intellij-rust/intellij-rust/labels/up%20for%20grab
[CONTRIBUTING.md]: CONTRIBUTING.md

<!-- Badges -->
[gitter-chat]: https://gitter.im/intellij-rust/intellij-rust
[gitter-chat-svg]: https://img.shields.io/badge/Gitter-Join%20Chat-blue.svg?style=flat-square

[travis-build-status]: https://travis-ci.org/intellij-rust/intellij-rust
[travis-build-status-svg]: https://img.shields.io/travis/intellij-rust/intellij-rust.svg?style=flat-square

[appveyor-build-status]: https://ci.appveyor.com/project/matklad/intellij-rust
[appveyor-build-status-svg]: https://img.shields.io/appveyor/ci/matklad/intellij-rust.svg?style=flat-square

[teamcity-build-status]: https://teamcity.jetbrains.com/viewType.html?buildTypeId=IntellijIdeaPlugins_Rust_Idea16_TestsRust_2&guest=1
[teamcity-build-status-svg]: https://img.shields.io/teamcity/http/teamcity.jetbrains.com/s/IntellijIdeaPlugins_Rust_Idea16_TestsRust_2.svg?style=flat-square

[Performance metrics]: https://teamcity.jetbrains.com/project.html?projectId=IntellijIdeaPlugins_Rust&tab=stats&guest=1
