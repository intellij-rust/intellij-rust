from common import get_patch_version

if __name__ == '__main__':
    patch_version = get_patch_version()
    print(f"release-{patch_version - 1}", end="")
