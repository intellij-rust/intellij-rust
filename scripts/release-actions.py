import argparse
import requests


def send_github_event(token: str, event_name: str):
    payload = {"event_type": event_name}
    headers = {"Authorization": f"token {token}",
               "Accept": "application/vnd.github.v3+json"}
    response = requests.post("https://api.github.com/repos/intellij-rust/intellij-rust/dispatches",
                             json=payload,
                             headers=headers)
    response.raise_for_status()


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument("--command", choices=["release-branch", "nightly-release", "beta-release", "stable-release"],
                        type=str, help="command", required=True)
    parser.add_argument("--token", type=str, help="github token", required=True)

    args = parser.parse_args()

    send_github_event(args.token, args.command)
