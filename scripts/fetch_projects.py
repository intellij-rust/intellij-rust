import argparse
import json
from typing import List, Dict

from common import git_command

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument("--projects", type=str, help="Projects info in JSON format", required=True)

    args = parser.parse_args()
    projects: List[Dict] = json.loads(args.projects)

    for project in projects:
        name = project["name"]
        repository = project["repository"]

        git_command("clone", "--depth", "1", f"https://github.com/{repository}.git", f"testData/{name}")
