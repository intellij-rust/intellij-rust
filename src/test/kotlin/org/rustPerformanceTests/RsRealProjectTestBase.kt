/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rustPerformanceTests

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.vfs.*
import com.intellij.util.ui.UIUtil
import org.rust.cargo.RsWithToolchainTestBase
import org.rust.cargo.toolchain.RustToolchain
import org.rust.openapiext.fullyRefreshDirectory

abstract class RsRealProjectTestBase : RsWithToolchainTestBase() {
    protected fun openRealProject(info: RealProjectInfo): VirtualFile? {
        val base = openRealProject("testData/${info.path}", info.exclude)
        if (base == null) {
            val name = info.name
            println("SKIP $name: git clone ${info.gitUrl} testData/$name")
            return null
        }
        return base
    }

    private fun openRealProject(path: String, exclude: List<String> = emptyList()): VirtualFile? {
        val projectDir = LocalFileSystem.getInstance().refreshAndFindFileByPath(path)
            ?: return null

        fun isAppropriate(file: VirtualFile): Boolean {
            val relativePath = file.path.substring(projectDir.path.length + 1)
            // 1. Ignore excluded files
            if (exclude.any { relativePath.startsWith(it) }) return false
            // 2. Ignore hidden files
            if (file.name.startsWith(".")) return false
            // 3. Ignore excluded directories in the root of the project
            if (file.isDirectory &&
                file.name in EXCLUDED_DIRECTORY_NAMES &&
                file.parent.findChild(RustToolchain.CARGO_TOML) != null) return false

            // Otherwise, analyse it
            return true
        }

        runWriteAction {
            fullyRefreshDirectoryInUnitTests(projectDir)
            VfsUtil.copyDirectory(this, projectDir, cargoProjectDirectory, ::isAppropriate)
            fullyRefreshDirectoryInUnitTests(cargoProjectDirectory)
        }

        refreshWorkspace()
        UIUtil.dispatchAllInvocationEvents()
        return cargoProjectDirectory
    }

    class RealProjectInfo(
        val name: String,
        val path: String,
        val gitUrl: String,
        val exclude: List<String> = emptyList()
    )

    companion object {
        val RUSTC = RealProjectInfo(
            name = "rust",
            path = "rust",
            gitUrl = "https://github.com/rust-lang/rust",
            exclude = listOf(
                "src/llvm",
                "src/llvm-emscripten",
                "src/binaryen",
                "src/test",
                "src/ci",
                "src/rt",
                "src/compiler-rt",
                "src/jemalloc",
                "build",
                "tmp"
            )
        )
        val CARGO = RealProjectInfo("cargo", "cargo", "https://github.com/rust-lang/cargo")
        val MYSQL_ASYNC = RealProjectInfo("mysql_async", "mysql_async", "https://github.com/blackbeam/mysql_async")
        val TOKIO = RealProjectInfo("tokio", "tokio", "https://github.com/tokio-rs/tokio")
        val AMETHYST = RealProjectInfo("amethyst", "amethyst", "https://github.com/amethyst/amethyst")
        val CLAP = RealProjectInfo("clap", "clap", "https://github.com/clap-rs/clap")
        val DIESEL = RealProjectInfo("diesel", "diesel", "https://github.com/diesel-rs/diesel")
        val RUST_ANALYZER = RealProjectInfo("rust-analyzer", "rust-analyzer", "https://github.com/rust-analyzer/rust-analyzer")
        val XI_EDITOR = RealProjectInfo("xi-editor", "xi-editor/rust", "https://github.com/xi-editor/xi-editor")
        val JUNIPER = RealProjectInfo("juniper", "juniper", "https://github.com/graphql-rust/juniper")

        private val EXCLUDED_DIRECTORY_NAMES = setOf("target")
    }
}

fun VirtualFile.findDescendants(filter: (VirtualFile) -> Boolean): ArrayList<VirtualFile> {
    val result = ArrayList<VirtualFile>()
    VfsUtilCore.visitChildrenRecursively(this,
        object : VirtualFileVisitor<ArrayList<VirtualFile>>() {
            override fun visitFile(file: VirtualFile): Boolean {
                if (!file.isDirectory && filter(file)) result.add(file)
                return true
            }
        })
    return result
}

fun fullyRefreshDirectoryInUnitTests(directory: VirtualFile) {
    // It's very weird, but real refresh occurs only if
    // we touch file names. At least in the test environment
    VfsUtilCore.iterateChildrenRecursively(directory, null) { it.name; true }
    fullyRefreshDirectory(directory)
}
