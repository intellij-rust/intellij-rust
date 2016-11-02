# Getting started

## Clone

```
git clone https://github.com/intellij-rust/intellij-rust.git
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

You can get the latest Intellij IDEA Community Edition [here](https://www.jetbrains.com/idea/download/).
You can also get the version from EAP channel [here](https://confluence.jetbrains.com/display/IDEADEV/EAP).

Import the plugin project as you would do with any other gradle based project.
For example, `Ctrl + Shift + A`, `Import project` and select `build.gradle` from
the root directory of the plugin.

To run or debug plugin from within the IDE use "gradle task" run configuration
for `runIdea` task. Another important task is `generate`. Use it if you change
`.flex` or `.bnf` files to generate the lexer and the parser.

To run test, execute "test" gradle task, or navigate to the interesting test
method, class or package and press `Ctrl+Shift+F10`.


# Contributing

To contribute to the code of the project check out the the latest version and follow instructions on how to build & run it.

If you feel yourself new to the field or don't know what you should start coping with check out issue-tracker for the
[up-for-grab](https://github.com/intellij-rust/intellij-rust/labels/up%20for%20grab) tasks as these may be greatest entry points to the project source code.

It's also very useful to submit fixes for the problems you face yourself when
using the plugin.

If you want to contribute to the project in any of the numerous other ways, first of all consider joining our [Gitter](https://gitter.im/alexeykudinkin/intellij-rust?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge) therefore
being on par with the current development efforts.

Feel free to submit a work-in-progress pull request to collect early feedback.
When you are done with the PR, please make sure that all tests pass.


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
  * MISC for anything else :)

Try to keep the summary line of a commit message under 50 characters.

# Testing

It is much easier to understand code changes if they are accompanied with tests.
Most tests are fixture-driven. They typically:
  1. Load a rust file that represents the initial state
  2. Execute your method under test
  3. Verify the final state, which may also be represented as a fixture

#### Structure

All test classes are placed in the `src/test/kotlin` directory, while
accompanying fixtures are placed in`src/test/resources`.

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

#### Fixtures

Fixture files are very simple: they're rust code! Output fixtures
on the other hand, can be rust code over which you've run an action,
HTML (for generated documentation) or any other output you'd like
to verify. Output fixtures have the same filename as the initial
fixture, but with `_after` appended.

Continuing with our example above, our initial fixture `blocks.rs`
could look like:

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

Some tests are dependent on the position of the editor caret. Fixtures support
a special marker `<caret>` for this purpose. Multiple such markers for more complex
tests. An example of a fixture with a caret:

    pub fn main>() {
      let _ = S {
        <caret>
    };

#### Test Classes

Test classes are JUnit and written in Kotlin. They specify the resource path
in which fixtures are found and contain a number of test methods. Test methods
follow a simple convention: their name is the initial fixture name camel-cased.
For example, `RustFormatterTest.kt` would look like:

    class RustFormatterTest: FormatterTestCase() {
        override fun getTestDataPath() = "src/test/resources"
        override fun getFileExtension() = "rs"

        fun testBlocks() = doTest()
    }

The test method `testBlocks` states that this test uses `blocks.rs` as the
initial fixture and `blocks_after.rs` as the expected output fixture. A more
complicated fixture name like `a_longer_fixture_name.rs` would use the
test method `testALongerFixtureName()`.

#### Running

We use the Rust compiler and standard library as data in our test suite. They
are downloaded automatically by gradle.

To run the test suite, use:

    ./gradlew test

To launch parser performance tests, use

    ./gradlew performanceTest

There is a special TeamCity configuration for tracking [performance metrics].

[performance metrics]: https://teamcity.jetbrains.com/project.html?projectId=IntellijIdeaPlugins_Rust&tab=stats

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
should be able to read/write Kotlin right away. Learn more about [Kotlin](https://kotlinlang.org/).

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

The plugin is composed of three packages: `org.rust.lang`, `org.rust.ide` and `org.rust.cargo`.

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
implementations. Use **View PSI Structure of Current File** action to explore PSI,
or install the PSI viewer plugin.

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
