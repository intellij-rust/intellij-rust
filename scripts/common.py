import os
import re
import subprocess

from typing import Tuple

GRADLE_PROPERTIES: str = "gradle.properties"
PATCH_VERSION_RE: str = r"patchVersion=(\d+)"


def __get_patch_version() -> Tuple[str, int]:
    with open(GRADLE_PROPERTIES) as properties:
        text = properties.read()
    return text, int(re.search(PATCH_VERSION_RE, text).group(1))


def get_patch_version() -> int:
    _, patch_version = __get_patch_version()
    return patch_version


def inc_patch_version():
    text, version = __get_patch_version()
    new_text = re.sub(PATCH_VERSION_RE, f"patchVersion={version + 1}", text)
    with open(GRADLE_PROPERTIES, mode="w") as properties:
        properties.write(new_text)


def git_command(*args) -> str:
    print(["git"] + list(args))
    result = subprocess.run(["git"] + list(args), check=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    stdout_str = result.stdout.decode("utf-8").strip()
    print(stdout_str)
    return stdout_str


def env(key: str) -> str:
    value = os.getenv(key)
    if value is None:
        raise Exception(f"{key} is not set")
    return value


def construct_repository_url(token: str) -> str:
    actor = env("GITHUB_ACTOR")
    repo = env("GITHUB_REPOSITORY")
    return f"https://{actor}:{token}@github.com/{repo}.git"
