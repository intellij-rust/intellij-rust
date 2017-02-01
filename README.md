# ![logo](art/icon_intellij_rust_32.png) Rust IDE built using the IntelliJ Platform


| Build Status |                                                                                                                            |
|--------------|----------------------------------------------------------------------------------------------------------------------------|
| TeamCity     | [![TeamCity Build Status][teamcity-build-status-svg]][teamcity-build-status] [![Performance][performance-svg]][performance]|
| Travis       | [![Travis Build Status][travis-build-status-svg]][travis-build-status]                                                     |
| AppVeyor     | [![AppVeyor Build Status][appveyor-build-status-svg]][appveyor-build-status]                                               |

[![Join the chat at https://gitter.im/intellij-rust/intellij-rust][gitter-chat-svg]][gitter-chat]

## Status

This is a **work in progress**, some features are implemented partially (most
notably completion), there may be performance and stability problems.

## Usage

Visit [intellij-rust.github.io] to find documentation about available
installation options and features.

If you want to jump straight in, install IntelliJ IDEA, open `Settings > Plugins > Browse repositories`,
install Rust plugin, and use **project from existing sources** action to import a Cargo-based project.

Unstable master branch builds can be downloaded from [TeamCity].

## FAQ

Frequently asked questions, a.k.a. "Where is Rust SDK?", are on the [website] :)

## TOML

If you are looking for the TOML plugin, see [toml] directory.

## Compatible IDEs

The plugin is compatible with any IntelliJ based IDE starting from 2016.2. 
If you don't have any yet, try [IntelliJ IDEA Community Edition](https://www.jetbrains.com/idea/)

## Contributing

You're encouraged to contribute to the plugin in any form if you've found any
issues or missing functionality that you'd want to see. Check out
[CONTRIBUTING.md] to learn how to setup the project and [ARCHITECTURE.md] to
understand the high level structure of the code base. Good first bugs are tagged
with [up-for-grab].

[intellij-rust.github.io]: https://intellij-rust.github.io/docs/
[website]: https://intellij-rust.github.io/docs/faq.html
[up-for-grab]: https://github.com/intellij-rust/intellij-rust/labels/up%20for%20grab
[CONTRIBUTING.md]: CONTRIBUTING.md
[ARCHITECTURE.md]: ARCHITECTURE.md
[TeamCity]: https://teamcity.jetbrains.com/repository/download/IntellijIdeaPlugins_Rust_CurrentIdea_TestsRust/.lastSuccessful/intellij-rust-0.1.0.%7Bbuild.number%7D.zip
[toml]: toml/

<!-- Badges -->
[gitter-chat]: https://gitter.im/intellij-rust/intellij-rust
[gitter-chat-svg]: https://img.shields.io/badge/Gitter-Join%20Chat-blue.svg?style=flat-square

[travis-build-status]: https://travis-ci.org/intellij-rust/intellij-rust?branch=master
[travis-build-status-svg]: https://img.shields.io/travis/intellij-rust/intellij-rust.svg?style=flat-square

[appveyor-build-status]: https://ci.appveyor.com/project/matklad/intellij-rust/branch/master
[appveyor-build-status-svg]: https://img.shields.io/appveyor/ci/matklad/intellij-rust.svg?style=flat-square

[teamcity-build-status]: https://teamcity.jetbrains.com/viewType.html?buildTypeId=IntellijIdeaPlugins_Rust_CurrentIdea_TestsRust&guest=1
[teamcity-build-status-svg]: https://img.shields.io/teamcity/http/teamcity.jetbrains.com/s/IntellijIdeaPlugins_Rust_CurrentIdea_TestsRust.svg?style=flat-square

[performance]: https://teamcity.jetbrains.com/project.html?projectId=IntellijIdeaPlugins_Rust&tab=stats&guest=1
[performance-svg]: https://img.shields.io/badge/build-performance-blue.svg?style=flat-square
