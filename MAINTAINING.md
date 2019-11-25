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
* mark the pull request by special labels ([feature](https://github.com/intellij-rust/intellij-rust/labels/feature), 
[fix](https://github.com/intellij-rust/intellij-rust/labels/fix) and 
[internal](https://github.com/intellij-rust/intellij-rust/labels/internal) if you consider
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

Nightly and beta are released automatically by [TeamCity]. Stable is generally released every two weeks.
One week before release we create release branch with `release-%release_version%` name from the `master` branch.
`release_version` value is the same as the corresponding milestone version.
Release branches are used to build beta and stable plugin builds.
After release branch creation, don't forget to increase `patchVersion` property in `gradle.properties` of master branch

Release notes live in [intellij-rust.github.io](https://github.com/intellij-rust/intellij-rust.github.io).
To write notes, run `./changelog.py`. It goes thorough all pull requests from the corresponding milestone and 
creates a template with default info about merged PRs in `_posts`. 
The initial version of each post depends on special tags that PR can be labeled. 
At this moment, `changelog.py` supports `feature`, `fix` and `internal` tags. 
Note, PR can be marked with any subset of these tags.
Transform generated text to user-friendly one, add necessary links/gifs. 
Don't forget to mention every contributor using `by [@username]` syntax.

After finishing with release notes, execute `./gradlew makeRelease` tasks. It'll do the following things:

* add links to the release notes
* commit and push release notes to intellij-rust.github.io
* push "Changelog" commit to master branch of intellij-rust
* cherry-pick changelog commit to release branch

Then hit `run` for all `Upload Stable` configuration on [TeamCity].
Make sure to select the changeset with "Changelog" commit.

[TeamCity]: https://teamcity.jetbrains.com/project.html?projectId=IntellijIdeaPlugins_Rust
