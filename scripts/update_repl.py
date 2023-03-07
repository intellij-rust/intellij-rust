import argparse
import re
from urllib import request
import json

from common import env
from updater import UpdaterBase

WORKFLOW_PATH = ".github/workflows/check.yml"
# args: evcxr_repl --locked --version 0.13.0
REPL_WORKFLOW_RE = re.compile(r'(?<=REPL_VERSION: )(\d+\.\d+\.\d+)')


class ReplUpdater(UpdaterBase):

    def _update_locally(self):

        with request.urlopen('https://crates.io/api/v1/crates/evcxr_repl') as r:
            new_version = json.loads(r.read())['crate']['max_stable_version']

        with open(WORKFLOW_PATH) as f:
            workflow_text = f.read()

        result = re.search(REPL_WORKFLOW_RE, workflow_text)
        if result is None:
            raise ValueError("Failed to find the current version of repl")

        new_workflow_text = re.sub(REPL_WORKFLOW_RE, new_version, workflow_text)
        if new_workflow_text == workflow_text:
            print("The latest nightly repl version is already used")
            return

        with open(WORKFLOW_PATH, "w") as f:
            f.write(new_workflow_text)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--token", type=str, required=True, help="github token")
    args = parser.parse_args()

    repo = env("GITHUB_REPOSITORY")

    updater = ReplUpdater(repo, args.token, branch_name="repl", message=":arrow_up: repl", assignee="dima74")
    updater.update()


if __name__ == '__main__':
    main()
