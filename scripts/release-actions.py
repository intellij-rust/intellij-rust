import functools

import click
import requests
from typing import Dict, Any, Optional


def send_github_event(token: str, event_name: str, client_payload: Optional[Dict[str, Any]] = None):
    if client_payload is None:
        client_payload = {}
    payload = {"event_type": event_name, "client_payload": client_payload}
    headers = {"Authorization": f"token {token}",
               "Accept": "application/vnd.github.v3+json"}
    response = requests.post("https://api.github.com/repos/intellij-rust/intellij-rust/dispatches",
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
    send_github_event(token, "release-branch")


@cli.command(help="Build plugin and publish it to nightly channel")
@token_option
def nightly_release(token: str):
    send_github_event(token, "nightly-release")


@cli.command(help="Build plugin and publish it to beta channel")
@token_option
def beta_release(token: str):
    send_github_event(token, "beta-release")


@cli.command(help="Build plugin and publish it to stable channel")
@click.option("--update-changelog/--no-update-changelog", default=True, help="Update changelog link in plugin.xml")
@token_option
def stable_release(token: str, update_changelog: bool):
    send_github_event(token, "stable-release", {"update_changelog": update_changelog})


cli.add_command(release_branch)
cli.add_command(nightly_release)
cli.add_command(beta_release)
cli.add_command(stable_release)

if __name__ == '__main__':
    cli()
