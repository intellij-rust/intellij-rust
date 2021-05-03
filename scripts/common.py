import os
import re
import subprocess

from typing import Tuple, Optional

GRADLE_PROPERTIES: str = "gradle.properties"
PATCH_VERSION_RE: str = r"patchVersion=(\d+)"


def __get_patch_version() -> Tuple[str, int]:
    with open(GRADLE_PROPERTIES) as properties:
        text = properties.read()
    return text, get_patch_version_from_text(text)


def get_patch_version_from_text(text: str) -> int:
    return int(re.search(PATCH_VERSION_RE, text).group(1))


def get_patch_version() -> int:
    _, patch_version = __get_patch_version()
    return patch_version


def inc_patch_version():
    text, version = __get_patch_version()
    new_text = re.sub(PATCH_VERSION_RE, f"patchVersion={version + 1}", text)
    with open(GRADLE_PROPERTIES, mode="w") as properties:
        properties.write(new_text)


def execute_command(*args, print_stdout=True, check=True, cwd: Optional[str] = None) -> str:
    if print_stdout:
        print(list(args))

    try:
        result = subprocess.run(list(args),
                                check=check,
                                stdout=subprocess.PIPE,
                                stderr=subprocess.STDOUT,
                                cwd=cwd)
    except subprocess.CalledProcessError as e:
        if print_stdout:
            print(e.output.decode("utf-8"))
        raise e

    stdout_str = result.stdout.decode("utf-8").strip()
    if print_stdout:
        print(stdout_str)
    return stdout_str


def env(key: str) -> str:
    value = os.getenv(key)
    if value is None:
        raise Exception(f"{key} is not set")
    return value
