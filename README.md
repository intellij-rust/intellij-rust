# Rust IDE built using the IntelliJ Platform

[![Build Status](https://img.shields.io/travis/alexeykudinkin/intellij-rust/master.svg)](https://travis-ci.org/alexeykudinkin/intellij-rust) [![Join the chat at https://gitter.im/alexeykudinkin/intellij-rust](https://img.shields.io/badge/Gitter-Join%20Chat-blue.svg)](https://gitter.im/alexeykudinkin/intellij-rust?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge) 

**BEWARE**

This is experimental implementation targeting bleeding-edge version of Rust language and (as may be assumed reasonably enough) 
is highly *unstable* just yet.

## Installation

We intentionally do not provide a download for the plugin just yet. If you are
brave enough and want to use the plugin, you have to build it from source. Note that it will download all build dependencies, so make sure you have enough space (one GB should be enough). IDEA 15 is required. 

Building:

```
$ git clone https://github.com/intellij-rust/intellij-rust
$ cd intellij-rust
$ ./gradlew buildPlugin
```

This creates a zip archive in `build/distributions` which you can install with
`install plugin from disk` action.


## Usage

See the [usage docs](doc/Usage.md).

## Bugs

Current high-volatility state entails no support just yet, so be patient, please, and save your anger until it hits stability milestone (at least)
 Until then, no crusade to fight any issues (even completely relevant ones) may be possible.

## Contributing

You're encouraged to contribute to the plugin in any form if you've found any issues or missing
functionality that you'd want to see. In order to get started, check out
[CONTRIBUTING.md](CONTRIBUTING.md) guide.
