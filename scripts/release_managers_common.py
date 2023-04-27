import re

RELEASE_MANAGER_RE = re.compile("Release manager: @(\\w+)")

# After editing maintainers list, don't forget to update `SLACK_USERNAMES` GitHub secret.
# For more information see `make_release_branch.py` file.
release_managers = [
    "mchernyavsky",
    "vlad20012",
    "dima74"
]
