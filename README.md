# Rust IDE built using the IntelliJ Platform

[![Build Status](https://teamcity.jetbrains.com/app/rest/builds/buildType:(id:IntellijIdeaPlugins_Rust_Tests)/statusIcon.svg)](https://teamcity.jetbrains.com/viewType.html?buildTypeId=IntellijIdeaPlugins_Rust_Tests&guest=1) [![Build Status](https://img.shields.io/travis/intellij-rust/intellij-rust/master.svg)](https://travis-ci.org/intellij-rust/intellij-rust) [![Join the chat at https://gitter.im/intellij-rust/intellij-rust](https://img.shields.io/badge/Gitter-Join%20Chat-blue.svg)](https://gitter.im/intellij-rust/intellij-rust?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

**NOTA BENE**

This is experimental implementation targeting bleeding-edge version of Rust language and (as may be assumed reasonably enough) 
may be highly *unstable* just yet.

## Installation

At the moment we ship pre-release builds in the nightly channel _only_.

To use them you need to follow the [instructions](https://www.jetbrains.com/idea/help/managing-enterprise-plugin-repositories.html) of adding 
additional plugin repository and paste the URL for the one you need:
     
    - Rust: https://plugins.jetbrains.com/plugins/nightly/8182
    - TOML: https://plugins.jetbrains.com/plugins/nightly/8195 

*NOTA BENE*: To successfully import Cargo-backed project you will need a fairly recent version of `cargo` installed 
(at least `cargo 0.9.0-nightly (6c05bcb 2016-01-29)`). To install nightly version of the Cargo please follow the 
instructions at [crates.io](https://crates.io/install). Note that you can use nightly Cargo with stable version of Rust just fine. 

*NOTA BENE*: To use Run Configurations with IDEA 15.0.3 please update your Kotlin plugin.

As this is a preview release, something might go wrong. So, be brave to face some nasty bugs.

## Building

```
$ git clone https://github.com/intellij-rust/intellij-rust --recursive
$ cd intellij-rust
$ ./gradlew buildPlugin
```

This creates a zip archive in `build/distributions` which you can install with the `Install plugin from disk...` action found in `Settings > Plugins`.

Note that it will download all build dependencies, so make sure you have enough space (one GB should be enough). 
You need IDEA 15 (or newer) to use this plugin. 

## Usage

See the [usage docs](doc/Usage.md).

## Bugs

Current high-volatility state entails no support just yet, so be patient, please, and save your anger until it hits stability milestone (at least)
 Until then, no crusade to fight any issues (even completely relevant ones) may be possible.

## Contributing

You're encouraged to contribute to the plugin in any form if you've found any issues or missing
functionality that you'd want to see. In order to get started, check out
[CONTRIBUTING.md](CONTRIBUTING.md) guide.
