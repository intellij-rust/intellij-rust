# Rust IDE built using the IntelliJ Platform

[![Build Status](https://teamcity.jetbrains.com/app/rest/builds/buildType:(id:IntellijIdeaPlugins_Rust_Idea16_TestsRust_2)/statusIcon.svg)](https://teamcity.jetbrains.com/viewType.html?buildTypeId=IntellijIdeaPlugins_Rust_Idea16_TestsRust_2&guest=1) [![Build Status](https://img.shields.io/travis/intellij-rust/intellij-rust/master.svg)](https://travis-ci.org/intellij-rust/intellij-rust) [![Join the chat at https://gitter.im/intellij-rust/intellij-rust](https://img.shields.io/badge/Gitter-Join%20Chat-blue.svg)](https://gitter.im/intellij-rust/intellij-rust?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

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
