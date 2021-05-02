from common import execute_command


def git_command(*args, print_stdout=True, check=True) -> str:
    return execute_command("git", *args, print_stdout=print_stdout, check=check)
