# Rust plugin for the IntelliJ Platform

[![official JetBrains project](http://jb.gg/badges/official-flat-square.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)
[![Join the chat at https://gitter.im/intellij-rust/intellij-rust][gitter-chat-svg]][gitter-chat]


| Build Status |                                                                              |
|--------------|------------------------------------------------------------------------------|
| Alpha        | [![Alpha Build Status][alpha-build-status-svg]][alpha-build-status]          |
| Nightly      | [![Nightly Build Status][nightly-build-status-svg]][nightly-build-status]    |
| TeamCity     | [![TeamCity Build Status][teamcity-build-status-svg]][teamcity-build-status] |
| Travis       | [![Travis Build Status][travis-build-status-svg]][travis-build-status]       |
| AppVeyor     | [![AppVeyor Build Status][appveyor-build-status-svg]][appveyor-build-status] |


## Status

This is a **work in progress**, some features are implemented partially, 
there may be performance and stability problems.

## Installation & Usage

Visit [intellij-rust.github.io] to find documentation about available
installation options and features. All new features are announced in 
the [changelog](https://intellij-rust.github.io/thisweek/).

If you want to jump straight in, install IntelliJ IDEA, open `Settings > Plugins > Browse repositories`,
install Rust plugin, and use **project from existing sources** action to import a Cargo-based project.

Unstable master branch builds can be downloaded from [TeamCity].

## FAQ

Frequently asked questions, a.k.a. "Where is Rust SDK?", are on the [website] :)

## TOML

If you are looking for the TOML plugin, see [intellij-toml] directory.

## Compatible IDEs

The plugin is compatible with any IntelliJ based IDE starting from 2017.2. 
If you don't have any yet, try [IntelliJ IDEA Community Edition](https://www.jetbrains.com/idea/), it's free.

## Contributing

You're encouraged to contribute to the plugin in any form if you've found any
issues or missing functionality that you'd want to see. Check out
[CONTRIBUTING.md] to learn how to setup the project and [ARCHITECTURE.md] to
understand the high level structure of the code base. Good first bugs are tagged
with [help wanted].

[intellij-rust.github.io]: https://intellij-rust.github.io/docs/
[website]: https://intellij-rust.github.io/docs/faq.html
[help wanted]: https://github.com/intellij-rust/intellij-rust/labels/help%20wanted
[CONTRIBUTING.md]: CONTRIBUTING.md
[ARCHITECTURE.md]: ARCHITECTURE.md
[TeamCity]: https://teamcity.jetbrains.com/guestAuth/repository/download/IntellijIdeaPlugins_Rust_CurrentIdea_TestsRust/.lastSuccessful/intellij-rust-0.2.0.{build.number}.zip
[intellij-toml]: intellij-toml/

<!-- Badges -->
[gitter-chat]: https://gitter.im/intellij-rust/intellij-rust
[gitter-chat-svg]: https://badges.gitter.im/Join%20Chat.svg

[travis-build-status]: https://travis-ci.org/intellij-rust/intellij-rust?branch=master
[travis-build-status-svg]: https://travis-ci.org/intellij-rust/intellij-rust.svg?branch=master

[appveyor-build-status]: https://ci.appveyor.com/project/matklad/intellij-rust/branch/master
[appveyor-build-status-svg]: https://ci.appveyor.com/api/projects/status/mc5rjptfa0t2cer5?svg=true

[teamcity-build-status]: https://teamcity.jetbrains.com/viewType.html?buildTypeId=IntellijIdeaPlugins_Rust_CurrentIdea_TestsRust&guest=1
[teamcity-build-status-svg]: https://teamcity.jetbrains.com/app/rest/builds/buildType:IntellijIdeaPlugins_Rust_CurrentIdea_TestsRust/statusIcon.svg

[alpha-build-status]: https://teamcity.jetbrains.com/viewType.html?buildTypeId=IntellijIdeaPlugins_Rust_UploadAlphaRust&guest=1
[alpha-build-status-svg]: https://teamcity.jetbrains.com/app/rest/builds/buildType:IntellijIdeaPlugins_Rust_UploadAlphaRust/statusIcon.svg

[nightly-build-status]: https://teamcity.jetbrains.com/viewType.html?buildTypeId=IntellijIdeaPlugins_Rust_UploadNightlyRust&guest=1
[nightly-build-status-svg]: https://teamcity.jetbrains.com/app/rest/builds/buildType:IntellijIdeaPlugins_Rust_UploadNightlyRust/statusIcon.svg
