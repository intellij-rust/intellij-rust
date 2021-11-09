import re
from datetime import date

from common import get_patch_version
from git import git_command

PLUGIN_XML = "plugin/src/main/resources/META-INF/plugin.xml"

if __name__ == '__main__':
    with open(PLUGIN_XML) as f:
        text = f.read()
    today = date.today()
    version = get_patch_version() - 1
    new_text = re.sub(r"https://intellij-rust\.github\.io/.*\.html",
                      f"https://intellij-rust.github.io/{today.year}/{today.month:02d}/{today.day:02d}/changelog-{version}.html",
                      text)
    with (open(PLUGIN_XML, mode="w")) as f:
        f.write(new_text)

    git_command("add", PLUGIN_XML)
    # [skip ci] says GitHub not to emit `push` and `pull_request` events, i.e. don't trigger `check` workflow.
    # It allows not to occupy GitHub Actions agents that are needed for building release builds
    # See https://github.blog/changelog/2021-02-08-github-actions-skip-pull-request-and-push-workflows-with-skip-ci/
    git_command("commit", "-m", "Changelog\n\n[skip ci]")

    head = git_command("rev-parse", "HEAD")
    release_branch = f"release-{version}"
    git_command("checkout", release_branch)
    git_command("cherry-pick", head)

    git_command("push", "origin", "master")
    git_command("push", "origin", release_branch)
