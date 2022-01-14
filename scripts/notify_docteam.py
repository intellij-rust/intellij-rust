import argparse

from github import Github

from common import env

DOC_MSG = "@intellij-rust/technical-writers, seems like it's time to write the documentation!"

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument("--token", type=str, required=True)
    parser.add_argument("--pull_request", type=int, required=True)
    args = parser.parse_args()

    repo = env("GITHUB_REPOSITORY")

    g = Github(args.token)
    repo = g.get_repo(repo)
    pr = repo.get_pull(args.pull_request)

    pr.create_issue_comment(DOC_MSG)
