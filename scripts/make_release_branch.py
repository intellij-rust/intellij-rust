import argparse
import json
import re
import traceback
from datetime import datetime
from urllib.request import urlopen

import github_connect
from common import get_patch_version, inc_patch_version, GRADLE_PROPERTIES, env
from git import git_command
from release_managers_common import RELEASE_MANAGER_RE


def send_slack_message(slack_ids, version, webhook):
    repository = env("GITHUB_REPOSITORY")
    milestone = github_connect.get_current_milestone(repo=repository, patch_version=version)

    release_version = milestone["title"]
    release_manager = re.search(RELEASE_MANAGER_RE, milestone['description']).group(1)
    link = milestone['html_url']
    date = datetime.strptime(milestone["due_on"], "%Y-%m-%dT%H:%M:%SZ").date()
    slack_user = slack_ids.get(release_manager)
    if slack_user is None:
        # Hack to pass correct user-id. Otherwise, Slack will not send the message.
        slack_user = slack_ids.get("neonaot")

    # Expected template on the Slack workflow side:
    # Release branch for %release_version% is created
    # %link%
    # Release is planned for %date%
    # Release manager is %slack_user%
    message = {
        "release_version": release_version,
        "link": link,
        "date": f'{date.day:02d}.{date.month:02d}.{date.year}',
        "slack_user": slack_user
    }
    urlopen(webhook, data=json.dumps(message).encode())


if __name__ == '__main__':
    parser = argparse.ArgumentParser(formatter_class=argparse.RawTextHelpFormatter)
    parser.add_argument("--slack_webhook", type=str, required=True,
                        help='Webhook is unique URL for the Slack workflow. After sending a request to it,'
                             'the workflow will start doing its work.\n'
                             'Looks like https://hooks.slack.com/workflows/{unique_part}, '
                             'can be found on the workflow builder page.')

    parser.add_argument("--slack_ids", type=str, required=True,
                        help='slack_ids is a json looks like {"github-username1": "slack-ID1",  ... }.\n'
                             'NOTE: slack ID for user can be found on their profile > ⋮ > Copy member ID.'
                             'It is a set of random digits and letters, not @name.surname or any custom usernames.\n'
                             'On slack workflow side it will be automatically treated as @username.')
    args = parser.parse_args()

    patch_version = get_patch_version()

    release_branch = f"release-{patch_version}"
    git_command("branch", release_branch)
    git_command("push", "origin", release_branch)

    inc_patch_version()

    git_command("add", GRADLE_PROPERTIES)
    git_command("commit", "-m", ":arrow_up: patch version")
    git_command("push", "origin", "master")

    try:
        send_slack_message(json.loads(args.slack_ids), patch_version, args.slack_webhook)
    except Exception as e:
        traceback.print_exc()
