/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain.tools

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapiext.isUnitTestMode
import org.rust.cargo.toolchain.RsToolchain
import org.rust.cargo.toolchain.impl.RustcVersion
import org.rust.cargo.toolchain.impl.parseRustcVersion
import org.rust.openapiext.checkIsBackgroundThread
import org.rust.openapiext.execute
import org.rust.openapiext.isSuccess
import java.nio.file.Path

fun RsToolchain.rustc(): Rustc = Rustc(this)

class Rustc(toolchain: RsToolchain) : RustupComponent(NAME, toolchain) {

    fun queryVersion(): RustcVersion? {
        if (!isUnitTestMode) {
            checkIsBackgroundThread()
        }
        val lines = createBaseCommandLine("--version", "--verbose").execute()?.stdoutLines
        return lines?.let { parseRustcVersion(it) }
    }

    fun getSysroot(projectDirectory: Path): String? {
        if (!isUnitTestMode) {
            checkIsBackgroundThread()
        }
        val timeoutMs = 10000
        val output = createBaseCommandLine(
            "--print", "sysroot",
            workingDirectory = projectDirectory
        ).execute(timeoutMs)
        return if (output?.isSuccess == true) output.stdout.trim() else null
    }

    fun getStdlibFromSysroot(projectDirectory: Path): VirtualFile? {
        val sysroot = getSysroot(projectDirectory) ?: return null
        val fs = LocalFileSystem.getInstance()
        return fs.refreshAndFindFileByPath(FileUtil.join(sysroot, "lib/rustlib/src/rust"))
    }

    fun getCfgOptions(projectDirectory: Path): List<String>? {
        val timeoutMs = 10000
        val output = createBaseCommandLine(
            "--print", "cfg",
            workingDirectory = projectDirectory
        ).execute(timeoutMs)
        return if (output?.isSuccess == true) output.stdoutLines else null
    }

    companion object {
        const val NAME: String = "rustc"
    }
}
