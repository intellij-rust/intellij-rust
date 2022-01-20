import argparse
import json

from common import env
from github_connect import get_check_statuses

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument("--token", type=str, required=True)
    parser.add_argument("--ref", type=str, required=True)
    parser.add_argument("--check_name", type=str, required=True)

    args = parser.parse_args()

    repo = env("GITHUB_REPOSITORY")
    try:
        statuses = get_check_statuses(repo, args.token, args.ref, args.check_name)
        has_successful_check = any(s["conclusion"] == "success" for s in statuses["check_runs"])
    except:
        has_successful_check = False

    print(json.dumps(has_successful_check))
