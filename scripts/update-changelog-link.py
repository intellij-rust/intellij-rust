import re
from datetime import date

import argparse

from common import get_patch_version, git_command, construct_repository_url

PLUGIN_XML = "plugin/src/main/resources/META-INF/plugin.xml"

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument("--token", type=str, help="github token")

    args = parser.parse_args()

    with open(PLUGIN_XML) as f:
        text = f.read()
    today = date.today()
    version = get_patch_version() - 1
    new_text = re.sub(r"https://intellij-rust\.github\.io/.*\.html",
                      f"https://intellij-rust.github.io/{today.year}/{today.month:02d}/{today.day:02d}/changelog-{version}.html",
                      text)
    with (open(PLUGIN_XML, mode="w")) as f:
        f.write(new_text)

    repo_url = construct_repository_url(args.token)

    git_command("add", PLUGIN_XML)
    git_command("commit", "-m", "Changelog")

    head = git_command("rev-parse", "HEAD")
    release_branch = f"release-{version}"
    git_command("checkout", release_branch)
    git_command("cherry-pick", head)

    git_command("push", repo_url, "master")
    git_command("push", repo_url, release_branch)
