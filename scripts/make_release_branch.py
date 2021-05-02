from common import get_patch_version, inc_patch_version, GRADLE_PROPERTIES
from git import git_command

if __name__ == '__main__':
    patch_version = get_patch_version()

    release_branch = f"release-{patch_version}"
    git_command("branch", release_branch)
    git_command("push", "origin", release_branch)

    inc_patch_version()

    git_command("add", GRADLE_PROPERTIES)
    git_command("commit", "-m", ":arrow_up: patch version")
    git_command("push", "origin", "master")
