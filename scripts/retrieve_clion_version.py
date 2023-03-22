import argparse
import os
import re

CLION_VERSION_PROPERTY_RE = re.compile("clionVersion\\s*=\\s*(.+)")


def read_version_from_properties():
    parser = argparse.ArgumentParser()
    parser.add_argument("--path", type=str, required=True, help="Path to property file")
    args = parser.parse_args()
    with open(args.path) as f:
        text = f.read()
    # It doesn't support multiline values and escaping
    result = re.search(CLION_VERSION_PROPERTY_RE, text)
    if result is None:
        raise Exception(f"Failed to find `clionVersion` property in `{args.path}` file")
    return result.group(1)


def main():
    clion_version = os.getenv("ORG_GRADLE_PROJECT_clionVersion")
    if clion_version is None:
        clion_version = read_version_from_properties()

    print(clion_version.removeprefix("CL-"))


if __name__ == '__main__':
    main()
