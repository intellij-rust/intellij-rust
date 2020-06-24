import argparse
from subprocess import CalledProcessError

from common import git_command

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument("--tag", type=str, required=True, help="tag name")

    args = parser.parse_args()

    try:
        commit_hash = git_command("rev-list", "-n", "1", args.tag, print_stdout=False)
    except CalledProcessError:
        commit_hash = ""

    print(commit_hash)
