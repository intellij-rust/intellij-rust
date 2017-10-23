/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.text.SemVer
import org.rust.openapiext.GeneralCommandLine
import org.rust.openapiext.checkIsBackgroundThread
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

private val LOG = Logger.getInstance(RustToolchain::class.java)

data class RustToolchain(val location: Path) {

    fun looksLikeValidToolchain(): Boolean =
        hasExecutable(CARGO) && hasExecutable(RUSTC)

    fun queryVersions(): VersionInfo {
        checkIsBackgroundThread()
        return VersionInfo(scrapeRustcVersion(pathToExecutable(RUSTC)))
    }

    fun rawCargo(): Cargo =
        Cargo(pathToExecutable(CARGO), pathToExecutable(RUSTC))

    fun cargoOrWrapper(cargoProjectDirectory: Path?): Cargo {
        val hasXargoToml = cargoProjectDirectory?.resolve(XARGO_TOML)?.let { Files.isRegularFile(it) } == true
        val cargoWrapper = if (hasXargoToml && hasExecutable(XARGO)) XARGO else CARGO
        return Cargo(pathToExecutable(cargoWrapper), pathToExecutable(RUSTC))
    }

    fun rustup(cargoProjectDirectory: Path): Rustup? =
        if (isRustupAvailable)
            Rustup(pathToExecutable(RUSTUP), pathToExecutable(RUSTC), cargoProjectDirectory)
        else
            null

    val isRustupAvailable: Boolean get() = hasExecutable(RUSTUP)

    val presentableLocation: String = pathToExecutable(CARGO).toString()

    private fun pathToExecutable(toolName: String): Path {
        val exeName = if (SystemInfo.isWindows) "$toolName.exe" else toolName
        return location.resolve(exeName).toAbsolutePath()
    }

    private fun hasExecutable(exec: String): Boolean =
        Files.isExecutable(pathToExecutable(exec))

    data class VersionInfo(
        val rustc: RustcVersion
    )

    companion object {
        private val RUSTC = "rustc"
        private val CARGO = "cargo"
        private val RUSTUP = "rustup"
        private val XARGO = "xargo"

        val CARGO_TOML = "Cargo.toml"
        val CARGO_LOCK = "Cargo.lock"
        val XARGO_TOML = "Xargo.toml"

        fun suggest(): RustToolchain? = Suggestions.all().mapNotNull {
            val candidate = RustToolchain(it.toPath().toAbsolutePath())
            if (candidate.looksLikeValidToolchain()) candidate else null
        }.firstOrNull()
    }
}

data class RustcVersion(
    val semver: SemVer?,
    val nightlyCommitHash: String?
) {
    companion object {
        val UNKNOWN = RustcVersion(null, null)
    }
}

private fun scrapeRustcVersion(rustc: Path): RustcVersion {
    val lines = GeneralCommandLine(rustc)
        .withParameters("--version", "--verbose")
        .runExecutable()
        ?: return RustcVersion.UNKNOWN

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
    val commitHashRe = "commit-hash: (.*)".toRegex()
    val releaseRe = """release: (\d+\.\d+\.\d+)(.*)""".toRegex()
    val find = { re: Regex -> lines.mapNotNull { re.matchEntire(it) }.firstOrNull() }

    val commitHash = find(commitHashRe)?.let { it.groups[1]!!.value }
    val releaseMatch = find(releaseRe) ?: return RustcVersion.UNKNOWN
    val versionText = releaseMatch.groups[1]?.value ?: return RustcVersion.UNKNOWN

    val semVer = SemVer.parseFromText(versionText) ?: return RustcVersion.UNKNOWN
    val isStable = releaseMatch.groups[2]?.value.isNullOrEmpty()
    return RustcVersion(semVer, if (isStable) null else commitHash)
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

private fun GeneralCommandLine.runExecutable(): List<String>? {
    val procOut = try {
        val timeoutMs = 1000
        CapturingProcessHandler(this).runProcess(timeoutMs)
    } catch (e: ExecutionException) {
        LOG.warn("Failed to run executable!", e)
        return null
    }

    if (procOut.exitCode != 0 || procOut.isCancelled || procOut.isTimeout)
        return null

    return procOut.stdoutLines
}
