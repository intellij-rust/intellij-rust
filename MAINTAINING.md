# Notes for maintainers

## Accepting pull requests

We use [bors](https://bors.tech/) to make sure master is always green. Common commands are

* `bors r+` to merge a PR,
* `bors r=username` to merge a PR on behalf of the user without r+ permissions
* `bors delegate+` to grant the author of PR r+ right for this PR. 

Don't forget to say "Thank you!" when merging pull requests! :)

After pull request accepting you need:
* add the corresponding milestone for the pull request to make search "when this change was released" easier
* add [project](https://github.com/intellij-rust/intellij-rust/projects) to inform QA 
that these changes should be tested. We don't usually add a project to pull request if it doesn't affect users
* mark the pull request by special labels ([feature](https://github.com/intellij-rust/intellij-rust/pulls?q=is%3Apr+label%3Afeature), 
[fix](https://github.com/intellij-rust/intellij-rust/pulls?q=is%3Apr+label%3Afix),
[performance](https://github.com/intellij-rust/intellij-rust/pulls?q=is%3Apr+label%3Aperformance) and 
[internal](https://github.com/intellij-rust/intellij-rust/pulls?q=is%3Apr+label%3Ainternal)) if you consider
that the corresponding changes should be mentioned in changelog.
If pull request doesn't have any of these labels it will be ignored while changelog generation
See more about releases in the [corresponding](#Releases) section.
* mark the pull request by [to be documented](https://github.com/intellij-rust/intellij-rust/labels/to%20be%20documented) label
if the corresponding change should be mentioned in documentation or affects existing documentation


Each non-stalled pull-request should be assigned to a reviewer, who should make
sure that PR moves forward. However, anybody with r+ can accept any PR, if
they are confident that the PR is in a good state.

## Accepting contributions outside of GitHub

In case you receive a contribution (a patch) outside of GitHub (e.g. by email),
we require a CLA from that contributor. The CLA can be signed at 
https://www.jetbrains.com/agreements/cla/ via DocuSign. The most important one 
is that signing is fully electronic and can be done in seconds


## Upgrades

### Gradle

```
# Substitute 4.1 for latest version https://gradle.org/install/#install
# Repeat the command twice, as only the second iteration is idempotent =/ 
./gradlew wrapper --gradle-version 4.1 --distribution-type all
./gradlew wrapper --gradle-version 4.1 --distribution-type all
```

Note `--distribution-type all`.

### New IDE version

Each new major platform release brings not only a lot of new features
but usually also a bunch of new incompatibilities (both source and binary ones).
Since we want to support at least the latest stable major platform release and new [EAP](https://www.jetbrains.com/resources/eap/)s,
we have to compile the plugin with different platform versions to avoid any runtime errors.

To solve the problem with incompatibilities, we use conditional compilation based on Gradle [source sets](https://docs.gradle.org/current/dsl/org.gradle.api.tasks.SourceSet.html).
See [CONTRIBUTING.md](CONTRIBUTING.md#source-code-for-different-platform-versions) for more details about concrete source structure.

#### How to support new platform version

The following explanation uses `old`, `current` and `new` platform terms that mean:
* `old` - number of the oldest supported platform version that should be dropped
* `current` - number of the latest major stable platform release
* `new` - number of new platform that should be supported

For example, at the moment of writing, `201` is `old`, `202` is `current` and `203` is `new`.
See [build_number_ranges](https://jetbrains.org/intellij/sdk/docs/basics/getting_started/build_number_ranges.html) for more info about platform versions.
 
Step by step instruction on how to support new platform version:
* Drop all code related to the `old` platform version, i.e. drop `gradle-%old%.properties` and
all `%module_name%/src/%old%` directories in each module
* Move code from `%module_name%/src/%current%` directories into common source set,
i.e. `%module_name%/src/%current%/main` into `%module_name%/src/main` and `%module_name%/src/%current%/test` into `%module_name%/src/test`.
Also, simplify the moved code if possible.
* Add support for `new` platform, i.e. add `gradle-%new%.properties` with all necessary properties and make it compile.
It may be required to extract some code into platform-specific source sets to make plugin compile with each supported platform.
See [Tips and tricks](#tips-and-tricks) section for the most common examples of how to do it
* Fix tests
* Update log path in run configurations like `runIDEA` and `runCLion`.
For `runIDEA` configuration it requires:
    - drop `idea-%old%.log` item 
    - add `idea-%new%.log` item with `$PROJECT_DIR$/plugin/build/idea-sandbox-%new%/system/log/idea.log` path
* Update CI workflows to use new platform version instead of old one.
It usually requires just to update `platform-version` list in all workflows where we build the plugin.
At the moment of writing they are `check.yml`, `rust-nightly.yml` and `rust-release.yml`
* Fix `BACKCOMPAT: %old%` comments

#### Tips and tricks

A non-exhaustive list of tips how you can adapt your code for several platforms:
* if you need to execute specific code for each platform in gradle build scripts (`build.gradle.kts` or `settings.gradle.kts`),
just use `platformVersion` property and `if`/`when` conditions.
Note, in `build.gradle.kts` value of this property is already retrieved into `platformVersion` variable
* if you need to have different sources for each platform:
    - check that you actually need to have specific code for each platform.
    There is quite often a deprecated way to make necessary action.
    If it's your case, don't forget to suppress the deprecation warning and add `// BACKCOMPAT: %current%` comment to mark this code and
    fix the deprecation in the future
    - extract different code into a function and place it into `compatUtils.kt` file in each platform-specific source set.
    It usually works well when you need to call specific public code to make the same things in each platform
    - if code that you need to call is not public (for example, it uses some protected methods of parent class), use the inheritance mechanism.
    Extract `AwesomeClassBase` from your `AwesomeClass`, inherit `AwesomeClass` from `AwesomeClassBase`,
    move `AwesomeClassBase` into platform specific source sets and move all platform specific code into `AwesomeClassBase` as protected methods.
    - sometimes, signatures of some methods can be specified while platform evolution.
    For example, `protected abstract void foo(Bar<Baz> bar)` can be converted into `protected abstract void foo(Bar<? extends Baz> bar)` and you have to override this method in your implementation. It introduces source incompatibility (although it doesn't break binary compatibility).
    The simplest way to make it compile for each platform is to introduce platform-specific [`typealias`](https://kotlinlang.org/docs/reference/type-aliases.html),
    i.e. `typealias PlaformBar = Bar<Baz>` for `current` platform and `typealias PlaformBar = Bar<out Baz>` for `new` one and use it in signature of overridden method.
    Also, this approach works well when some class you depend on was moved into another package. 
    - if creation of platform-specific source set is too heavy for your case, there is a way how you can choose specific code in runtime.
    Just create the corresponding condition using `com.intellij.openapi.application.ApplicationInfo.getBuild`.
    Usually, it looks like
    ```kotlin
    val BUILD_%new% = BuildNumber.fromString("%new%")!!
    if (ApplicationInfo.getInstance().build < BUILD_%new%) {
        // code for current platform
    } else {
        // code for new platform
    }
    ```
    Of course, code should be compilable with all supported platforms to use this approach.
    - sometimes you want to disable some tests temporarily to find out why they don't work later.
    The simplest way to do it is to mark them with `org.rust.IgnoreInPlatform` annotation.
    Under the hood, it uses the previous approach with `ApplicationInfo`.
* if you need to register different items in `%xml_name%.xml` for each platform:
    1. create `platform-%xml_name%.xml` in `%module_name%/src/%current%/main/resources/META-INF` and `%module_name%/src/%new%/main/resources/META-INF`
    2. put platform specific definitions into these xml files
    3. include platform specific xml into `%module_name%/src/main/resources/META-INF/%xml_name%.xml`, i.e. add the following code
    ```xml
      <idea-plugin xmlns:xi="http://www.w3.org/2001/XInclude">
          <xi:include href="/META-INF/platform-%xml_name%.xml" xpointer="xpointer(/idea-plugin/*)"/>
      </idea-plugin>  
    ```


While supporting new IDE version we should check all UI components manually
because we don't have UI tests yet.
Especially it's important if it's major IDE update 
because major platform updates can bring a lot of changes.

#### Common places
* New rust project
* Import rust project
* Rust preferences (in *Languages & Frameworks*)
* Cargo toolbar
* Run configuration
* Debugger settings (in *Build, Execution, Deployment > Debugger > Data Views > Rust*, CLion only)

#### Specific places
* Notifications (see `MissingToolchainNotificationProvider`)
* `Run Cargo Command` action
* `Implement Members` refactoring
* `Introduce Variable` refactoring
* `Extract Function` refactoring
* `Auto Import` quick fix & options
* `Unresolved Reference` inspection options

## Releases

Nightly and beta are released automatically by GitHub workflows. Stable is generally released every two weeks.
One week before release we create release branch with `release-%release_version%` name from the `master` branch.
`release_version` value is the same as the corresponding milestone version.
Release branches are used to build beta and stable plugin builds.

Most of release actions can be done automatically via GitHub workflow.
You can trigger them from [GitHub UI](https://github.blog/changelog/2020-07-06-github-actions-manual-triggers-with-workflow_dispatch/).
Just open `Action` tab, choose a necessary workflow and launch it via `Run workflow` button.

Alternatively, there is `scripts/release-actions.py` script to trigger events from your console.
Syntax: `python release-actions.py release_command --token github_token`.
Also, you can provide `IR_GITHUB_TOKEN` environment variable to provide github token.
It allows you to omit `--token` option.

See [instruction](https://help.github.com/en/github/authenticating-to-github/creating-a-personal-access-token-for-the-command-line)
how to create personal github token. Note, it should have `repo` scope.

Note, we use [pipenv](https://pipenv.pypa.io/en/latest/) to manage python version, dependencies and virtual environment.
See [instruction](https://pipenv.pypa.io/en/latest/install/#installing-pipenv) how to install it.
To run any command, just execute the following:
```
cd scripts
pipenv install # to install dependencies
pipenv run python release-actions.py release_command --token github_token # to run script in virtual environment
```

Available commands:
* `release-branch` - creates new release branch `release-%release_version%` from `master` one
where `%release_version%` is the same as `patchVersion` property in `gradle.properties`.
After that it increases `patchVersion` by one, commits changes and pushes them to master.
Note, the corresponding workflow is triggered on schedule to create release branch one week before release,
so you don't usually need to trigger it manually.
* `nightly-release` - builds the plugin from `master` branch and publishes it into `nightly` channel on [Marketplace].
Note, the corresponding workflow is triggered on schedule, so you don't usually need to trigger it manually.
* `beta-release` - builds the plugin from release branch and publishes it into `beta` channel on [Marketplace].
Note, the corresponding workflow is triggered on schedule, so you don't usually need to trigger it manually.
* `stable-release` - updates changelog link in `plugin/src/main/resources/META-INF/plugin.xml`, commits changes, 
pushes into `master` and cherry-picks the corresponding changes into release branch.
After that, it builds the plugin from release branch and publishes it into `stable` channel on [Marketplace].

Note, each command may provide additional options. Add `--help` option to get actual option list.  

Release notes live in [intellij-rust.github.io](https://github.com/intellij-rust/intellij-rust.github.io).
To write notes, run `./changelog.py`. It goes thorough all pull requests from the corresponding milestone and 
creates a template with default info about merged PRs in `_posts`. 
The initial version of each post depends on special tags that PR can be labeled. 
At this moment, `changelog.py` supports `feature`, `performance`, `fix` and `internal` tags. 
Note, PR can be marked with any subset of these tags.
Transform generated text to user-friendly one, add necessary links/gifs. 
Don't forget to mention every contributor using `by [@username]` syntax.

[Marketplace]: https://plugins.jetbrains.com/plugin/8182-rust/versions
