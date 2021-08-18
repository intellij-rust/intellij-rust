# TODO: think about using library for GitHub API
import json
from typing import Dict, Optional, List
from urllib.error import HTTPError
from urllib.request import Request, urlopen

from common import get_patch_version


def get_current_milestone(repo: str, patch_version: Optional[int] = None) -> Dict:
    milestones = get_latest_milestones(repo)
    if patch_version is None:
        patch_version = get_patch_version()
    milestone_version = f"v{patch_version}"
    result = next((milestone for milestone in milestones if milestone["title"] == milestone_version), None)
    if result is None:
        raise AssertionError(f"Milestone `{milestone_version}` doesn't exist")
    return result


def set_milestone(token: str, repo: str, issue_number: int, milestone_number: int) -> None:
    data = json.dumps({"milestone": milestone_number}).encode()
    headers = {"Authorization": f"token {token}",
               "Accept": "application/vnd.github.v3+json"}
    request = Request(f"https://api.github.com/repos/{repo}/issues/{issue_number}", data, headers, method="PATCH")
    urlopen(request)


def get_latest_milestones(repo: str, state: str = "all") -> List[Dict]:
    response = urlopen(f"https://api.github.com/repos/{repo}/milestones?state={state}&sort=due_on&direction=desc")
    return json.load(response)


def create_milestone(repo: str, token: str, title: str, description: str = "", due_on: Optional[str] = None) -> None:
    headers = {"Authorization": f"token {token}",
               "Accept": "application/vnd.github.v3+json"}
    params = {"title": title, "description": description}
    if due_on is not None:
        params["due_on"] = due_on
    data = json.dumps(params).encode()
    request = Request(f"https://api.github.com/repos/{repo}/milestones", data, headers, method="POST")
    urlopen(request)


def create_pull_request(repo: str, token: str, branch: str, title: str, draft: bool = True) -> Dict:
    headers = {"Authorization": f"token {token}",
               "Accept": "application/vnd.github.v3+json"}
    params = {"base": "master", "head": branch, "title": title, "draft": draft}
    data = json.dumps(params).encode()
    request = Request(f"https://api.github.com/repos/{repo}/pulls", data, headers, method="POST")
    response = urlopen(request)
    return json.load(response)


def add_assignee(repo: str, token: str, id: int, assignee: str) -> None:
    headers = {"Authorization": f"token {token}",
               "Accept": "application/vnd.github.v3+json"}
    params = {"assignees": [assignee]}
    data = json.dumps(params).encode()
    request = Request(f"https://api.github.com/repos/{repo}/issues/{id}", data, headers, method="POST")
    urlopen(request)


def get_check_statuses(repo: str, token: str, ref: str, check_name: str) -> Dict[str, List[Dict]]:
    headers = {"Authorization": f"token {token}",
               "Accept": "application/vnd.github.v3+json"}
    request = Request(
        f"https://api.github.com/repos/{repo}/commits/{ref}/check-runs?check_name={check_name}&status=completed",
        headers=headers)
    response = urlopen(request)
    return json.load(response)


def get_all_branches(repo: str, token: str) -> List[Dict]:
    headers = {"Authorization": f"token {token}",
               "Accept": "application/vnd.github.v3+json"}
    request = Request(f"https://api.github.com/repos/{repo}/branches", headers=headers)
    response = urlopen(request)
    return json.load(response)


def get_branch(repo: str, token: str, branch_name: str) -> Optional[Dict]:
    headers = {"Authorization": f"token {token}",
               "Accept": "application/vnd.github.v3+json"}
    request = Request(f"https://api.github.com/repos/{repo}/branches/{branch_name}", headers=headers)
    try:
        response = urlopen(request)
    except HTTPError as e:
        if e.code == 404:
            return None
        else:
            raise e
    return json.load(response)
