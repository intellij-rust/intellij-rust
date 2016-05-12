# Getting started

## Clone

```
git clone --recursive https://github.com/intellij-rust/intellij-rust.git
cd intellij-rust
```

## Building

To build the plugin just navigate to the directory you've previously checked it out and type `./gradlew build`. That's it.

This creates a zip archive in `build/distributions` which you can install with the `Install plugin from disk...` action found in `Settings > Plugins`.

## Running

To launch the plugin from console just type `./gradlew runIdea`.


## Development

For development you could use any editor/IDE of your choice. There is no peculiar dependency on IDEA, though we're particularly stuck to it, therefore providing instructions how to nail it from that side.

If you're using any other particular stack feel free to contribute to that list.

* [IDEA](#IDEA)


### IDEA

For development you'd need IDEA with Kotlin plugin (which is shipped by default starting from 14.1)

You can get the latest Intellij IDEA Community Edition [here](https://www.jetbrains.com/idea/download/).
You can also get the version from EAP channel [here](https://confluence.jetbrains.com/display/IDEADEV/EAP).

To import the project just: `Ctrl + Shift + A`, `Import project` or `File`, `New`, `Project from existing
sources`, and select `build.gradle` from the root directory of the plugin.


# Contributing

To contribute to the code of the project check out the the latest version and follow instructions on how to build & run it.

If you feel yourself new to the field or don't know what you should start coping with check out issue-tracker for the
[up-for-grab](https://github.com/intellij-rust/intellij-rust/labels/up%20for%20grab) tasks as these may be greatest entry points to the project source code.

If you want to contribute to the project in any of the numerous other ways, first of all consider joining our [Gitter](https://gitter.im/alexeykudinkin/intellij-rust?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge) therefore
being on par with the current development efforts.

**NOTA BENE**

Please, make sure that all tests pass and Travis reports build as green prior to making a pull request.


## Code style

Please, consider our [code-style](STYLE.md) prior to submitting the pull request. We love PRs, but much more than just PRs we love PRs that are 'on style'.

For the Java sources we're stuck to the Google's [one](https://google.github.io/styleguide/javaguide.html).

## PR style

It's much easier to review small, focused pull requests. So if you can split your changes into several chunks then
please do it.


# Testing

It is much easier to understand code changes if they are accompanied with
tests. Test classes are placed in the `src/test/kotlin` directory, `src/test/resources` holds rust files
which are used as fixtures. We also include
[CTRS](https://github.com/brson/ctrs) as a submodule and use it to test the
parser.

The test suite can be run by launching:

    ./gradlew test

You can execute specific test from within IDE. To do so, navigate to the tests
and press `Ctrl+Shift+F10`. Note that IDEA test runner may not run gradle code
generation tasks, so if you are modifying `rust.bnf` or `RustLexer.flex`, you
may want to use the gradle runner instead.

# Plugin architecture

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
[Go](https://github.com/go-lang-plugin-org/go-lang-idea-plugin) plugins. There
is also [plugin repository](https://github.com/JetBrains/intellij-plugins) and,
of course, [Kotlin](https://github.com/JetBrains/kotlin).

## Packages

The plugin is composed of three packages: `org.rust.lang`, `org.rust.ide` and `org.rust.cargo`.

The `lang` package is the heart of the plugin. It includes Rust Program
Structure Interface classes (PSI) which describe a contents of a Rust program.
The `lang` package is responsible for the semantic analysis of PSI.
`lang.resolve` package maps variables and paths to their definitions,
`lang.completion` package provides semantic completion.

The `cargo` package is used for integration with Cargo build tool. Path to Cargo
is configured via `RustProjectSettingsService`. `CargoProjectWorkspace` invokes
`cargo metadata` command and stores information about a project as
`module.cargoProject` and sets up libraries for indexing. `cargo.commands.Cargo`
class and `cargo.runconfig` package run Cargo commands for building, running and
testing.

The `ide` package uses `cargo` and `lang` packages to provide useful
functionality for the user. It consists of numerous sub packages. Some of them
are

* `intentions`: actions that the user can invoke with `Alt+Enter`,
* `inspections`: warnings and quick fixes,
* `navigation.goto`: leverages `lang.core.stubs.index` to provide GoToSymbol action.

## PSI

You can think of PSI as an AST, but it is more general. For one thing, it
includes things which are usually omitted from an AST: whitespace, comments and
parenthesis. PSI is a facade for program structure, which can have several
implementations.

The main implementation is the AST of a file with the Rust source code. The
grammar is specified in the `rust.bnf` file. Grammar Kit uses it to generate PSI
classes and the parser. The PSI classes are extended via `lang.core.impl`
package.

Another possible implementation of PSI is compiled code: in theory we can parse
Rust binary rlibs and build PSI from them.

And yet another implementation is a stub tree. Stub tree is a condensed AST,
which includes information necessary for the resolve and nothing more. That is,
`struct`s and functions declarations are present in stubs, but function bodies
and local variables are omitted. Stubs are build from source files during
indexing and are stored in binary format. The cool thing is that PSI can
dynamically
[switch](https://github.com/intellij-rust/intellij-rust/blob/c7769a68d14a02ced8fd4b307b5ae1f0443e1fd4/src/main/kotlin/org/rust/lang/core/psi/impl/RustStubbedNamedElementImpl.kt#L21)
between stub based and AST based implementation. You can read more in the
[documentation](http://www.jetbrains.org/intellij/sdk/docs/basics/indexing_and_psi_stubs.html)
and the [source
code](https://github.com/intellij-rust/intellij-rust/tree/master/src/main/kotlin/org/rust/lang/core/stubs)

# Adding new intentions and inspections.

Tests for intentions are similar and data driven: you specify `before.rs`,
`after.rs` and the name of the intention to invoke. So it makes sense to start
with the test.

Here is [an
example](https://github.com/intellij-rust/intellij-rust/blob/aa9db9c70e07a8175ef4fe043fd2438dd9788b26/src/test/kotlin/org/rust/ide/intentions/RustIntentionsTest.kt).
`dataPath` specifies path to the test files inside `src/test/resources`
directory. The names of the test files themselves are derived from the name of the test
function.

And here is the
[intention](https://github.com/intellij-rust/intellij-rust/blob/aa9db9c70e07a8175ef4fe043fd2438dd9788b26/src/main/kotlin/org/rust/ide/intentions/ExpandModule.kt)
itself. Don't forget to
[register](https://github.com/intellij-rust/intellij-rust/blob/aa9db9c70e07a8175ef4fe043fd2438dd9788b26/src/main/resources/META-INF/plugin.xml#L132-L135)
it in the `plugin.xml`.

Check [sdk docs](http://www.jetbrains.org/intellij/sdk/docs/index.html) for more information.