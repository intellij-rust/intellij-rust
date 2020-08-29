import argparse

from common import env
from github import get_current_milestone, set_milestone

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument("--token", type=str, help="GitHub token", required=True)
    parser.add_argument("--pull-request", type=int, help="Pull request number", required=True)

    args = parser.parse_args()

    repo = env("GITHUB_REPOSITORY")
    milestone = get_current_milestone(repo)

    set_milestone(args.token, repo, args.pull_request, milestone["number"])
