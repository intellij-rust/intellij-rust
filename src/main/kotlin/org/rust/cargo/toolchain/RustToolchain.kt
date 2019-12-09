/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain

import com.google.common.annotations.VisibleForTesting
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapiext.isUnitTestMode
import com.intellij.util.text.SemVer
import org.rust.openapiext.checkIsBackgroundThread
import org.rust.openapiext.execute
import org.rust.openapiext.isSuccess
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDate
import java.time.format.DateTimeParseException

abstract class RustToolchain(open val location: Path) {

    /**
     * @return true if the tool is in the toolchain and is executable
     */
    abstract fun hasExecutable(name: String): Boolean

    /**
     * @return true if the toolchain contains the cargo and rustc binaries
     */
    fun looksLikeValidToolchain(): Boolean = hasExecutable(CARGO) && hasExecutable(RUSTC)

    /**
     * @return version information about the rust compiler
     */
    fun queryVersions(): VersionInfo {
        if (!isUnitTestMode) {
            checkIsBackgroundThread()
        }

        val version = runTool(RUSTC, "--version", "--verbose")
            ?: return VersionInfo(null)

        return VersionInfo(rustc = parseRustcVersion(version.stdoutLines))
    }

    open fun getSysroot(projectDirectory: Path): String? {
        if (!isUnitTestMode) {
            checkIsBackgroundThread()
        }
        val output = runTool(RUSTC, "--print", "sysroot", workingDirectory = projectDirectory, timeout = 10000)
        return if (output?.isSuccess == true) output.stdout.trim() else null
    }

    fun getStdlibFromSysroot(projectDirectory: Path): VirtualFile? {
        val sysroot = getSysroot(projectDirectory) ?: return null
        val fs = LocalFileSystem.getInstance()
        return fs.refreshAndFindFileByPath(FileUtil.join(sysroot, "lib/rustlib/src/rust/src"))
    }

    fun getCfgOptions(projectDirectory: Path): List<String>? {
        val output = runTool(RUSTC, "--print", "cfg", workingDirectory = projectDirectory, timeout = 10000)
        return if (output?.isSuccess == true) output.stdoutLines else null
    }

    fun rawCargo(): Cargo = Cargo(this, CARGO)

    fun cargoOrWrapper(cargoProjectDirectory: Path?): Cargo {
        val hasXargoToml = cargoProjectDirectory?.resolve(XARGO_TOML)?.let { Files.isRegularFile(it) } == true
        val cargoWrapper = if (hasXargoToml && hasExecutable(XARGO)) XARGO else CARGO
        return Cargo(this, cargoWrapper)
    }

    fun rustup(cargoProjectDirectory: Path): Rustup? = when {
        isRustupAvailable -> Rustup(this, cargoProjectDirectory)
        else -> null
    }

    fun rustfmt(): Rustfmt = Rustfmt(this)

    fun grcov(): Grcov? = if (hasExecutable(GRCOV)) Grcov(this) else null

    fun evcxr(): Evcxr? = if (hasExecutable(EVCXR)) Evcxr(this) else null

    abstract val isRustupAvailable: Boolean

    abstract val presentableLocation: String

    abstract fun createGeneralCommandLine(tool: String, vararg arguments: String, workingDirectory: Path?, setup: GeneralCommandLine.() -> Unit = {}): GeneralCommandLine

    fun runTool(name: String, vararg arguments: String, workingDirectory: Path? = null, timeout: Int? = 1000): ProcessOutput? {
        return createGeneralCommandLine(name, *arguments, workingDirectory = workingDirectory)
            .execute(timeout)
    }

    fun runTool(name: String, vararg arguments: String, workingDirectory: Path? = null, owner: Disposable, ignoreExitCode: Boolean = true, stdIn: ByteArray? = null, listener: ProcessListener? = null): ProcessOutput {
        return createGeneralCommandLine(name, *arguments, workingDirectory = workingDirectory)
            .execute(owner, ignoreExitCode = ignoreExitCode, stdIn = stdIn, listener = listener)
    }

    data class VersionInfo(
        val rustc: RustcVersion?
    )

    companion object {
        const val RUSTC = "rustc"
        const val RUSTFMT = "rustfmt"
        const val CARGO = "cargo"
        const val RUSTUP = "rustup"
        const val XARGO = "xargo"
        const val GRCOV = "grcov"
        const val EVCXR = "evcxr"

        const val CARGO_TOML = "Cargo.toml"
        const val CARGO_LOCK = "Cargo.lock"
        const val XARGO_TOML = "Xargo.toml"

        fun get(location: Path): RustToolchain {
            return when {
                location.toString().startsWith("""\\wsl$\""") -> WslRustToolchain(location)
                else -> NativeRustToolchain(location)
            }
        }

        fun get(location: String): RustToolchain {
            return get(Paths.get(location))
        }

        fun suggest(): RustToolchain? = Suggestions.all()
            .map { get(it.toPath().toAbsolutePath()) }
            .firstOrNull { it.looksLikeValidToolchain() }

        @VisibleForTesting
        fun parseRustcVersion(lines: List<String>): RustcVersion? {
            // We want to parse following
            //
            //  ```
            //  rustc 1.8.0-beta.1 (facbfdd71 2016-03-02)
            //  binary: rustc
            //  commit-hash: facbfdd71cb6ed0aeaeb06b6b8428f333de4072b
            //  commit-date: 2016-03-02
            //  host: x86_64-unknown-linux-gnu
            //  release: 1.8.0-beta.1
            //  ```
            val releaseRe = """release: (\d+\.\d+\.\d+)(.*)""".toRegex()
            val hostRe = "host: (.*)".toRegex()
            val commitHashRe = "commit-hash: ([A-Fa-f0-9]{40})".toRegex()
            val commitDateRe = """commit-date: (\d{4}-\d{2}-\d{2})""".toRegex()
            val find = { re: Regex -> lines.mapNotNull { re.matchEntire(it) }.firstOrNull() }

            val releaseMatch = find(releaseRe) ?: return null
            val hostText = find(hostRe)?.groups?.get(1)?.value ?: return null
            val versionText = releaseMatch.groups[1]?.value ?: return null
            val commitHash = find(commitHashRe)?.groups?.get(1)?.value
            val commitDate = try {
                find(commitDateRe)?.groups?.get(1)?.value?.let(LocalDate::parse)
            } catch (e: DateTimeParseException) {
                null
            }

            val semVer = SemVer.parseFromText(versionText) ?: return null
            val releaseSuffix = releaseMatch.groups[2]?.value.orEmpty()
            val channel = when {
                releaseSuffix.isEmpty() -> RustChannel.STABLE
                releaseSuffix.startsWith("-beta") -> RustChannel.BETA
                releaseSuffix.startsWith("-nightly") -> RustChannel.NIGHTLY
                releaseSuffix.startsWith("-dev") -> RustChannel.DEV
                else -> RustChannel.DEFAULT
            }
            return RustcVersion(semVer, hostText, channel, commitHash, commitDate)
        }
    }
}

data class RustcVersion(
    val semver: SemVer,
    val host: String,
    val channel: RustChannel,
    val commitHash: String? = null,
    val commitDate: LocalDate? = null
)

private object Suggestions {
    fun all() = sequenceOf(
        fromRustup(),
        fromPath(),
        forMac(),
        forUnix(),
        forWindows()
    ).flatten()

    private fun fromRustup(): Sequence<File> {
        val file = File(FileUtil.expandUserHome("~/.cargo/bin"))
        return if (file.isDirectory) {
            sequenceOf(file)
        } else {
            emptySequence()
        }
    }

    private fun fromPath(): Sequence<File> = System.getenv("PATH").orEmpty()
        .split(File.pathSeparator)
        .asSequence()
        .filter { it.isNotEmpty() }
        .map(::File)
        .filter { it.isDirectory }

    private fun forUnix(): Sequence<File> {
        if (!SystemInfo.isUnix) return emptySequence()

        return sequenceOf(File("/usr/local/bin"))
    }

    private fun forMac(): Sequence<File> {
        if (!SystemInfo.isMac) return emptySequence()

        return sequenceOf(File("/usr/local/Cellar/rust/bin"))
    }

    private fun forWindows(): Sequence<File> {
        if (!SystemInfo.isWindows) return emptySequence()
        val fromHome = File(System.getProperty("user.home") ?: "", ".cargo/bin")

        val programFiles = File(System.getenv("ProgramFiles") ?: "")
        val fromProgramFiles = if (!programFiles.exists() || !programFiles.isDirectory)
            emptySequence()
        else
            programFiles.listFiles { file -> file.isDirectory }?.asSequence()
                ?.filter { it.nameWithoutExtension.toLowerCase().startsWith("rust") }
                ?.map { File(it, "bin") } ?: emptySequence()
        val fromWsl: Sequence<File> = if (SystemInfo.isWin10OrNewer) {
            try {
                val nixPathBytes = Runtime.getRuntime().exec("""bash.exe -c "source ~/.profile && which cargo"""")
                    .inputStream.readAllBytes()
                val nixPath = String(nixPathBytes).trim()
                val wslPathBytes = Runtime.getRuntime().exec("""bash.exe -c "wslpath -w $nixPath"""")
                    .inputStream.readAllBytes()
                sequenceOf(File(String(wslPathBytes).trim()).parentFile)
            } catch (e: IOException) {
                emptySequence<File>()
            }
        } else emptySequence()

        return sequenceOf(fromHome) + fromProgramFiles + fromWsl
    }
}
