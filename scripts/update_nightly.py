import argparse
import re

from common import execute_command, env
from updater import UpdaterBase

WORKFLOW_PATH = ".github/workflows/get-rust-versions.yml"
RUSTC_VERSION_RE = re.compile(r".* \(\w*\s*(\d{4}-\d{2}-\d{2})\)")
WORKFLOW_RUSTC_VERSION_RE = re.compile(r'(?<=NIGHTLY: "nightly-)\d{4}-\d{2}-\d{2}')


class NightlyUpdater(UpdaterBase):

    def _update_locally(self):
        output = execute_command("rustc", "-V")
        match_result = RUSTC_VERSION_RE.match(output)
        date = match_result.group(1)
        with open(WORKFLOW_PATH) as f:
            workflow_text = f.read()

        result = re.search(WORKFLOW_RUSTC_VERSION_RE, workflow_text)
        if result is None:
            raise ValueError("Failed to find the current version of nightly rust")

        new_workflow_text = re.sub(WORKFLOW_RUSTC_VERSION_RE, date, workflow_text)
        if new_workflow_text == workflow_text:
            print("The latest nightly rustc version is already used")
            return

        with open(WORKFLOW_PATH, "w") as f:
            f.write(new_workflow_text)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--token", type=str, required=True, help="github token")
    args = parser.parse_args()

    repo = env("GITHUB_REPOSITORY")

    updater = NightlyUpdater(repo, args.token, branch_name="nightly", message=":arrow_up: nightly", assignee="Undin")
    updater.update()


if __name__ == '__main__':
    main()
