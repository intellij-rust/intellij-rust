import re

import argparse

from common import execute_command, env
from git import git_command, has_git_changes
from github import add_assignee, get_branch, create_pull_request

CHECK_WORKFLOW_PATH = ".github/workflows/check.yml"
RUSTC_VERSION_RE = re.compile(r".* \(\w*\s*(\d{4}-\d{2}-\d{2})\)")
WORKFLOW_RUSTC_VERSION_RE = re.compile(r"(rust-version: \[.*nightly-)\d{4}-\d{2}-\d{2}(.*])")
NIGHTLY_BRANCH = "nightly"
DEFAULT_ASSIGNEE = "Undin"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--token", type=str, required=True, help="github token")

    args = parser.parse_args()

    repo = env("GITHUB_REPOSITORY")

    nightly_branch = get_branch(repo, args.token, NIGHTLY_BRANCH)
    if nightly_branch is not None:
        print("Repo already has nightly branch")
        return

    git_command("checkout", "-b", NIGHTLY_BRANCH)

    output = execute_command("rustc", "-V")
    match_result = RUSTC_VERSION_RE.match(output)
    date = match_result.group(1)
    with open(CHECK_WORKFLOW_PATH) as f:
        workflow_text = f.read()

    result = re.search(WORKFLOW_RUSTC_VERSION_RE, workflow_text)
    if result is None:
        raise ValueError("Failed to find the current version of nightly rust")

    new_workflow_text = re.sub(WORKFLOW_RUSTC_VERSION_RE, f"\\g<1>{date}\\g<2>", workflow_text)
    if new_workflow_text == workflow_text:
        print("The latest nightly rustc version is already used")
        return

    with open(CHECK_WORKFLOW_PATH, "w") as f:
        f.write(new_workflow_text)

    if has_git_changes():
        git_command("add", CHECK_WORKFLOW_PATH)
        git_command("commit", "-m", ":arrow_up: nightly")

        git_command("push", "origin", NIGHTLY_BRANCH)
        pull_request = create_pull_request(repo, args.token, NIGHTLY_BRANCH, ":arrow_up: nightly")
        add_assignee(repo, args.token, pull_request["number"], DEFAULT_ASSIGNEE)
    else:
        print("Everything is up to date")


if __name__ == '__main__':
    main()
