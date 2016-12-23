# Getting started

## Clone

```
git clone https://github.com/intellij-rust/intellij-rust.git
cd intellij-rust
```


## Building

We use gradle to build the plugin. It comes with a wrapper script (`gradlew` in
the root of the repository) which downloads appropriate version of gradle
automatically as long as you have JDK installed.

Common tasks are

  - `./gradlew :build` -- fully build plugin and create an archive at
    `build/distributions` which can be installed into IntelliJ IDEA via `Install
    plugin from disk...` action found in `Settings > Plugins`.

  - `./gradlew :runIdea` -- run a development IDE with the plugin installed.

  - `./gradlew :test` -- more than a thousand tests. We love tests!

  - `./gradlew :performanceTest` -- a couple of high level performance tests.

Note the `:` in front of the task name. The repository contains two independent
plugins for Rust and TOML, which are organized as gradle subprojects. Running
`./gradle :task` executes the task only for Rust plugin, `:toml:task` will run
the task for TOML and `./gradle task` will do for both.


## Development in Intellij IDEA

You can get the latest Intellij IDEA Community Edition
[here](https://www.jetbrains.com/idea/download/).

Import the plugin project as you would do with any other gradle based project.
For example, `Ctrl + Shift + A`, `Import project` and select `build.gradle` from
the root directory of the plugin.

To run or debug plugin from within the IDE use "gradle task" run configuration
for `:runIdea` task.

To run tests, execute `:test` gradle task, or navigate to the interesting test
method, class or package and press `Ctrl+Shift+F10`.


You might want to install the following plugins:
  - Grammar Kit to get highlighting for the files with BNFish grammar.
  - PSI viewer to view the AST of Rust files right in the IDE.


# Contributing

To find an problem to work on, look for
[up-for-grab](https://github.com/intellij-rust/intellij-rust/labels/up%20for%20grab)
issues on Github, or, even better, try to fix a problem you face yourself when
using the plugin.

To familiarize yourself with the plugin source code, read the architecture
section later in this document and look at some existing pull requests. Please
do ask any questions in our
[Gitter](https://gitter.im/intellij-rust/intellij-rust)!


Work in progress pull requests are very welcome! It is also a great way to ask
questions.

Here are some example pull requests:

  - Adding an inspection: [#713](https://github.com/intellij-rust/intellij-rust/pull/713/).

  - Adding an intention: [#318](https://github.com/intellij-rust/intellij-rust/pull/318/).

  - Adding a gutter icon: [#758](https://github.com/intellij-rust/intellij-rust/pull/758).


## Code style

Please use **reformat code** action to maintain consistent style. Pay attention
to IDEA's warning and suggestions, and try to keep the code green. If you are
sure that the warning is false positive, use an annotation to suppress it.

Try to avoid copy-paste and boilerplate as much as possible. For example,
proactively use `?:` and `?.let` to deal with nullable values.


### Commit Messages

Consider prefixing commit with a `(TAG):` which describes the area of the
change. Common tags are:

  * PSI
  * CARGO
  * GRD for build changes
  * FMT for formatter
  * GRAM for grammar changes
  * T for tests
  * RES for resolve
  * TYPE for type iference

Try to keep the summary line of a commit message under 50 characters.


# Testing

It is much easier to understand code changes if they are accompanied with tests.
Most tests are fixture-driven. They typically:
  1. Load a rust file that represents the initial state
  2. Execute your method under test
  3. Verify the final state, which may also be represented as a fixture


#### Structure

All test classes are placed in the `src/test/kotlin` directory. There are two
ways of providing fixtures for the tests. The first one is to put Rust files
in `src/test/resources`.

In the example below `RustFormatterTest.kt` is the test class, `blocks.rs` is
the fixture for the initial state and `blocks_after.rs` is the fixture for the
final state. It is good practice to put fixtures in the same package as tests.

     +-- src/test/kotlin
     |    +-- org/rust/ide/formatter
     |        +-- RustFormatterTest.kt
     |
     +-- src/test/resources
         +-- org/rust/ide/formatter
             +-- fixtures
                 +-- blocks.rs
                 +-- blocks_after.rs

Another way of providing fixtures is to use Kotlin's tripple quoted multiline
string literals. You can get Rust syntax highlighting inside them if you have a
`@Language("Rust")` annotation applied. You can see an example
[here](https://github.com/intellij-rust/intellij-rust/blob/b5e680cc80e90523610016e662a131985aa88e56/src/test/kotlin/org/rust/ide/intentions/MoveTypeConstraintToWhereClauseIntentionTest.kt).

In general, triple quoted string fixtures should be prefered over separate Rust files.


#### Fixtures

Fixture files are very simple: they're rust code! Output fixtures on the other
hand, can be rust code over which you've run an action, HTML (for generated
documentation) or any other output you'd like to verify. Output fixtures have
the same filename as the initial fixture, but with `_after` appended.

Continuing with our example above, our initial fixture `blocks.rs` could look
like:

    pub fn main() {
    let x = {
    92
    };
    x;
    }

While our expected-output fixture `blocks_after.rs` contains:

    pub fn main() {
        let x = {
            92
        };
        x;
    }

Some tests are dependent on the position of the editor caret. Fixtures support a
special marker `<caret>` for this purpose. Multiple such markers for more
complex tests. An example of a fixture with a caret:

    pub fn main>() {
      let _ = S {
        <caret>
    };


#### Test Classes

Test classes are JUnit and written in Kotlin. They specify the resource path in
which fixtures are found and contain a number of test methods. Test methods
follow a simple convention: their name is the initial fixture name camel-cased.
For example, `RustFormatterTest.kt` would look like:

    class RustFormatterTest: FormatterTestCase() {
        override fun getTestDataPath() = "src/test/resources"
        override fun getFileExtension() = "rs"

        fun testBlocks() = stubOnlyResolve()
    }

The test method `testBlocks` states that this test uses `blocks.rs` as the
initial fixture and `blocks_after.rs` as the expected output fixture. A more
complicated fixture name like `a_longer_fixture_name.rs` would use the test
method `testALongerFixtureName()`.


# Pull requests best practices

It's much easier to review small, focused pull requests. If you can split your
changes into several pull requests then please do it. There is no such thing as
a "too small" pull request.

Here is my typical workflow for submitting a pull request. You don't need to
follow it exactly. I will show command line commands, but you can use any git
client of your choice.

First, I press the fork button on the GitHub website to fork
`https://github.com/intellij-rust/intellij-rust` to
`https://github.com/matklad/intellij-rust`. Then I clone my fork:

```
$ git clone git://github.com/matklad/intellij-rust && cd intellij-rust
```

The next thing is usually creating a branch:

```
$ git checkout -b "useful-fix"
```

I can work directly on my fork's master branch, but having a dedicated PR branch
helps if I want to synchronize my work with upstream repository or if I want to
submit several pull requests.

Usually I try to keep my PRs one commit long:

```
$ hack hack hack
$ git commit -am"(INSP): add a useful inspection"
$ ./gradlew test && git push -u origin useful-fix
```

Now I am ready to press "create pull request" button on the GitHub website!


## Incorporating code review suggestions

If my pull request consists of a single commit then to address the review I just
push additional commits to the pull request branch:

```
$ more hacking
$ git commit -am"Fix code style issues"
$ ./gradlew test && git push
```

I don't pay much attention to the commit messages, because after everything is
fine the PR will be squash merged as a single good commit.

If my PR consists of several commits, then the situation is a bit tricky. I like
to keep the history clean, so I do some form of the rebasing:

```
$ more hacking
$ git add .
$ git commit --fixup aef92cc
$ git rebase --autosquash -i HEAD~3
```

And then I force push the branch

```
$ ./gradlew test && git push --force-with-lease
```


## Updating the pull request to solve merge conflicts

If my PR starts to conflict with the upstream changes, I need to update it.
First, I add the original repository as a remote, so that I can pull changes
from it.

```
$ git remote add upstream https://github.com/intellij-rust/intellij-rust
$ git fetch upstream
$ git merge upstream/master master  # The dedicated PR branch helps a lot here.
```

Then I rebase my work on top of the updated master:

```
$ git rebase master useful-fix
```

And now I need to force push the PR branch:

```
$ ./gradlew test && git push --force-with-lease
```


# Plugin architecture

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
