package org.rust.cargo.toolchain

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import org.rust.cargo.commands.Cargo
import java.io.File

data class RustToolchain(val location: String) {

    fun looksLikeValidToolchain(): Boolean =
        File(pathToExecutable(CARGO)).canExecute()

    fun containsMetadataCommand(): Boolean =
        queryCargoVersion()?.let { it >= RustToolchain.CARGO_LEAST_COMPATIBLE_VERSION } ?: false

    fun queryCargoVersion(): Version? {
        val cmd = GeneralCommandLine()
            .withExePath(pathToExecutable(CARGO))
            .withParameters("--version")

        val procOut = try {
            CapturingProcessHandler(cmd.createProcess(), Charsets.UTF_8, cmd.commandLineString).runProcess(10 * 1000)
        } catch (e: ExecutionException) {
            log.warn("Failed to detect `rustc` version!", e)
            return null
        }

        if (procOut.exitCode != 0 || procOut.isCancelled || procOut.isTimeout) {
            return null
        }

        return parseCargoVersion(procOut.stdoutLines)
    }

    fun queryRustcVersion(): Version? {
        check(!ApplicationManager.getApplication().isDispatchThread)

        if (!looksLikeValidToolchain()) return null

        val cmd = GeneralCommandLine()
            .withExePath(pathToExecutable(RUSTC))
            .withParameters("--version", "--verbose")

        val procOut = try {
            CapturingProcessHandler(cmd.createProcess(), Charsets.UTF_8, cmd.commandLineString).runProcess(10 * 1000)
        } catch (e: ExecutionException) {
            log.warn("Failed to detect `rustc` version!", e)
            return null
        }

        if (procOut.exitCode != 0 || procOut.isCancelled || procOut.isTimeout) {
            return null
        }

        return parseRustcVersion(procOut.stdoutLines)
    }

    fun cargo(cargoProjectDirectory: String): Cargo =
        Cargo(pathToExecutable(CARGO), cargoProjectDirectory)

    private fun pathToExecutable(toolName: String): String {
        val exeName = if (SystemInfo.isWindows) "$toolName.exe" else toolName
        return File(File(location), exeName).absolutePath
    }

    companion object {

        private val log = Logger.getInstance(UnstableVersion::class.java)

        private val RUSTC = "rustc"
        private val CARGO = "cargo"

        val CARGO_TOML = "Cargo.toml"

        val CARGO_LEAST_COMPATIBLE_VERSION = Version(0, 9, 0)
    }
}

class UnstableVersion(
    val commitHash: String,
    major: Int,
    minor: Int,
    build: Int
) : Version(major, minor, build)

open class Version(val major: Int, val minor: Int, val build: Int) : Comparable<Version> {
    val release: String = "$major.$minor.$build"

    /**
     * NOTA BENE: It only compares the release (!) part of the version
     */
    override fun compareTo(other: Version): Int {
        if (major != other.major)
            return major - other.major

        if (minor != other.minor)
            return minor - other.minor

        return build - other.build
    }

    override fun toString(): String = release
}

private fun parseCargoVersion(lines: List<String>): Version? {
    // We want to parse following
    //
    //  ```
    //  cargo 0.9.0-nightly (c4c6f39 2016-01-30)
    //  ```
    val releaseRe = """cargo (\d+)\.(\d+)\.(\d+)-(stable|nightly \(([a-zA-Z0-9]+) .*\))""".toRegex()

    val match = lines.mapNotNull { releaseRe.matchEntire(it) }.firstOrNull() ?: return null

    val major = match.groups[1]!!.value.toInt()
    val minor = match.groups[2]!!.value.toInt()
    val build = match.groups[3]!!.value.toInt()

    val isStable = match.groups[4]!!.value.isEmpty()

    return if (isStable) {
        Version(major, minor, build)
    } else {
        val commitHash = match.groups[5]!!.value
        UnstableVersion(commitHash, major, minor, build)
    }
}


private fun parseRustcVersion(lines: List<String>): Version? {
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
    val releaseRe = """release: (\d+)\.(\d+)\.(\d+)(.*)""".toRegex()
    val find = { re: Regex -> lines.mapNotNull { re.matchEntire(it) }.firstOrNull() }

    val commitHash = find(commitHashRe)?.let { it.groups[1]!!.value } ?: return null

    val releaseMatch = find(releaseRe) ?: return null

    val major = releaseMatch.groups[1]!!.value.toInt()
    val minor = releaseMatch.groups[2]!!.value.toInt()
    val build = releaseMatch.groups[3]!!.value.toInt()

    val isStable = releaseMatch.groups[4]!!.value.isEmpty()

    return if (isStable)
        Version(major, minor, build)
    else
        UnstableVersion(commitHash, major, minor, build)
}

fun suggestToolchain(): RustToolchain? = Suggestions.all().mapNotNull {
    val candidate = RustToolchain(it.absolutePath)
    if (candidate.looksLikeValidToolchain()) candidate else null
}.firstOrNull()

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
