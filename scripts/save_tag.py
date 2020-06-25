import argparse

from common import construct_repository_url, git_command

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument("--token", type=str, help="github token", required=True)
    parser.add_argument("--tag", type=str, help="tag name", required=True)
    parser.add_argument("--commit", type=str, help="commit hash", required=True)

    args = parser.parse_args()

    repo_url = construct_repository_url(args.token)

    git_command("tag", "-d", args.tag, check=False)
    git_command("tag", args.tag, args.commit)
    git_command("push", "-f", repo_url, args.tag)
