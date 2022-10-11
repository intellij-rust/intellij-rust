/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain

import com.intellij.execution.ExecutionException
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PtyCommandLine
import com.intellij.execution.wsl.WslPath
import com.intellij.util.io.exists
import com.intellij.util.net.HttpConfigurable
import org.rust.cargo.CargoConstants
import org.rust.cargo.toolchain.flavors.RsToolchainFlavor
import org.rust.cargo.toolchain.tools.Cargo
import org.rust.cargo.toolchain.wsl.getHomePathCandidates
import org.rust.cargo.util.parseSemVer
import org.rust.openapiext.GeneralCommandLine
import org.rust.openapiext.withWorkDirectory
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

abstract class RsToolchainBase(val location: Path) {
    val presentableLocation: String get() = pathToExecutable(Cargo.NAME).toString()

    abstract val fileSeparator: String

    abstract val executionTimeoutInMilliseconds: Int

    fun looksLikeValidToolchain(): Boolean = RsToolchainFlavor.getFlavor(location) != null

    /**
     * Patches passed command line to make it runnable in remote context.
     */
    abstract fun patchCommandLine(commandLine: GeneralCommandLine): GeneralCommandLine

    abstract fun toLocalPath(remotePath: String): String

    abstract fun toRemotePath(localPath: String): String

    abstract fun expandUserHome(remotePath: String): String

    protected abstract fun getExecutableName(toolName: String): String

    // for executables from toolchain
    abstract fun pathToExecutable(toolName: String): Path

    // for executables installed using `cargo install`
    fun pathToCargoExecutable(toolName: String): Path {
        // Binaries installed by `cargo install` (e.g. Grcov, Evcxr) are placed in ~/.cargo/bin by default:
        // https://doc.rust-lang.org/cargo/commands/cargo-install.html
        // But toolchain root may be different (e.g. on Arch Linux it is usually /usr/bin)
        val exePath = pathToExecutable(toolName)
        if (exePath.exists()) return exePath
        val cargoBin = expandUserHome("~/.cargo/bin")
        val exeName = getExecutableName(toolName)
        return Paths.get(cargoBin, exeName)
    }

    abstract fun hasExecutable(exec: String): Boolean

    abstract fun hasCargoExecutable(exec: String): Boolean

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RsToolchainBase) return false

        if (location != other.location) return false

        return true
    }

    override fun hashCode(): Int = location.hashCode()

    fun createGeneralCommandLine(
        executable: Path,
        workingDirectory: Path,
        redirectInputFrom: File?,
        backtraceMode: BacktraceMode,
        environmentVariables: EnvironmentVariablesData,
        parameters: List<String>,
        emulateTerminal: Boolean,
        withSudo: Boolean,
        patchToRemote: Boolean = true,
        http: HttpConfigurable = HttpConfigurable.getInstance()
    ): GeneralCommandLine {
        var commandLine = GeneralCommandLine(executable, withSudo)
            .withWorkDirectory(workingDirectory)
            .withInput(redirectInputFrom)
            .withEnvironment("TERM", "ansi")
            .withParameters(parameters)
            .withCharset(Charsets.UTF_8)
            .withRedirectErrorStream(true)
        withProxyIfNeeded(commandLine, http)

        when (backtraceMode) {
            BacktraceMode.SHORT -> commandLine.withEnvironment(CargoConstants.RUST_BACKTRACE_ENV_VAR, "short")
            BacktraceMode.FULL -> commandLine.withEnvironment(CargoConstants.RUST_BACKTRACE_ENV_VAR, "full")
            BacktraceMode.NO -> Unit
        }

        environmentVariables.configureCommandLine(commandLine, true)

        if (emulateTerminal) {
            commandLine = PtyCommandLine(commandLine)
                .withInitialColumns(PtyCommandLine.MAX_COLUMNS)
                .withConsoleMode(false)
        }

        if (patchToRemote) {
            commandLine = patchCommandLine(commandLine)
        }

        return commandLine
    }

    companion object {
        val MIN_SUPPORTED_TOOLCHAIN = "1.41.0".parseSemVer()

        /** Environment variable to unlock unstable features of rustc and cargo.
         *  It doesn't change real toolchain.
         *
         * @see <a href="https://github.com/rust-lang/cargo/blob/06ddf3557796038fd87743bd3b6530676e12e719/src/cargo/core/features.rs#L447">features.rs</a>
         */
        const val RUSTC_BOOTSTRAP: String = "RUSTC_BOOTSTRAP"
        const val RUSTC_WRAPPER: String = "RUSTC_WRAPPER"

        @JvmOverloads
        fun suggest(projectDir: Path? = null): RsToolchainBase? {
            val distribution = projectDir?.let { WslPath.getDistributionByWindowsUncPath(it.toString()) }
            val toolchain = distribution
                ?.getHomePathCandidates()
                ?.filter { RsToolchainFlavor.getFlavor(it) != null }
                ?.mapNotNull { RsToolchainProvider.getToolchain(it.toAbsolutePath()) }
                ?.firstOrNull()
            if (toolchain != null) return toolchain

            if (projectDir?.let { isBazelProject(it) } == true) {
                return RsToolchainFlavor.getApplicableFlavors()
                    .asSequence()
                    .flatMap { it.suggestBazelPaths(projectDir) }
                    .mapNotNull { RsToolchainProvider.getToolchain(it.toAbsolutePath()) }
                    .firstOrNull()
            }

            return RsToolchainFlavor.getApplicableFlavors()
                .asSequence()
                .flatMap { it.suggestHomePaths() }
                .mapNotNull { RsToolchainProvider.getToolchain(it.toAbsolutePath()) }
                .firstOrNull()
        }

        private fun isBazelProject(projectDir: Path): Boolean {
            return projectDir.fileName.toString() in listOf(".ijwb", ".clwb")
        }

        fun findToolchainInBazelProject(projectRoot: File): Path? {
            var file = projectRoot
            while (!file.resolve("bazel-bin").exists()) {
                file = file.parentFile ?: return null
            }
            return file.resolve("bazel-bin/external").listFiles()
                ?.findBazelRustToolchainCandidates()
                ?.firstOrNull()
                ?.listFiles()
                ?.get(0)
                ?.resolve("bin")
                ?.toPath()
        }

        fun Array<File>.findBazelRustToolchainCandidates(): Collection<File> {
            val prefix = when (OS.currentOS) {
                OS.WINDOWS -> "rust_windows"
                OS.LINUX -> "rust_linux"
                OS.MAC -> "rust_darwin"
            }
            return filter { it.name.startsWith(prefix) && !it.name.endsWith("toolchains") }
        }
    }

    enum class OS {
        WINDOWS,
        LINUX,
        MAC;

        companion object {
            val currentOS: OS
            get() {
                val osName = System.getProperty("os.name").lowercase(Locale.ENGLISH)
                return when {
                    "mac" in osName || "darwin" in osName -> MAC
                    "win" in osName -> WINDOWS
                    else -> LINUX
                }
            }
        }
    }
}
