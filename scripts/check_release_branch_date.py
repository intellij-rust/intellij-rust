import json
from datetime import datetime, date, timedelta

from common import env
from github import get_current_milestone

if __name__ == '__main__':
    repo = env("GITHUB_REPOSITORY")
    milestone = get_current_milestone(repo)
    # TODO: find out more correct way to parse data
    release_date = datetime.strptime(milestone["due_on"], "%Y-%m-%dT%H:%M:%SZ").date()

    today = date.today()
    delta = release_date - today
    week = timedelta(7)

    # Should be synchronized with .github/workflows/release-branch.yml
    print(json.dumps({"create_release_branch": delta < week}))
