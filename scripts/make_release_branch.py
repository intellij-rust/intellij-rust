import argparse
import os
import re
import subprocess


def git_command(*args):
    subprocess.run(["git"] + list(args), check=True)


def env(key: str) -> str:
    value = os.getenv(key)
    if value is None:
        raise Exception(f"{key} is not set")
    return value


patchVersionRe = r"patchVersion=(\d+)"

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument("--token", type=str, help="github token")

    args = parser.parse_args()

    actor = env("GITHUB_ACTOR")
    repo = env("GITHUB_REPOSITORY")

    repo_url = f"https://{actor}:{args.token}@github.com/{repo}.git"

    with open("gradle.properties") as properties:
        text = properties.read()
    patchVersion = int(re.search(patchVersionRe, text).group(1))

    releaseBranch = f"release-{patchVersion}"
    git_command("branch", releaseBranch)
    git_command("push", repo_url, releaseBranch)

    newText = re.sub(patchVersionRe, f"patchVersion={patchVersion + 1}", text)
    with open("gradle.properties", mode="w") as properties:
        properties.write(newText)

    git_command("add", "gradle.properties")
    git_command("commit", "-m", ":arrow_up: patch version")
    git_command("push", repo_url, "master")
