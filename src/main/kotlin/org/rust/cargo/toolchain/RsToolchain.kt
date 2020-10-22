/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain

import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PtyCommandLine
import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.io.exists
import com.intellij.util.net.HttpConfigurable
import com.intellij.util.text.SemVer
import org.rust.cargo.CargoConstants
import org.rust.cargo.toolchain.tools.Cargo
import org.rust.cargo.toolchain.tools.Rustc
import org.rust.openapiext.GeneralCommandLine
import org.rust.openapiext.withWorkDirectory
import org.rust.stdext.isExecutable
import org.rust.stdext.toPath
import java.nio.file.Path

interface RsToolchainProvider {
    fun isApplicable(homePath: String): Boolean
    fun getToolchain(homePath: String, toolchainName: String?): RsToolchain?

    companion object {
        private val EP_NAME: ExtensionPointName<RsToolchainProvider> =
            ExtensionPointName.create("org.rust.toolchainProvider")

        fun getToolchain(homePath: String, toolchainName: String?): RsToolchain? =
            EP_NAME.extensionList.find { it.isApplicable(homePath) }?.getToolchain(homePath, toolchainName)
    }
}

abstract class RsToolchain(val location: Path, val name: String?) {
    val presentableLocation: String = pathToExecutable(Cargo.NAME).toString()

    fun looksLikeValidToolchain(): Boolean = hasExecutable(Cargo.NAME) && hasExecutable(Rustc.NAME)

    /**
     * Patches passed command line to make it runnable in remote context.
     */
    abstract fun <T : GeneralCommandLine> patchCommandLine(commandLine: T): T

    abstract fun startProcess(commandLine: GeneralCommandLine): ProcessHandler

    abstract fun toLocalPath(remotePath: String): String

    abstract fun toRemotePath(localPath: String): String

    abstract fun expandUserHome(remotePath: String): String

    protected abstract fun getExecutableName(toolName: String): String

    // for executables from toolchain
    fun pathToExecutable(toolName: String): Path {
        val exeName = getExecutableName(toolName)
        return location.resolve(exeName).toAbsolutePath()
    }

    // for executables installed using `cargo install`
    fun pathToCargoExecutable(toolName: String): Path {
        // Binaries installed by `cargo install` (e.g. Grcov, Evcxr) are placed in ~/.cargo/bin by default:
        // https://doc.rust-lang.org/cargo/commands/cargo-install.html
        // But toolchain root may be different (e.g. on Arch Linux it is usually /usr/bin)
        val path = pathToExecutable(toolName)
        if (path.exists()) return path

        val exeName = getExecutableName(toolName)
        val cargoBinPath = expandUserHome("~/.cargo/bin").toPath()
        return cargoBinPath.resolve(exeName).toAbsolutePath()
    }

    fun hasExecutable(exec: String): Boolean = pathToExecutable(exec).isExecutable()

    fun hasCargoExecutable(exec: String): Boolean = pathToCargoExecutable(exec).isExecutable()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RsToolchain) return false

        if (location != other.location) return false

        return true
    }

    override fun hashCode(): Int {
        return location.hashCode()
    }

    override fun toString(): String {
        return "RsToolchain(location=$location)"
    }

    fun createGeneralCommandLine(
        executable: Path,
        workingDirectory: Path,
        backtraceMode: BacktraceMode,
        environmentVariables: EnvironmentVariablesData,
        parameters: List<String>,
        emulateTerminal: Boolean,
        http: HttpConfigurable = HttpConfigurable.getInstance()
    ): GeneralCommandLine {
        var commandLine = GeneralCommandLine(executable)
            .withWorkDirectory(workingDirectory)
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
        commandLine = patchCommandLine(commandLine)

        if (emulateTerminal) {
            if (!SystemInfo.isWindows) {
                commandLine.environment["TERM"] = "xterm-256color"
            }
            commandLine = PtyCommandLine(commandLine).withInitialColumns(PtyCommandLine.MAX_COLUMNS)
        }

        return commandLine
    }

    companion object {
        @JvmField
        val MIN_SUPPORTED_TOOLCHAIN: SemVer = SemVer.parseFromText("1.32.0")!!
    }
}
