/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain

import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapiext.isUnitTestMode
import org.rust.openapiext.*
import java.nio.file.Files
import java.nio.file.Path

class NativeRustToolchain(location: Path) : RustToolchain(location) {
    override fun looksLikeValidToolchain() = hasExecutable(CARGO) && hasExecutable(RUSTC)

    override fun queryVersions(): VersionInfo {
        if (!isUnitTestMode) {
            checkIsBackgroundThread()
        }

        return VersionInfo(scrapeRustcVersion(pathToExecutable(RUSTC)))
    }

    private fun scrapeRustcVersion(rustc: Path): RustcVersion? {

        return parseRustcVersion(GeneralCommandLine(rustc)
            .withParameters("--version", "--verbose")
            .execute()
            ?.stdoutLines
            ?: return null)
    }

    override fun getSysroot(projectDirectory: Path): String? {
        if (!isUnitTestMode) {
            checkIsBackgroundThread()
        }
        val timeoutMs = 10000
        val output = GeneralCommandLine(pathToExecutable(RUSTC))
            .withCharset(Charsets.UTF_8)
            .withWorkDirectory(projectDirectory)
            .withParameters("--print", "sysroot")
            .execute(timeoutMs)
        return if (output?.isSuccess == true) output.stdout.trim() else null

    }

    override fun getStdlibFromSysroot(projectDirectory: Path): VirtualFile? {
        val sysroot = getSysroot(projectDirectory) ?: return null
        val fs = LocalFileSystem.getInstance()
        return fs.refreshAndFindFileByPath(FileUtil.join(sysroot, "lib/rustlib/src/rust/src"))
    }

    override fun getCfgOptions(projectDirectory: Path): List<String>? {
        val timeoutMs = 10000
        val output = GeneralCommandLine(pathToExecutable(RUSTC))
            .withCharset(Charsets.UTF_8)
            .withWorkDirectory(projectDirectory)
            .withParameters("--print", "cfg")
            .execute(timeoutMs)
        return if (output?.isSuccess == true) output.stdoutLines else null
    }

    override fun rawCargo(): Cargo = Cargo(pathToExecutable(CARGO))

    override fun cargoOrWrapper(cargoProjectDirectory: Path?): Cargo {
        val hasXargoToml = cargoProjectDirectory?.resolve(XARGO_TOML)?.let { Files.isRegularFile(it) } == true
        val cargoWrapper = if (hasXargoToml && hasExecutable(XARGO)) XARGO else CARGO
        return Cargo(pathToExecutable(cargoWrapper))
    }

    override fun rustup(cargoProjectDirectory: Path): Rustup? = when {
        isRustupAvailable -> Rustup(this, pathToExecutable(RUSTUP), cargoProjectDirectory)
        else -> null
    }

    private fun hasExecutable(exec: String): Boolean =
        Files.isExecutable(pathToExecutable(exec))

    private fun pathToExecutable(toolName: String): Path {
        val exeName = if (SystemInfo.isWindows) "$toolName.exe" else toolName
        return location.resolve(exeName).toAbsolutePath()
    }

    override fun rustfmt(): Rustfmt = Rustfmt(pathToExecutable(RUSTFMT))

    override fun grcov(): Grcov? = if (hasExecutable(GRCOV)) Grcov(pathToExecutable(GRCOV)) else null

    override fun evcxr(): Evcxr? = if (hasExecutable(EVCXR)) Evcxr(pathToExecutable(EVCXR)) else null

    override val isRustupAvailable: Boolean
        get() = hasExecutable(RUSTUP)

    override val presentableLocation: String
        get() = pathToExecutable(CARGO).toString()
}
