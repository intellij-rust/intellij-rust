import os

PREAMBLE: str = """
/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */
""".strip()


def file_has_license(file_path: str) -> bool:
    with open(file_path) as file:
        text = file.read()
        return PREAMBLE in text


if __name__ == '__main__':
    no_license = []
    for dir_path, dirs, files in os.walk("."):
        for filename in files:
            path = os.path.join(dir_path, filename)
            if path.endswith(".kt") and not file_has_license(path):
                no_license.append(path)

    if len(no_license) > 0:
        print("Files without license notice:")
        for path in no_license:
            print(path)
        exit(1)
    else:
        print("License OK!")
