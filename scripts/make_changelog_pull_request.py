import argparse
import re
from datetime import datetime, date, timedelta
from typing import Dict

from common import env, get_patch_version, execute_command
from git import git_command
from github_connect import get_current_milestone, create_pull_request, get_all_branches, add_assignee


def changelog_branch_name(patch_version: int) -> str:
    return f"release-{patch_version}"


def add_changelog_template(token: str, patch_version: int, cwd: str) -> None:
    branch_name = changelog_branch_name(patch_version)
    git_command("checkout", "-b", branch_name, cwd=cwd)
    execute_command("python", "changelog.py", "--token", token, cwd=cwd)
    git_command("add", "_posts", cwd=cwd)
    git_command("commit", "-m", f"Changelog {patch_version}", cwd=cwd)
    git_command("push", "origin", branch_name, cwd=cwd)


def create_changelog_pull_request(changelog_repo: str, token: str, patch_version: int, milestone: Dict) -> None:
    description = milestone["description"]
    result = re.search("Release manager: @(\\w+)", description)
    if result is None:
        title = milestone["title"]
        raise Exception(f"Failed to find release manager for {title} milestone")
    release_manager = result.group(1)
    pr = create_pull_request(changelog_repo, token, changelog_branch_name(patch_version), f"Changelog {patch_version}")
    add_assignee(changelog_repo, token, pr["number"], release_manager)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--token", type=str, required=True)
    parser.add_argument("--repo_owner", type=str, required=True)
    parser.add_argument("--repo_name", type=str, required=True)
    args = parser.parse_args()

    repo = env("GITHUB_REPOSITORY")
    # the script is supposed to be invoked only after release branch creation
    # so we need previous patch version
    release_patch_version = get_patch_version() - 1
    changelog_repo = f"{args.repo_owner}/{args.repo_name}"
    branch_name = changelog_branch_name(release_patch_version)

    branches = get_all_branches(changelog_repo, args.token)
    existing_branch = next((branch["name"] for branch in branches if branch["name"].endswith(branch_name)), None)
    if existing_branch is not None:
        print(f"Branch for v{release_patch_version} release already exists: `{existing_branch}`")
        return

    milestone = get_current_milestone(repo, release_patch_version)

    # TODO: find out more correct way to parse data
    release_date = datetime.strptime(milestone["due_on"], "%Y-%m-%dT%H:%M:%SZ").date()
    today = date.today()
    if today >= release_date or milestone["state"] == "closed":
        print(f"Milestone v{release_patch_version} is over")
        return

    delta = release_date - today
    five_days = timedelta(5)

    if delta > five_days:
        print("Too early to create release changelog")
        return

    add_changelog_template(args.token, release_patch_version, args.repo_name)
    create_changelog_pull_request(changelog_repo, args.token, release_patch_version, milestone)


if __name__ == '__main__':
    main()
