from typing import Optional

from common import execute_command


def git_command(*args, print_stdout=True, check=True, cwd: Optional[str] = None) -> str:
    return execute_command("git", *args, print_stdout=print_stdout, check=check, cwd=cwd)
