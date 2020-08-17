# Notes for maintainers

## Accepting pull requests

We require a CLA from all contributors. See [CONTRIBUTING.md](CONTRIBUTING.md) 
for the details. The most important one is that signing is fully electronic 
and can be done in seconds. The list of GitHub users who have already signed 
the CLA is at [CONTRIBUTORS.txt](CONTRIBUTORS.txt).

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
