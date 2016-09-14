package org.rust.cargo.toolchain

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.PathUtil
import com.intellij.util.text.SemVer
import org.rust.utils.seconds
import java.io.File

private val LOG = Logger.getInstance(RustToolchain::class.java)

data class RustToolchain(val location: String) {

    fun looksLikeValidToolchain(): Boolean =
        hasExecutable(CARGO) && hasExecutable(RUSTC)

    fun queryVersions(): VersionInfo {
        check(!ApplicationManager.getApplication().isDispatchThread)

        val rustup = GeneralCommandLine(pathToExecutable(RUSTUP))
            .withParameters("--version")
            .runExecutable()?.let { findSemVer(it) }

        return VersionInfo(
            rustc = scrapeRustcVersion(pathToExecutable(RUSTC)),
            cargo = scrapeCargoVersion(nonProjectCargo()),
            rustup = rustup
        )
    }

    fun cargo(cargoProjectDirectory: String): Cargo =
        Cargo(pathToExecutable(CARGO), pathToExecutable(RUSTC), cargoProjectDirectory)

    fun rustup(cargoProjectDirectory: String): Rustup? {
        if (!hasExecutable(RUSTUP)) return null

        return Rustup(pathToExecutable(RUSTUP), pathToExecutable(RUSTC), cargoProjectDirectory)
    }

    fun nonProjectCargo(): Cargo =
        Cargo(pathToExecutable(CARGO), pathToExecutable(RUSTC), null)

    val presentableLocation: String = PathUtil.toPresentableUrl(pathToExecutable(CARGO))

    private fun pathToExecutable(toolName: String): String {
        val exeName = if (SystemInfo.isWindows) "$toolName.exe" else toolName
        return File(File(location), exeName).absolutePath
    }

    private fun hasExecutable(exec: String): Boolean =
        File(pathToExecutable(exec)).canExecute()

    // TODO: drop IDEA 15 support and use SevVer.UNKNOWN
    data class VersionInfo(
        val rustc: RustcVersion?,
        val cargo: CargoVersion?,
        val rustup: SemVer?
    )

    companion object {
        private val RUSTC = "rustc"
        private val CARGO = "cargo"
        private val RUSTUP = "rustup"

        val CARGO_TOML = "Cargo.toml"

        val CARGO_LEAST_COMPATIBLE_VERSION = SemVer.parseFromText("0.9.0")!!

        fun suggest(): RustToolchain? = Suggestions.all().mapNotNull {
            val candidate = RustToolchain(it.absolutePath)
            if (candidate.looksLikeValidToolchain()) candidate else null
        }.firstOrNull()
    }
}

data class RustcVersion(
    val semver: SemVer,
    val nightlyCommitHash: String?
)

data class CargoVersion(
    val semver: SemVer,
    val hasMetadataCommand: Boolean
)

private fun findSemVer(lines: List<String>): SemVer? {
    val re = """\d+\.\d+\.\d+""".toRegex()
    val versionText = lines.mapNotNull { re.find(it) }.firstOrNull()?.value ?: return null
    return SemVer.parseFromText(versionText)
}

private fun scrapeRustcVersion(pathToRustc: String): RustcVersion? {
    val lines = GeneralCommandLine(pathToRustc)
        .withParameters("--version", "--verbose")
        .runExecutable()
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
    val commitHashRe = "commit-hash: (.*)".toRegex()
    val releaseRe = """release: (\d+\.\d+\.\d+)(.*)""".toRegex()
    val find = { re: Regex -> lines.mapNotNull { re.matchEntire(it) }.firstOrNull() }

    val commitHash = find(commitHashRe)?.let { it.groups[1]!!.value }
    val releaseMatch = find(releaseRe) ?: return null
    val versionText = releaseMatch.groups[1]?.value ?: return null

    val semVer = SemVer.parseFromText(versionText) ?: return null
    val isStable = releaseMatch.groups[2]?.value.isNullOrEmpty()
    return RustcVersion(semVer, if (isStable) null else commitHash)
}

private fun scrapeCargoVersion(cargo: Cargo): CargoVersion? {
    val lines = cargo.generalCommand("version").runExecutable() ?: return null
    val semver = findSemVer(lines) ?: return null
    val hasMetadataCommand = cargo.generalCommand("metadata", listOf("--help")).runExecutable() != null
    return CargoVersion(semver, hasMetadataCommand)
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
        .map { File(it) }
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

        val programFiles = File(System.getenv("ProgramFiles") ?: return emptySequence())
        if (!programFiles.exists() || !programFiles.isDirectory) return emptySequence()

        return programFiles.listFiles { file -> file.isDirectory }.asSequence()
            .filter { it.nameWithoutExtension.toLowerCase().startsWith("rust") }
            .map { File(it, "bin") }
    }
}

private fun GeneralCommandLine.runExecutable(): List<String>? {
    val procOut = try {
        CapturingProcessHandler(createProcess(), Charsets.UTF_8, commandLineString).runProcess(1.seconds)
    } catch (e: ExecutionException) {
        LOG.warn("Failed to run executable!", e)
        return null
    }

    if (procOut.exitCode != 0 || procOut.isCancelled || procOut.isTimeout)
        return null

    return procOut.stdoutLines
}
