from github_connect import create_pull_request, get_branch, add_assignee
from git import git_command, has_git_changes

from abc import abstractmethod, ABC


class UpdaterBase(ABC):
    __repo: str
    __token: str
    __branch_name: str
    __message: str
    __assignee: str

    def __init__(self, repo: str, token: str, branch_name: str, message: str, assignee: str):
        self.__repo = repo
        self.__token = token
        self.__branch_name = branch_name
        self.__message = message
        self.__assignee = assignee

    @abstractmethod
    def _update_locally(self) -> None:
        pass

    def update(self) -> None:
        target_branch = get_branch(self.__repo, self.__token, self.__branch_name)
        if target_branch is not None:
            print(f"Repo already has `{self.__branch_name}` branch")
            return

        git_command("checkout", "-b", self.__branch_name)

        self._update_locally()

        if has_git_changes():
            git_command("commit", "-am", self.__message)

            git_command("push", "origin", self.__branch_name)
            pull_request = create_pull_request(self.__repo, self.__token, self.__branch_name, self.__message)
            add_assignee(self.__repo, self.__token, pull_request["number"], self.__assignee)
        else:
            print("Everything is up-to-date")
