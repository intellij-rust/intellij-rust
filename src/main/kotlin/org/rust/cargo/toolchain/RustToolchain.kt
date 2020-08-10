/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain

import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapiext.isUnitTestMode
import com.intellij.util.io.exists
import com.intellij.util.text.SemVer
import org.rust.openapiext.*
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.format.DateTimeParseException

data class RustToolchain(val location: Path) {

    fun looksLikeValidToolchain(): Boolean =
        hasExecutable(CARGO) && hasExecutable(RUSTC)

    fun queryVersions(): VersionInfo {
        if (!isUnitTestMode) {
            checkIsBackgroundThread()
        }
        return VersionInfo(scrapeRustcVersion(pathToExecutable(RUSTC)))
    }

    fun getSysroot(projectDirectory: Path): String? {
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

    fun getStdlibFromSysroot(projectDirectory: Path): VirtualFile? {
        val sysroot = getSysroot(projectDirectory) ?: return null
        val fs = LocalFileSystem.getInstance()
        return fs.refreshAndFindFileByPath(FileUtil.join(sysroot, "lib/rustlib/src/rust"))
    }

    fun getCfgOptions(projectDirectory: Path): List<String>? {
        val timeoutMs = 10000
        val output = GeneralCommandLine(pathToExecutable(RUSTC))
            .withCharset(Charsets.UTF_8)
            .withWorkDirectory(projectDirectory)
            .withParameters("--print", "cfg")
            .execute(timeoutMs)
        return if (output?.isSuccess == true) output.stdoutLines else null
    }

    fun rawCargo(): Cargo = Cargo(pathToExecutable(CARGO), pathToExecutable(RUSTC))

    fun cargoOrWrapper(cargoProjectDirectory: Path?): Cargo {
        val hasXargoToml = cargoProjectDirectory?.resolve(XARGO_TOML)?.let { Files.isRegularFile(it) } == true
        val cargoWrapper = if (hasXargoToml && hasExecutable(XARGO)) XARGO else CARGO
        return Cargo(pathToExecutable(cargoWrapper), pathToExecutable(RUSTC))
    }

    fun rustup(cargoProjectDirectory: Path): Rustup? =
        if (isRustupAvailable)
            Rustup(this, pathToExecutable(RUSTUP), cargoProjectDirectory)
        else
            null

    fun rustfmt(): Rustfmt = Rustfmt(pathToExecutable(RUSTFMT))

    fun grcov(): Grcov? = if (hasCargoExecutable(GRCOV)) Grcov(pathToCargoExecutable(GRCOV)) else null

    fun evcxr(): Evcxr? = if (hasCargoExecutable(EVCXR)) Evcxr(pathToCargoExecutable(EVCXR)) else null

    fun wasmPack(): WasmPack? = if (hasCargoExecutable(WASM_PACK)) WasmPack(pathToCargoExecutable(WASM_PACK)) else null

    val isRustupAvailable: Boolean get() = hasExecutable(RUSTUP)

    val presentableLocation: String = pathToExecutable(CARGO).toString()

    // for executables from toolchain
    private fun pathToExecutable(toolName: String): Path {
        val exeName = if (SystemInfo.isWindows) "$toolName.exe" else toolName
        return location.resolve(exeName).toAbsolutePath()
    }

    // for executables installed using `cargo install`
    private fun pathToCargoExecutable(toolName: String): Path {
        // Binaries installed by `cargo install` (e.g. Grcov, Evcxr) are placed in ~/.cargo/bin by default:
        // https://doc.rust-lang.org/cargo/commands/cargo-install.html
        // But toolchain root may be different (e.g. on Arch Linux it is usually /usr/bin)
        val path = pathToExecutable(toolName)
        if (path.exists()) return path

        val exeName = if (SystemInfo.isWindows) "$toolName.exe" else toolName
        val cargoBinPath = File(FileUtil.expandUserHome("~/.cargo/bin")).toPath()
        return cargoBinPath.resolve(exeName).toAbsolutePath()
    }

    private fun hasExecutable(exec: String): Boolean =
        Files.isExecutable(pathToExecutable(exec))

    private fun hasCargoExecutable(exec: String): Boolean =
        Files.isExecutable(pathToCargoExecutable(exec))

    data class VersionInfo(
        val rustc: RustcVersion?
    )

    companion object {
        private const val RUSTC = "rustc"
        private const val RUSTFMT = "rustfmt"
        private const val CARGO = "cargo"
        private const val RUSTUP = "rustup"
        private const val XARGO = "xargo"
        private const val GRCOV = "grcov"
        private const val EVCXR = "evcxr"
        private const val WASM_PACK = "wasm-pack"

        const val CARGO_TOML = "Cargo.toml"
        const val CARGO_LOCK = "Cargo.lock"
        const val XARGO_TOML = "Xargo.toml"

        val MIN_SUPPORTED_TOOLCHAIN = SemVer.parseFromText("1.32.0")!!

        fun suggest(): RustToolchain? = Suggestions.all().mapNotNull {
            val candidate = RustToolchain(it.toPath().toAbsolutePath())
            if (candidate.looksLikeValidToolchain()) candidate else null
        }.firstOrNull()
    }
}

data class RustcVersion(
    val semver: SemVer,
    val host: String,
    val channel: RustChannel,
    val commitHash: String? = null,
    val commitDate: LocalDate? = null
)

private fun scrapeRustcVersion(rustc: Path): RustcVersion? {
    val lines = GeneralCommandLine(rustc)
        .withParameters("--version", "--verbose")
        .execute()
        ?.stdoutLines
        ?: return null

    return parseRustcVersion(lines)
}

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
        .filter { !it.isEmpty() }
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
            programFiles.listFiles { file -> file.isDirectory }.asSequence()
                .filter { it.nameWithoutExtension.toLowerCase().startsWith("rust") }
                .map { File(it, "bin") }

        return sequenceOf(fromHome) + fromProgramFiles
    }
}
