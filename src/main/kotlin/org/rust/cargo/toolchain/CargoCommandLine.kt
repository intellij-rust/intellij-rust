/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain

import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.util.execution.ParametersListUtil
import org.rust.cargo.project.model.CargoProject
import java.nio.file.Path

data class CargoCommandLine(
    val command: String, // Can't be `enum` because of custom subcommands
    val additionalArguments: List<String> = emptyList(),
    val backtraceMode: BacktraceMode = BacktraceMode.DEFAULT,
    val channel: RustChannel = RustChannel.DEFAULT,
    val workingDirectory: Path? = null,
    val environmentVariables: EnvironmentVariablesData = EnvironmentVariablesData.DEFAULT,
    val nocapture: Boolean = true
) {
    companion object {
        fun forProject(
            project: CargoProject,
            command: String, // Can't be `enum` because of custom subcommands
            additionalArguments: List<String> = emptyList(),
            backtraceMode: BacktraceMode = BacktraceMode.DEFAULT,
            channel: RustChannel = RustChannel.DEFAULT,
            workingDirectory: Path? = null,
            environmentVariables: EnvironmentVariablesData = EnvironmentVariablesData.DEFAULT,
            nocapture: Boolean = true
        ): CargoCommandLine = CargoCommandLine(
            command,
            listOf("--manifest-path", project.manifest.toString()) + additionalArguments,
            backtraceMode, channel, workingDirectory, environmentVariables, nocapture
        )
    }

    /**
     * Returns the list of arguments after the "--". If there is no "--" returns an empty list.
     */
    fun getRunArguments() : List<String> {
        val idx = additionalArguments.indexOf("--")
        val cnt = if(idx >= 0) idx else additionalArguments.size-1
        return additionalArguments.takeLast(Math.max(0, additionalArguments.size - cnt - 1))
    }

    /**
     * Returns the arguments before any "--" argument, intended for the "cargo build" command.
     */
    fun getBuildArguments() : List<String> {
        val idx = additionalArguments.indexOf("--")
        val cnt = if(idx >= 0) idx else additionalArguments.size
        return additionalArguments.take(cnt)
    }
}
