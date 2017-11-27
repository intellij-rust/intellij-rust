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


## Releases

Nightly is released automatically by TeamCity. Alpha is generally released 
on mondays. 

Release notes live in [intellij-rust.github.io](https://github.com/intellij-rust/intellij-rust.github.io).
To write notes, run `./changelog.py` to generate template in `_posts`. 
Go thorough bors merge commits since the latest release and write about them in 
appropriate section. Don't forget to mention every contributor using `by [@username]` syntax.

After finishing with release notes, execute `./gradlew makeRelease` tasks. It'll do the following things:

* add links to the release notes
* commit and push release notes to intellij-rust.github.io
* update changelog in `plugin.xml` and push "Changelog" commit to master branch of intellij-rust
* checkout "nightly" branch, advance versions of EAP idea an nightly rust, and push this branch


Then:

* Hit Run on https://teamcity.jetbrains.com/viewType.html?buildTypeId=IntellijIdeaPlugins_Rust_UploadAlphaRust, make
  sure to select the changeset with "Changelog" commit.

* Send and r+ PR which bumps nightly.  
