import json
from datetime import datetime, date, timedelta
from urllib import request

from common import get_patch_version, env

if __name__ == '__main__':
    repo = env("GITHUB_REPOSITORY")
    response = request.urlopen(f"https://api.github.com/repos/{repo}/milestones")
    milestones = json.load(response)

    milestone_version = f"v{get_patch_version()}"
    milestone = next(milestone for milestone in milestones if milestone["title"] == milestone_version)
    # TODO: find out more correct way to parse data
    release_date = datetime.strptime(milestone["due_on"], "%Y-%m-%dT%H:%M:%SZ").date()

    today = date.today()
    delta = release_date - today
    week = timedelta(7)

    # Should be synchronized with .github/workflows/release-branch.yml
    print(json.dumps({"create_release_branch": delta < week}))
