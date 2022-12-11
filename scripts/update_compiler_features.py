import argparse

from common import execute_command, env
from updater import UpdaterBase


class CompilerFeatureUpdater(UpdaterBase):

    def _update_locally(self) -> None:
        execute_command("cargo", "build", "--manifest-path", "attributes-info/Cargo.toml", "--package", "attributes-info")
        execute_command("cargo", "run", "--manifest-path", "attributes-info/Cargo.toml", "--package", "attributes-info",
                        "--bin", "attributes-info",)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--token", type=str, required=True, help="github token")
    args = parser.parse_args()

    repo = env("GITHUB_REPOSITORY")

    updater = CompilerFeatureUpdater(repo, args.token, branch_name="update-compiler-features",
                                     message="Update compiler features",
                                     assignee="neonaot")
    updater.update()


if __name__ == '__main__':
    main()
