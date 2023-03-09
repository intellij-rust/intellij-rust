import argparse
import re
from datetime import datetime, timedelta

from common import env, get_patch_version
from github_connect import get_latest_milestones, create_milestone

RELEASE_MANAGER_RE = re.compile("Release manager: @(\\w+)")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--token", type=str, required=True, help="github token")

    args = parser.parse_args()

    repo = env("GITHUB_REPOSITORY")
    milestones = get_latest_milestones(repo)
    milestones.sort(key=lambda milestone: milestone["title"], reverse=True)

    patch_version = get_patch_version()
    milestone_version = f"v{patch_version}"
    current_milestone = next((milestone for milestone in milestones if milestone["title"] == milestone_version), None)
    # do nothing if milestone with expected title is already created
    if current_milestone is not None:
        return

    prev_milestone_version = f"v{patch_version - 1}"
    prev_milestone = next((milestone for milestone in milestones if milestone["title"] == prev_milestone_version), None)
    due_on = None
    if prev_milestone is not None and prev_milestone["due_on"] is not None:
        date = datetime.strptime(prev_milestone["due_on"], "%Y-%m-%dT%H:%M:%SZ") + timedelta(weeks=2)
        due_on = "%04d-%02d-%02dT%02d:%02d:%02dZ" % (date.year, date.month, date.day, date.hour, date.minute, date.second)

    release_managers = [
        "mchernyavsky",
        "vlad20012",
        "dima74",
        "ozkriff"
    ]

    for m in milestones:
        if len(release_managers) == 1:
            break
        desc = m["description"]
        res = re.search(RELEASE_MANAGER_RE, desc)
        if res is not None:
            try:
                release_managers.remove(res.group(1))
            except ValueError:
                pass

    description = f"Release manager: @{release_managers[0]}"
    create_milestone(repo, args.token, milestone_version, description=description, due_on=due_on)


if __name__ == '__main__':
    main()
