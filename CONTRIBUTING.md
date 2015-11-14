# Getting started

## Clone

```
git clone --recursive https://github.com/alexeykudinkin/intellij-rust.git
cd intellij-rust
```

## Building

To build the plugin just navigate to the directory you've previously checked it out and type `./gradlew build`. That's it.  


## Running

To launch the plugin from console just type `./gradlew runIdea`.


## Development

For development you could use any editor/IDE of your choice. There is no peculiar dependency on IDEA, though we're particularly sticked to it, therefore providing instructions how to nail it from that side. 

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
[up-for-grab](https://github.com/alexeykudinkin/intellij-rust/labels/up%20for%20grab) tasks as these may be greatest entry points to the project source code.

If you want to contribute to the project in any of the numerous other ways, first of all consider joining our [Gitter](https://gitter.im/alexeykudinkin/intellij-rust?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge) therefore
being on par with the current development efforts.

**NOTA BENE**

Please, make sure that all tests pass and Travis reports build as green prior to making a pull request. 


## Code style

Please, consider our [code-style](STYLE.md) prior to submitting the pull request. We love PRs, but much more than just PRs we love PRs that are 'on style'.

For the Java sources we're sticked to the Google's [one](https://google.github.io/styleguide/javaguide.html).


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

The test suite can be run by launching:

    ./gradlew test
