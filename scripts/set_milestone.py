import argparse
from urllib.request import urlopen

from common import env, get_patch_version_from_text
from github_connect import get_current_milestone, set_milestone

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument("--token", type=str, help="GitHub token", required=True)
    parser.add_argument("--pull-request", type=int, help="Pull request number", required=True)

    args = parser.parse_args()

    repo = env("GITHUB_REPOSITORY")
    text = urlopen(f"https://github.com/{repo}/raw/master/gradle.properties").read().decode("utf-8")
    patch_version = get_patch_version_from_text(text)
    milestone = get_current_milestone(repo, patch_version)

    set_milestone(args.token, repo, args.pull_request, milestone["number"])
