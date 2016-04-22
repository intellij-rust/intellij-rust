# Rust IDE built using the IntelliJ Platform

[![Build Status](https://teamcity.jetbrains.com/app/rest/builds/buildType:(id:IntellijIdeaPlugins_Rust_Tests)/statusIcon.svg)](https://teamcity.jetbrains.com/viewType.html?buildTypeId=IntellijIdeaPlugins_Rust_Idea16_TestsRust&guest=1) [![Build Status](https://img.shields.io/travis/intellij-rust/intellij-rust/master.svg)](https://travis-ci.org/intellij-rust/intellij-rust) [![Join the chat at https://gitter.im/intellij-rust/intellij-rust](https://img.shields.io/badge/Gitter-Join%20Chat-blue.svg)](https://gitter.im/intellij-rust/intellij-rust?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

**NOTA BENE**

This is a work in progress and (as may be assumed reasonably enough) may be highly *unstable* just yet.

## Installation

At the moment we ship pre-release builds in the nightly channel _only_.

To use them you need to follow the [instructions](https://www.jetbrains.com/idea/help/managing-enterprise-plugin-repositories.html) of adding
additional plugin repository and paste the URL for the one you need:

    - Rust: https://plugins.jetbrains.com/plugins/nightly/8182
    - TOML: https://plugins.jetbrains.com/plugins/nightly/8195

See the [quick start](doc/QuickStart.md) and [tips & tricks](doc/Tips.md) for tutorial and screenshots!

*NOTA BENE*: To successfully import Cargo-backed project you will need Cargo
 from at least 1.8.0 toolchain, which is the current beta. You can use any
 version of the compiler.

As this is a preview release, something might go wrong. So, be brave to face some nasty bugs.

## Contributing

You're encouraged to contribute to the plugin in any form if you've found any issues or missing
functionality that you'd want to see. In order to get started, check out
[CONTRIBUTING.md](CONTRIBUTING.md) guide and [up-for-grab](https://github.com/intellij-rust/intellij-rust/labels/up%20for%20grab) tasks.
