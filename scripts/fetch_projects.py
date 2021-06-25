import argparse
import json
import subprocess
from typing import List, Dict

from git import git_command

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument("--projects", type=str, help="Projects info in JSON format", required=True)

    args = parser.parse_args()
    projects: List[Dict] = json.loads(args.projects)

    for project in projects:
        name = project["name"]
        path = f"testData/{name}"

        if name == "stdlib":
            subprocess.run(["cargo", "new", "--name", name, "--bin", "--vcs", "none", path], check=True,
                           stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        else:
            repository = project["repository"]
            git_command("clone", "--depth", "1", f"https://github.com/{repository}.git", path)
