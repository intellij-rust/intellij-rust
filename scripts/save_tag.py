import argparse

from git import git_command

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument("--tag", type=str, help="tag name", required=True)
    parser.add_argument("--commit", type=str, help="commit hash", required=True)

    args = parser.parse_args()

    git_command("tag", "-d", args.tag, check=False)
    git_command("tag", args.tag, args.commit)
    git_command("push", "-f", "origin", args.tag)
