/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain

import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.text.SemVer
import org.rust.openapiext.*
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

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
        val timeoutMs = 10000
        val output = GeneralCommandLine(pathToExecutable(RUSTC))
            .withCharset(Charsets.UTF_8)
            .withWorkDirectory(projectDirectory)
            .withParameters("--print", "sysroot")
            .execute(timeoutMs)
        return if (output?.isSuccess == true) output.stdout.trim() else null
    }

    fun rawCargo(): Cargo = Cargo(pathToExecutable(CARGO))

    fun cargoOrWrapper(cargoProjectDirectory: Path?): Cargo {
        val hasXargoToml = cargoProjectDirectory?.resolve(XARGO_TOML)?.let { Files.isRegularFile(it) } == true
        val cargoWrapper = if (hasXargoToml && hasExecutable(XARGO)) XARGO else CARGO
        return Cargo(pathToExecutable(cargoWrapper))
    }

    fun rustup(cargoProjectDirectory: Path): Rustup? =
        if (isRustupAvailable)
            Rustup(this, pathToExecutable(RUSTUP), cargoProjectDirectory)
        else
            null

    fun rustfmt(): Rustfmt = Rustfmt(pathToExecutable(RUSTFMT))

    val isRustupAvailable: Boolean get() = hasExecutable(RUSTUP)

    val presentableLocation: String = pathToExecutable(CARGO).toString()

    private fun pathToExecutable(toolName: String): Path {
        val exeName = if (SystemInfo.isWindows) "$toolName.exe" else toolName
        return location.resolve(exeName).toAbsolutePath()
    }

    private fun hasExecutable(exec: String): Boolean =
        Files.isExecutable(pathToExecutable(exec))

    data class VersionInfo(
        val rustc: RustcVersion?
    )

    companion object {
        private const val RUSTC = "rustc"
        private const val RUSTFMT = "rustfmt"
        private const val CARGO = "cargo"
        private const val RUSTUP = "rustup"
        private const val XARGO = "xargo"

        const val CARGO_TOML = "Cargo.toml"
        const val CARGO_LOCK = "Cargo.lock"
        const val XARGO_TOML = "Xargo.toml"

        fun suggest(): RustToolchain? = Suggestions.all().mapNotNull {
            val candidate = RustToolchain(it.toPath().toAbsolutePath())
            if (candidate.looksLikeValidToolchain()) candidate else null
        }.firstOrNull()
    }
}

data class RustcVersion(
    val semver: SemVer,
    val host: String,
    val channel: RustChannel
)

private fun scrapeRustcVersion(rustc: Path): RustcVersion? {
    val lines = GeneralCommandLine(rustc)
        .withParameters("--version", "--verbose")
        .execute()
        ?.stdoutLines
        ?: return null

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
    val find = { re: Regex -> lines.mapNotNull { re.matchEntire(it) }.firstOrNull() }

    val releaseMatch = find(releaseRe) ?: return null
    val hostText = find(hostRe)?.groups?.get(1)?.value ?: return null
    val versionText = releaseMatch.groups[1]?.value ?: return null

    val semVer = SemVer.parseFromText(versionText) ?: return null

    val releaseSuffix = releaseMatch.groups[2]?.value.orEmpty()
    val channel = when {
        releaseSuffix.isEmpty() -> RustChannel.STABLE
        releaseSuffix.startsWith("-beta") -> RustChannel.BETA
        releaseSuffix.startsWith("-nightly") -> RustChannel.NIGHTLY
        else -> RustChannel.DEFAULT
    }
    return RustcVersion(semVer, hostText, channel)
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
