import functools

import click
import requests
from typing import Dict, Any, Optional


def send_github_event(workflow_name: str, token: str, inputs: Optional[Dict[str, Any]] = None):
    if inputs is None:
        inputs = {}
    payload = {"ref": "master", "inputs": inputs}
    headers = {"Authorization": f"token {token}",
               "Accept": "application/vnd.github.v3+json"}
    response = requests.post(f"https://api.github.com/repos/intellij-rust/intellij-rust/actions/workflows/{workflow_name}/dispatches",
                             json=payload,
                             headers=headers)
    response.raise_for_status()


@click.group()
def cli():
    pass


def token_option(func):
    @functools.wraps(func)
    @click.option("-t", "--token", required=True, envvar="IR_GITHUB_TOKEN", show_envvar=True,
                  help="GitHub token. Note, it should have `repo` scope.")
    def wrapper(*args, **kwargs):
        func(*args, **kwargs)

    return wrapper


@cli.command(help="Create release branch")
@token_option
def release_branch(token: str):
    send_github_event("release-branch.yml", token)


@cli.command(help="Build plugin and publish it to nightly channel")
@token_option
def nightly_release(token: str):
    send_github_event("rust-nightly.yml", token)


@cli.command(help="Build plugin and publish it to beta channel")
@token_option
def beta_release(token: str):
    send_github_event("rust-release.yml", token, {"type": "beta"})


@cli.command(help="Build plugin and publish it to stable channel")
@click.option("--update-changelog/--no-update-changelog", default=True, help="Update changelog link in plugin.xml")
@token_option
def stable_release(token: str, update_changelog: bool):
    send_github_event("rust-release.yml", token, {"type": "stable", "update_changelog": str(update_changelog).lower()})


cli.add_command(release_branch)
cli.add_command(nightly_release)
cli.add_command(beta_release)
cli.add_command(stable_release)

if __name__ == '__main__':
    cli()
