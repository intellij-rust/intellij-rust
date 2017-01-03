We use Kotlin language for the plugin. If you can program in Java or Rust, you
should be able to read/write Kotlin right away. Learn more about
[Kotlin](https://kotlinlang.org/).

See the IDEA platform [documentation](http://www.jetbrains.org/intellij/sdk/docs/).
Of a particular interest are the following sections:
  * custom language
  [tutorial](http://www.jetbrains.org/intellij/sdk/docs/tutorials/custom_language_support_tutorial.html).
  * custom language support
  [reference](http://www.jetbrains.org/intellij/sdk/docs/reference_guide/custom_language_support.html)

If you find any piece of SDK docs missing or unclear, do open an issue at
[YouTrack](https://youtrack.jetbrains.com/issues/IJSDK). You can also
[contribute directly](https://github.com/JetBrains/intellij-sdk-docs/blob/master/CONTRIBUTING.md)
to the plugin development docs.

It's also very inspirational to browse existing plugins. Check out
[Erlang](https://github.com/ignatov/intellij-erlang) and
[Go](https://github.com/go-lang-plugin-org/go-lang-idea-plugin) plugins. There
is also [plugin repository](https://github.com/JetBrains/intellij-plugins) and,
of course, [Kotlin](https://github.com/JetBrains/kotlin).


## Packages

The plugin is composed of three packages: `org.rust.lang`, `org.rust.ide` and
`org.rust.cargo`.

The `lang` package is the heart of the plugin. It includes Rust Program
Structure Interface classes (PSI) which describe a contents of a Rust program.
The `lang` package is responsible for the semantic analysis of PSI.
`lang.core.resolve` package maps variables and paths to their definitions,
`lang.core.completion` package provides semantic completion, and
`lang.core.types` implements type inference.

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
implementations. Use **View PSI Structure of Current File** action to explore
PSI, or install the PSI viewer plugin and use `Ctrl+Shift+Q` shortcut.

The main implementation is the AST of a file with the Rust source code. The
grammar is specified in the `RustGrammar.bnf` file. Grammar Kit uses it to
generate PSI classes and the parser. The PSI classes are extended via
`lang.core.impl` package.

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
and the
[source code](https://github.com/intellij-rust/intellij-rust/tree/master/src/main/kotlin/org/rust/lang/core/stubs)
