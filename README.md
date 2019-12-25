# Rust plugin for the IntelliJ Platform

[![official JetBrains project](https://jb.gg/badges/official.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)
[![Join the chat at https://gitter.im/intellij-rust/intellij-rust][gitter-chat-svg]][gitter-chat]
[![JetBrains plugins][plugin-version-svg]][plugin-repo]
[![JetBrains plugins][plugin-downloads-svg]][plugin-repo]

| Build Status |                                                                              |
|--------------|------------------------------------------------------------------------------|
| Travis       | [![Travis Build Status][travis-build-status-svg]][travis-build-status]       |
| AppVeyor     | [![AppVeyor Build Status][appveyor-build-status-svg]][appveyor-build-status] |
| Stable       | [![Stable Build Status][stable-build-status-svg]][stable-build-status]       |
| Beta         | [![Beta Build Status][beta-build-status-svg]][beta-build-status]             |
| Nightly      | [![Nightly Build Status][nightly-build-status-svg]][nightly-build-status]    |
| TeamCity     | [![TeamCity Build Status][teamcity-build-status-svg]][teamcity-build-status] |


## Installation & Usage

Available installation options and features are described on [intellij-rust.github.io]. All new features are announced in 
the [changelog](https://intellij-rust.github.io/thisweek/).

If you want to jump straight in, open `Settings > Plugins > Marketplace` in your IDE,
search for _Rust_ and install the plugin. To open an existing project, use **File | Open** and point to the directory containing `Cargo.toml`. For creating projects, use the **Rust** template. You can find more details in the [Quick Start Guide](https://intellij-rust.github.io/docs/quick-start.html). 

Unstable master branch builds can be downloaded from [TeamCity].

## Compatible IDEs

The plugin is compatible with all IntelliJ-based IDEs starting from the version 2019.2, with the following differences in the sets of the available features:


|                        | Open-source and Educational IDEs<sup>*</sup> | [CLion] (commercial) | [IntelliJ IDEA] Ultimate, [PyCharm] Professional, other commercial IDEs |
|------------------------|---|---|---|
| Language support       | + | + | + |
| Cargo support          | + | + | + |
| Code coverage          | + | +<sup>**</sup> | + |
| Debugger               | - | + | - |
| Profiler               | - | + | - |
| Valgrind Memcheck      | - | + | - |
| [Detecting duplicates] | - | + | + |


\* [IntelliJ IDEA] Community Edition, [PyCharm] Community Edition, [PyCharm Edu and IntelliJ IDEA Edu].

<!-- BACKCOMPAT: 2019.2 -->
\** Since CLion 2019.3

## TOML

If you are looking for the TOML plugin, see [intellij-toml] directory.

## Contributing

You're encouraged to contribute to the plugin if you've found any
issues or missing functionality that you would want to see. Check out
[CONTRIBUTING.md] to learn how to set up the project and [ARCHITECTURE.md] to
understand the high-level structure of the codebase. If you are not sure where to start, consider the issues tagged with [help wanted].

[intellij-rust.github.io]: https://intellij-rust.github.io/docs/
[website]: https://intellij-rust.github.io/docs/faq.html
[help wanted]: https://github.com/intellij-rust/intellij-rust/labels/help%20wanted
[CONTRIBUTING.md]: CONTRIBUTING.md
[ARCHITECTURE.md]: ARCHITECTURE.md
[TeamCity]: https://teamcity.jetbrains.com/guestAuth/repository/download/IntellijIdeaPlugins_Rust_192_TestIdea/.lastSuccessful/intellij-rust-0.2.104.{build.number}-192-dev.zip
[intellij-toml]: intellij-toml/

<!-- Badges -->
[gitter-chat]: https://gitter.im/intellij-rust/intellij-rust
[gitter-chat-svg]: https://badges.gitter.im/Join%20Chat.svg

[plugin-repo]: https://plugins.jetbrains.com/plugin/8182-rust
[plugin-version-svg]: https://img.shields.io/jetbrains/plugin/v/8182-rust.svg
[plugin-downloads-svg]: https://img.shields.io/jetbrains/plugin/d/8182-rust.svg

[travis-build-status]: https://travis-ci.org/intellij-rust/intellij-rust?branch=master
[travis-build-status-svg]: https://travis-ci.org/intellij-rust/intellij-rust.svg?branch=master

[appveyor-build-status]: https://ci.appveyor.com/project/intellij-rust/intellij-rust/branch/master
[appveyor-build-status-svg]: https://ci.appveyor.com/api/projects/status/xf8792c7p3637060?svg=true

[teamcity-build-status]: https://teamcity.jetbrains.com/viewType.html?buildTypeId=IntellijIdeaPlugins_Rust_192_TestIdea&guest=1
[teamcity-build-status-svg]: https://teamcity.jetbrains.com/app/rest/builds/buildType:IntellijIdeaPlugins_Rust_192_TestIdea/statusIcon.svg

[stable-build-status]: https://teamcity.jetbrains.com/viewType.html?buildTypeId=IntellijIdeaPlugins_Rust_192_UploadStable&guest=1
[stable-build-status-svg]: https://teamcity.jetbrains.com/app/rest/builds/buildType:IntellijIdeaPlugins_Rust_192_UploadStable/statusIcon.svg

[beta-build-status]: https://teamcity.jetbrains.com/viewType.html?buildTypeId=IntellijIdeaPlugins_Rust_192_UploadBeta&guest=1
[beta-build-status-svg]: https://teamcity.jetbrains.com/app/rest/builds/buildType:IntellijIdeaPlugins_Rust_192_UploadBeta/statusIcon.svg

[nightly-build-status]: https://teamcity.jetbrains.com/viewType.html?buildTypeId=IntellijIdeaPlugins_Rust_192_UploadNightly&guest=1
[nightly-build-status-svg]: https://teamcity.jetbrains.com/app/rest/builds/buildType:IntellijIdeaPlugins_Rust_192_UploadNightly/statusIcon.svg


[IntelliJ IDEA]: https://www.jetbrains.com/idea/
[CLion]: https://www.jetbrains.com/clion/
[PyCharm]: https://www.jetbrains.com/pycharm/
[PyCharm Edu and IntelliJ IDEA Edu]: https://www.jetbrains.com/education
[Detecting duplicates]: https://www.jetbrains.com/help/idea/analyzing-duplicates.html
