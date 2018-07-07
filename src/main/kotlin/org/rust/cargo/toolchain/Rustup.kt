/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import org.rust.openapiext.GeneralCommandLine
import org.rust.openapiext.fullyRefreshDirectory
import org.rust.openapiext.withWorkDirectory
import java.nio.file.Path

private val LOG = Logger.getInstance(Rustup::class.java)

class Rustup(
    private val toolchain: RustToolchain,
    private val rustup: Path,
    private val projectDirectory: Path
) {
    sealed class DownloadResult {
        class Ok(val library: VirtualFile) : DownloadResult()
        class Err(val error: String) : DownloadResult()
    }

    fun downloadStdlib(): DownloadResult {
        val downloadProcessOutput = GeneralCommandLine(rustup)
            .withWorkDirectory(projectDirectory)
            .withParameters("component", "add", "rust-src")
            .exec()

        return if (downloadProcessOutput.exitCode != 0) {
            val message = "rustup failed: `${downloadProcessOutput.stderr}`"
            LOG.warn(message)
            DownloadResult.Err(message)
        } else {
            val sources = getStdlibFromSysroot() ?: return DownloadResult.Err("Failed to find stdlib in sysroot")
            fullyRefreshDirectory(sources)
            DownloadResult.Ok(sources)
        }
    }

    fun getStdlibFromSysroot(): VirtualFile? {
        val sysroot = toolchain.getSysroot(projectDirectory) ?: return null
        val fs = LocalFileSystem.getInstance()
        return fs.refreshAndFindFileByPath(FileUtil.join(sysroot, "lib/rustlib/src/rust/src"))
    }
}
