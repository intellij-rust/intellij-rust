import os
import re
import subprocess
import time
from collections import defaultdict

import click

from common import git_command

ROOT_PATH = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEST_DATA_PATH = os.path.join(ROOT_PATH, "testData")

PROJECTS = [
    ("cargo", "https://github.com/rust-lang/cargo"),
    ("mysql_async", "https://github.com/blackbeam/mysql_async"),
    ("tokio", "https://github.com/tokio-rs/tokio"),
    ("amethyst", "https://github.com/amethyst/amethyst"),
    ("clap", "https://github.com/clap-rs/clap"),
    ("diesel", "https://github.com/diesel-rs/diesel"),
    ("rust-analyzer", "https://github.com/rust-analyzer/rust-analyzer"),
    ("xi-editor", "https://github.com/xi-editor/xi-editor"),
    ("juniper", "https://github.com/graphql-rust/juniper")
]


@click.command("init")
def init():
    for (project, git_url) in PROJECTS:
        directory = os.path.join(TEST_DATA_PATH, project)
        if not os.path.isdir(directory):
            print("Cloning {}".format(directory))
            subprocess.run(["git", "clone", git_url], cwd=TEST_DATA_PATH, check=True)
        else:
            print("{} already exists".format(directory))


RE_ANALYZE_PROJECT = re.compile(r"^org.rustPerformanceTests.RsRealProjectAnalysisTest > test analyze (\w*)")
RE_ERROR_FILE = re.compile(r"^(.*.rs):$")
RE_ERROR_LINE = re.compile(r"^(.*.rs: extra.*)$")


@click.command("run")
@click.argument("name")
@click.option("--revision", default=None)
def run(name, revision):
    """
    Runs real project tests and stores the results into `name`.
    If git revision is given, it will be used for the tests.
    """
    if revision:
        git_command("stash")
        current_revision = git_command("rev-parse", "HEAD")
        print("On branch {}, switching to {}".format(current_revision, revision))
        git_command("checkout", revision)

    start = time.time()
    ret = subprocess.run(["./gradlew", ":test", "--tests", "org.rustPerformanceTests.RsRealProjectAnalysisTest"],
                         stdout=subprocess.PIPE,
                         stderr=subprocess.PIPE,
                         cwd=ROOT_PATH)

    print("Finished in {} seconds with exit code {}".format(time.time() - start, ret.returncode))

    stdout = ret.stdout.decode()

    projects = {}
    current_project = ""
    current_file = ""
    for line in stdout.splitlines():
        line = line.strip()
        match = RE_ANALYZE_PROJECT.match(line)
        if match:
            current_project = match.group(1)
            projects[current_project] = defaultdict(lambda: [])
        match = RE_ERROR_FILE.match(line)
        if match:
            current_file = match.group(1)
        match = RE_ERROR_LINE.match(line)
        if match:
            projects[current_project][current_file].append(match.group(1))

    output_filename = "{}.txt".format(name)
    with open(output_filename, "w") as output:
        for (project, files) in projects.items():
            files = sorted(files.items())
            total_errors = 0

            output.write("{}\n".format(project))
            for (file, errors) in files:
                total_errors += len(errors)
                output.write("\n{}\n".format(file))
                for error in sorted(errors):
                    output.write("{}\n".format(error))
            print("Project {}: {} errors total".format(project, total_errors))

    print("Output written into {}".format(output_filename))

    if revision:
        print("Switching back to {}".format(current_revision))
        git_command("checkout", current_revision)
        git_command("stash", "pop")


@click.group()
def cli():
    pass


if __name__ == "__main__":
    cli.add_command(init)
    cli.add_command(run)
    cli()
