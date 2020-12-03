/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain.tools

import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PtyCommandLine
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.net.HttpConfigurable
import org.rust.cargo.CargoConstants
import org.rust.cargo.toolchain.BacktraceMode
import org.rust.cargo.toolchain.RsToolchain
import org.rust.cargo.toolchain.withProxyIfNeeded
import org.rust.openapiext.GeneralCommandLine
import org.rust.openapiext.withWorkDirectory
import java.io.File
import java.nio.file.Path

abstract class RsTool(toolName: String, protected val toolchain: RsToolchain) {
    open val executable: Path = toolchain.pathToExecutable(toolName)

    protected fun createBaseCommandLine(
        vararg parameters: String,
        workingDirectory: Path? = null
    ): GeneralCommandLine = createBaseCommandLine(
        parameters.toList(),
        workingDirectory = workingDirectory
    )

    protected open fun createBaseCommandLine(
        parameters: List<String>,
        workingDirectory: Path? = null
    ): GeneralCommandLine = GeneralCommandLine(executable)
        .withWorkDirectory(workingDirectory)
        .withParameters(parameters)
        .withCharset(Charsets.UTF_8)

    companion object {
        fun createGeneralCommandLine(
            executable: Path,
            workingDirectory: Path,
            redirectInputFrom: File?,
            backtraceMode: BacktraceMode,
            environmentVariables: EnvironmentVariablesData,
            parameters: List<String>,
            emulateTerminal: Boolean,
            http: HttpConfigurable = HttpConfigurable.getInstance()
        ): GeneralCommandLine {
            var commandLine = GeneralCommandLine(executable)
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
                if (!SystemInfo.isWindows) {
                    commandLine.environment["TERM"] = "xterm-256color"
                }
                commandLine = PtyCommandLine(commandLine).withInitialColumns(PtyCommandLine.MAX_COLUMNS)
            }

            return commandLine
        }
    }
}

abstract class CargoBinary(binaryName: String, toolchain: RsToolchain) : RsTool(binaryName, toolchain) {
    override val executable: Path = toolchain.pathToCargoExecutable(binaryName)
}

abstract class RustupComponent(componentName: String, toolchain: RsToolchain) : RsTool(componentName, toolchain)
