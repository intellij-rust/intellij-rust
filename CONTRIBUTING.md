# Getting started

## Clone the repository with submodules

```
git clone https://github.com/alexeykudinkin/intellij-rust.git
cd intellij-rust
git submodule update --init
```

## Get IDEA

Download the latest Intellij IDEA Community Edition EAP
[here](https://confluence.jetbrains.com/display/IDEADEV/EAP). If you are using
Arch Linux, there is `intellij-idea-ce-eap` package in the AUR.

## Import project

`Ctrl + Shift + A, Import project` or `File > New > Project from existing
sources`, select `build.gradle` from the root directory of the plugin and press
`OK`.


## Running

From Gradle Tool Window or from `Ctrl + Shift + A, Execute Gradle Task` run
`test` task. Run `runIdea` task to launch the plugin.


# Plugin overview

We use Kotlin language for the plugin. If you can program in Java or Rust, you
should be able to read/write Kotlin right away. Learn more about [Kotlin](https://kotlinlang.org/).

See the IDEA platform [documentation](http://www.jetbrains.org/intellij/sdk/docs/).
Of a particular interest are the following sections:
  * custom language
  [tutorial](http://www.jetbrains.org/intellij/sdk/docs/tutorials/custom_language_support_tutorial.html).
  * custom language support
  [reference](http://www.jetbrains.org/intellij/sdk/docs/reference_guide/custom_language_support.html)


It's also very inspirational to browse existing plugins. Check out
[Erlang](https://github.com/ignatov/intellij-erlang) and
[Go](https://github.com/go-lang-plugin-org/go-lang-idea-plugin) plugins.

# Testing

It is much easier to understand code changes if they are accompanied with
tests. Test classes are placed in the `test` directory, `testData` holds rust files,
which are used as fixtures. We also include
[CTRS](https://github.com/brson/ctrs) as a submodule and use it to test the
parser.
