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

## Upgrades

### Gradle

```
# Substitute 4.1 for latest version https://gradle.org/install/#install
# Repeat the command twice, as only the second iteration is idempotent =/ 
./gradlew wrapper --gradle-version 4.1 --distribution-type all
./gradlew wrapper --gradle-version 4.1 --distribution-type all
```

Note `--distribution-type all`.
