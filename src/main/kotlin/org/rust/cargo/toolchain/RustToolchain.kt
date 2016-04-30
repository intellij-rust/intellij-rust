package org.rust.cargo.toolchain

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import org.rust.cargo.commands.Cargo
import org.rust.cargo.util.PlatformUtil
import java.io.File

data class RustToolchain(
    val location: String
) {

    fun looksLikeValidToolchain(): Boolean =
        File(pathToExecutable(CARGO)).canExecute()

    fun queryRustcVersion(): RustcVersion? {
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

        return parseVersion(procOut.stdoutLines)
    }

    fun cargo(cargoProjectDirectory: String): Cargo =
        Cargo(pathToExecutable(CARGO), cargoProjectDirectory)

    private fun pathToExecutable(fileName: String): String {
        return File(File(location), PlatformUtil.getCanonicalNativeExecutableName(fileName)).absolutePath
    }

    companion object {
        private val log = Logger.getInstance(RustcVersion::class.java)

        private val RUSTC = "rustc"
        private val CARGO = "cargo"

        val CARGO_TOML = "Cargo.toml"
    }
}

/* Parsed result of `rustc -vV` output, which looks like this
 *
 * ```
 *
 * rustc 1.8.0-beta.1 (facbfdd71 2016-03-02)
 * binary: rustc
 * commit-hash: facbfdd71cb6ed0aeaeb06b6b8428f333de4072b
 * commit-date: 2016-03-02
 * host: x86_64-unknown-linux-gnu
 * release: 1.8.0-beta.1
 * ```
 */
data class RustcVersion(
    val commitHash: String,
    val release: String,
    val isStable: Boolean
) {
    val sourcesArchiveUrl: String get() {
        // We download sources from github and not from rust-lang.org, because we want zip archives. rust-lang.org
        // hosts only .tar.gz.
        val tag = if (isStable) release else commitHash
        return "https://github.com/rust-lang/rust/archive/$tag.zip"
    }
}

private fun parseVersion(lines: List<String>): RustcVersion? {
    // We want to parse here
    // rustc 1.8.0-beta.1 (facbfdd71 2016-03-02)
    // binary: rustc
    // commit-hash: facbfdd71cb6ed0aeaeb06b6b8428f333de4072b
    // commit-date: 2016-03-02
    // host: x86_64-unknown-linux-gnu
    // release: 1.8.0-beta.1
    val commitHashRe = "commit-hash: (.*)".toRegex()
    val releaseRe = """release: (\d+\.\d+\.\d+)(.*)""".toRegex()
    val find = { re: Regex -> lines.mapNotNull { re.matchEntire(it) }.firstOrNull() }

    val commitHash = find(commitHashRe)?.let { it.groups[1]!!.value } ?: return null
    val releaseMatch = find(releaseRe) ?: return null
    val release = releaseMatch.groups[1]!!.value
    val isStable = releaseMatch.groups[2]!!.value.isEmpty()

    return RustcVersion(
        commitHash,
        release,
        isStable
    )
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
