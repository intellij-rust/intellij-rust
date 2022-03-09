import argparse
from urllib import request

from bs4 import BeautifulSoup
from github import Github

ISSUE = 'https://github.com/intellij-rust/intellij-rust/issues/886'
RUST_LANG = 'https://doc.rust-lang.org/error-index.html'


def parse_error_from_issue(error):
    try:
        local_soup = BeautifulSoup(str(error), 'html.parser')
        error_id = str(local_soup.find_all(rel='nofollow')[0].get_text())
        if not error_id.startswith("E"):
            return None
        return error_id
    except Exception:
        return None


def get_errors_from_issue():
    try:
        wp = request.urlopen(ISSUE)
        page_text = wp.read()
    except Exception:
        return None

    soup = BeautifulSoup(page_text, 'html.parser')
    errors_list = soup.find_all('li', 'task-list-item')
    errors = set()
    for e in errors_list:
        errors.add(parse_error_from_issue(e))
    errors.remove(None)

    return errors


def parse_error_from_rust_lang(error: str):
    local_soup = BeautifulSoup(error, 'html.parser')
    marked_outdated = 'this error code is no longer emitted by the compiler' in error
    error_id = local_soup.find_all('h2', 'section-header')[0].get('id')

    # Some errors (e.x. E0388) may not have description
    try:
        error_description = str(local_soup.find_all('p')[0]).replace("<p>", '').replace("</p>", '').replace('\n', ' ')
    except IndexError:
        error_description = "can't parse description :("

    return marked_outdated, error_id, error_description


def get_errors_from_rust_lang():
    try:
        wp = request.urlopen(RUST_LANG)
        page_text = wp.read()
    except Exception:
        return None, None

    soup = BeautifulSoup(page_text, 'html.parser')
    errors_list = soup.find_all('div', 'error-described')

    # normal number of errors is 400+
    if len(errors_list) < 200:
        return None, None

    errors = dict()
    outdated_errors = dict()

    for e in errors_list:
        outdated, id_error, err_description = parse_error_from_rust_lang(str(e))
        if not outdated:
            errors.update({id_error: err_description})
        else:
            outdated_errors.update({id_error: err_description})

    return errors, outdated_errors


def generate_list(ids: set, dictionary):
    ids = list(ids)
    ids.sort()
    t = ''
    for j in ids:
        t += "1. [{}](https://doc.rust-lang.org/error-index.html#{}): {} \n".format(j, j, dictionary.get(j))
    return t


def get_text():
    msg = ''
    rust_errors_with_description, outdated_rust_errors_with_description = get_errors_from_rust_lang()
    issue_errors = get_errors_from_issue()

    if None in [rust_errors_with_description, outdated_rust_errors_with_description, issue_errors]:
        return None

    rust_errors = set(rust_errors_with_description.keys())
    outdated_rust_errors = set(outdated_rust_errors_with_description.keys())

    not_found_in_issue = rust_errors.difference(issue_errors)

    if not_found_in_issue:
        msg += "These errors were not found in the meta issue https://github.com/neonaot/intellij-rust/issues/17:\n"
        msg += generate_list(not_found_in_issue, rust_errors_with_description)
        msg += '\n'

    outdated = issue_errors.intersection(outdated_rust_errors)
    if outdated:
        msg += "These errors are no longer emitted by the compiler:\n"
        msg += generate_list(outdated, outdated_rust_errors_with_description)
        msg += '\n'

    not_found = issue_errors.difference(rust_errors).difference(outdated)
    if not_found:
        msg += "These errors were not found on the page {}, " \
               "but they are in our tracking issue {}:\n".format(RUST_LANG, ISSUE)
        for i in not_found:
            msg += '1. {}\n'.format(i)
        msg += '\n'

    return msg


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument("--token", type=str, required=True)
    parser.add_argument("--repo", type=str, required=True)
    args = parser.parse_args()

    text = get_text()
    title = ''
    if text is None:
        title = "Check action `supported-errors`"
        text = 'Something went wrong at runtime, please check scripts.'
    elif len(text) == 0:
        print("logs: no need to update meta issue")
        exit()
    else:
        title = "Updating supported error list"
        text = "Hey!\n" \
               "This message was created automatically, and it may contain errors due to changes in used web pages.\n" \
               "If you want to fix (add or remove support) any of the errors mentioned here, " \
               "create an issue for this error and assign yourself. Thanks!\n\n" + text

    print(text)
    # TODO
    # g = Github(args.token)
    # repo = g.get_repo(args.repo)
    # repo.create_issue(title, text, assignee="neonaot")
