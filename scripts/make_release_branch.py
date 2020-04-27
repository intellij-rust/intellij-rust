import argparse

from common import get_patch_version, inc_patch_version, GRADLE_PROPERTIES, git_command, construct_repository_url

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument("--token", type=str, help="github token")

    args = parser.parse_args()

    repo_url = construct_repository_url(args.token)

    patch_version = get_patch_version()

    release_branch = f"release-{patch_version}"
    git_command("branch", release_branch)
    git_command("push", repo_url, release_branch)

    inc_patch_version()

    git_command("add", GRADLE_PROPERTIES)
    git_command("commit", "-m", ":arrow_up: patch version")
    git_command("push", repo_url, "master")
