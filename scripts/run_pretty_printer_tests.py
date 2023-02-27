import os
import platform
import stat
import sys
from enum import Enum, auto
from os import path

import argparse

from common import execute_command


class OS(Enum):
    Linux = auto()
    MacOs = auto()
    Windows = auto()
    Other = auto()


class Architecture(Enum):
    X86_64 = "x64"
    ARM64 = "aarch64"
    Other = "other"


# TODO: migrate to match
def current_os() -> OS:
    if sys.platform == "linux":
        return OS.Linux
    elif sys.platform == "darwin":
        return OS.MacOs
    elif sys.platform == "win32":
        return OS.Windows
    else:
        return OS.Other


def current_architecture() -> Architecture:
    machine = platform.machine().lower()
    if machine == "x86_64" or machine == "amd64":
        return Architecture.X86_64
    elif machine == "aarch64" or machine == "arm64":
        return Architecture.ARM64
    else:
        return Architecture.Other


def set_executable(filepath: str):
    s = os.stat(filepath)
    # TODO: it seems `os.chmod` doesn't work on Windows with executable permissions
    os.chmod(filepath, s.st_mode | stat.S_IEXEC)


def run_lldb_tests(clion_version: str):
    host_os = current_os()
    arch_name = current_architecture().value

    if host_os == OS.MacOs:
        # TODO: Remove after CLion snapshot builds provide these files with required permissions
        lldb_mac_bin_dir = path.abspath(f"deps/clion-{clion_version}/bin/lldb/mac")
        set_executable(f"{lldb_mac_bin_dir}/lldb")
        set_executable(f"{lldb_mac_bin_dir}/LLDBFrontend")
        set_executable(f"{lldb_mac_bin_dir}/LLDB.framework/LLDB")

        # TODO: Use `lldb` Python module from CLion distribution
        lldb_path = "/Applications/Xcode.app/Contents/SharedFrameworks/LLDB.framework/Resources/Python"
        python = "python"
    elif host_os == OS.Linux:
        lldb_path = path.abspath(f"deps/clion-{clion_version}/bin/lldb/linux/{arch_name}/lib/python3.8/site-packages")
        # BACKCOMPAT: 2022.3. Since 2022.3.2 `os.path.exists(lldb_path)` should be always true
        if not path.exists(lldb_path):
            lldb_path = path.abspath(f"deps/clion-{clion_version}/bin/lldb/linux/lib/python3.8/site-packages")
        python = "python3"
    elif host_os == OS.Windows:
        lldb_bundle_path = path.abspath(f"deps/clion-{clion_version}/bin/lldb/win/{arch_name}")
        # Create symlink to allow `lldb` Python module perform `import _lldb` inside
        # TODO: Drop when this is implemented on CLion side
        os.symlink(f"{lldb_bundle_path}/bin/liblldb.dll", f"{lldb_bundle_path}/lib/site-packages/lldb/_lldb.pyd")

        lldb_path = f"{lldb_bundle_path}/lib/site-packages"
        python = f"{lldb_bundle_path}/bin/python.exe"
    else:
        raise Exception("Unsupported OS")

    if not path.exists(lldb_path):
        raise Exception(f"`{lldb_path}` doesn't exist")

    execute_command("cargo", "run", "--package", "pretty_printers_test", "--bin", "pretty_printers_test", "--",
                    "lldb", python, lldb_path, cwd="pretty_printers_tests")


def run_gdb_tests(clion_version: str):
    host_os = current_os()
    arch_name = current_architecture().value

    if host_os == OS.MacOs:
        gdb_binary = path.abspath(f"deps/clion-{clion_version}/bin/gdb/mac/bin/gdb")
    elif host_os == OS.Linux:
        gdb_binary = path.abspath(f"deps/clion-{clion_version}//bin/gdb/linux/{arch_name}/bin/gdb")
        # BACKCOMPAT: 2022.3. Since 2022.3.2 `os.path.exists(gdb_binary)` should be always true
        if not path.exists(gdb_binary):
            gdb_binary = path.abspath(f"deps/clion-{clion_version}/bin/gdb/linux/bin/gdb")
    elif host_os == OS.Windows:
        print("GDB pretty-printers tests are not supported yet for Windows")
        return
    else:
        raise Exception("Unsupported OS")

    if not path.exists(gdb_binary):
        raise Exception(f"`{gdb_binary}` doesn't exist")

    set_executable(gdb_binary)
    execute_command("cargo", "run", "--package", "pretty_printers_test", "--bin", "pretty_printers_test", "--",
                    "gdb", gdb_binary, cwd="pretty_printers_tests")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--clion_version", type=str, required=True)
    args = parser.parse_args()

    run_lldb_tests(args.clion_version)
    run_gdb_tests(args.clion_version)


if __name__ == '__main__':
    main()
