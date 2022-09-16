import argparse
import github
from common import env

IN_PROGRESS_COLUMN_ID = 6050136
TEST_COLUMN_ID = 6050141

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument("--token", type=str, required=True)
    parser.add_argument("--pull_request", type=int, required=True)
    args = parser.parse_args()

    repo = env("GITHUB_REPOSITORY")

    g = github.Github(args.token)
    repo = g.get_repo(repo)
    pr = repo.get_pull(args.pull_request)

    column_id = TEST_COLUMN_ID if pr.is_merged() else IN_PROGRESS_COLUMN_ID
    column = g.get_project_column(column_id)

    try:
        column.create_card(content_id=pr.id, content_type="PullRequest")
    except github.GithubException as e:
        if e.status != 422:
            raise e
        print("This PR should already be on the board.")
