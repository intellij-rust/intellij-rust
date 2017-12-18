# Kotlin

We use [Kotlin] language for the plugin. If you can program in Java, you should
be able to read and write Kotlin code right away. Kotlin is deeply similar to
Java, but has less verbose syntax and better safety. It also shares some
characteristics with Rust: type inference, immutability by default, no null
pointers (apart from those that come from Java).

If you are unsure how to implement something in Kotlin, ask a question in our
[Gitter], send a PR in Java, or use **Convert Java to Kotlin** action in the
IDE.

[Kotlin]: https://kotlinlang.org/

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

  - `./gradlew :runIde` -- run a development IDE with the plugin installed.

  - `./gradlew :test` -- more than two thousands tests. We love tests!

Note the `:` in front of the task name. The repository contains two independent
plugins for Rust and TOML, which are organized as gradle subprojects. Running
`./gradlew :task` executes the task only for Rust plugin, `:toml:task` will run
the task for TOML and `./gradlew task` will do for both.


## Development in Intellij IDEA

You can get the latest Intellij IDEA Community Edition
[here](https://www.jetbrains.com/idea/download/), it is free.

Import the plugin project as you would do with any other gradle based project.
For example, <kbd>Ctrl + Shift + A</kbd>, `Import project` and select `build.gradle.kts` from
the root directory of the plugin.

There are `Test`, `Run` and `Generate Parser` run configurations for the most
common tasks. However try executing `./gradlew :test` first, to download all
necessary dependencies and launch all code generation tasks. Unfortunately
during import IDEA may delete `.idea/runConfigurations`, just revert changes in
the directory if this happens.

You might want to install the following plugins:
  - Grammar Kit to get highlighting for the files with BNFish grammar.
  - PSI viewer to view the AST of Rust files right in the IDE.


# Contributing

To find a problem to work on, look for
[`help wanted`](https://github.com/intellij-rust/intellij-rust/labels/help%20wanted)
issues on Github, or, even better, try to fix a problem you face yourself when
using the plugin.

When choosing an issue, you can navigate by the `E-` labels. They describe an
experience needed to solve an issue (`E-easy`, `E-medium`, `E-hard` or `E-unknown`).
The [`E-mentor`] label means that someone knows how to fix the issue and most likely
they provided some instructions about how to fix it, links to the relevant code
and so on. If you are looking for a good first issue, `E-mentor`-labeled one is the
best choice.

[`help-wanted`]: https://github.com/intellij-rust/intellij-rust/labels/help%20wanted
[`E-mentor`]: https://github.com/intellij-rust/intellij-rust/labels/E-mentor

See [worklist](https://github.com/intellij-rust/intellij-rust/wiki/Worklist) to learn 
who is doing what right now. This page is a wiki, so feel free to edit it and to write 
about your plans.

To familiarize yourself with the plugin source code, read
the [architecture](ARCHITECTURE.md) document and look at some existing pull
requests. Please do ask questions in our [Gitter]!


Work in progress pull requests are very welcome! It is also a great way to ask
questions.

Here are some example pull requests:

  - Adding an inspection: [#713](https://github.com/intellij-rust/intellij-rust/pull/713/).

  - Adding an intention: [#318](https://github.com/intellij-rust/intellij-rust/pull/318/).

  - Adding a gutter icon: [#758](https://github.com/intellij-rust/intellij-rust/pull/758).


## CLA

We require a contributor license agreement for all contributions. You can sign it 
electronically at https://www.jetbrains.com/agreements/cla/ via DocuSign. The procedure
consists of a couple of emails and clicks: no need to scan or print or snail-mail anything :)
`CONTRIBUTORS.txt` file stores GitHub usernames of people who already signed a CLA.
  
The text of the agreement can be found [here](http://www.jetbrains.org/download/attachments/983565/JB_ContributorLicenseAgreement.pdf). 

## Code style

Please use **reformat code** action to maintain consistent style. Pay attention
to IDEA's warning and suggestions, and try to keep the code green. If you are
sure that the warning is false positive, use an annotation to suppress it.

Try to avoid copy-paste and boilerplate as much as possible. For example,
proactively use `?:` to deal with nullable values.

If you add a new file, please make sure that it contains a license preamble, as all
other files do. 


### Commit Messages

Consider prefixing commit with a `TAG:` which describes the area of the
change. Common tags are:

  * GRAM for changes to `.bnf` files
  * PSI for other PSI related changes
  * RES for name resolution
  * TY for type inference
  * COMP for code completion
  * STUB for PSI stubs
  * MACRO for macro expansion
  
  
  * FMT for formatter
  * TYPE for editor-related functions
  * ANN for error highlighting and annotators
  * INSP for inspections
  * INT for intentions
  * RUN for run configurations
  * QDOC for quick documentation
  * PERF for performance optimizations
  * PRJ for project creation changes 
  * ACT for actions


  * CARGO for cargo-related changes
  * GRD for build changes
  * T for tests
  * DOC for documentation

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


[Gitter]: https://gitter.im/intellij-rust/intellij-rust
