/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain.tools

import com.intellij.execution.process.ProcessListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import org.rust.cargo.CfgOptions
import org.rust.cargo.toolchain.RsToolchainBase
import org.rust.cargo.toolchain.RsToolchainBase.Companion.RUSTC_BOOTSTRAP
import org.rust.cargo.toolchain.impl.RustcVersion
import org.rust.cargo.toolchain.impl.parseRustcVersion
import org.rust.openapiext.*
import java.nio.file.Path

fun RsToolchainBase.rustc(): Rustc = Rustc(this)

class Rustc(toolchain: RsToolchainBase) : RustupComponent(NAME, toolchain) {

    fun queryVersion(workingDirectory: Path? = null): RustcVersion? {
        if (!isUnitTestMode) {
            checkIsBackgroundThread()
        }
        val lines = createBaseCommandLine("--version", "--verbose", workingDirectory = workingDirectory)
            .execute(toolchain.executionTimeoutInMilliseconds)
            ?.stdoutLines
        return lines?.let { parseRustcVersion(it) }
    }

    fun queryVersion(
        workingDirectory: Path,
        owner: Disposable,
        listener: ProcessListener
    ): RsProcessResult<RustcVersion?> {
        if (!isUnitTestMode) {
            checkIsBackgroundThread()
        }
        return createBaseCommandLine("--version", "--verbose", workingDirectory = workingDirectory)
            .execute(owner, listener = listener)
            .map { parseRustcVersion(it.stdoutLines) }
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

        if (output?.isSuccess != true) return null
        return toolchain.toLocalPath(output.stdout.trim())
    }

    fun getStdlibPathFromSysroot(projectDirectory: Path): String? {
        val sysroot = getSysroot(projectDirectory) ?: return null
        return FileUtil.join(sysroot, "lib/rustlib/src/rust")
    }

    fun getStdlibFromSysroot(projectDirectory: Path): VirtualFile? {
        val stdlibPath = getStdlibPathFromSysroot(projectDirectory) ?: return null
        val fs = LocalFileSystem.getInstance()
        return fs.refreshAndFindFileByPath(stdlibPath)
    }

    private fun getRawCfgOption(projectDirectory: Path?): List<String>? {
        val timeoutMs = 10000
        val output = createBaseCommandLine(
            "--print", "cfg",
            workingDirectory = projectDirectory,
            environment = mapOf(RUSTC_BOOTSTRAP to "1")
        ).execute(timeoutMs)
        return if (output?.isSuccess == true) output.stdoutLines else null
    }

    fun getCfgOptions(projectDirectory: Path?): CfgOptions {
        // Running "cargo rustc -- --print cfg" causes an error when run in a project with multiple targets
        // error: extra arguments to `rustc` can only be passed to one target, consider filtering
        // the package by passing e.g. `--lib` or `--bin NAME` to specify a single target
        // Running "cargo rustc --bin=projectname  -- --print cfg" we can get around this
        // but it also compiles the whole project, which is probably not wanted
        // TODO: This does not query the target specific cfg flags during cross compilation :-(
        val rawCfgOptions = getRawCfgOption(projectDirectory) ?: emptyList()
        return CfgOptions.parse(rawCfgOptions)
    }

    fun getTargets(projectDirectory: Path?): List<String>? {
        if (!isUnitTestMode) {
            checkIsBackgroundThread()
        }
        val timeoutMs = 10000
        val output = createBaseCommandLine(
            "--print", "target-list",
            workingDirectory = projectDirectory
        ).execute(timeoutMs)
        return if (output?.isSuccess == true) output.stdout.trim().lines() else null
    }

    companion object {
        const val NAME: String = "rustc"
    }
}
