/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain

import com.intellij.execution.configuration.EnvironmentVariablesData
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.runconfig.command.workingDirectory
import java.nio.file.Path

data class CargoCommandLine(
    val command: String, // Can't be `enum` because of custom subcommands
    val workingDirectory: Path, // Note that working directory selects Cargo project as well
    val additionalArguments: List<String> = emptyList(),
    val backtraceMode: BacktraceMode = BacktraceMode.DEFAULT,
    val channel: RustChannel = RustChannel.DEFAULT,
    val environmentVariables: EnvironmentVariablesData = EnvironmentVariablesData.DEFAULT,
    val nocapture: Boolean = true
) {


    fun withDoubleDashFlag(arg: String): CargoCommandLine {
        val (pre, post) = splitOnDoubleDash()
        if (arg in post) return this
        return copy(additionalArguments = pre + "--" + arg + post)
    }

    /**
     * Splits [additionalArguments] into parts before and after `--`.
     * For `cargo run --release -- foo bar`, returns (["--release"], ["foo", "bar"])
     */
    fun splitOnDoubleDash(): Pair<List<String>, List<String>> {
        val idx = additionalArguments.indexOf("--")
        if (idx == -1) return additionalArguments to emptyList()
        return additionalArguments.take(idx) to additionalArguments.drop(idx + 1)
    }

    companion object {
        fun forTarget(
            target: CargoWorkspace.Target,
            command: String,
            additionalArguments: List<String> = emptyList()
        ): CargoCommandLine {
            val targetArgs = when (target.kind) {
                CargoWorkspace.TargetKind.BIN -> listOf("--bin", target.name)
                CargoWorkspace.TargetKind.TEST -> listOf("--test", target.name)
                CargoWorkspace.TargetKind.EXAMPLE -> listOf("--example", target.name)
                CargoWorkspace.TargetKind.BENCH -> listOf("--bench", target.name)
                CargoWorkspace.TargetKind.LIB -> listOf("--lib")
                CargoWorkspace.TargetKind.UNKNOWN -> emptyList()
            }

            return CargoCommandLine(
                command,
                workingDirectory = target.pkg.workspace.manifestPath.parent,
                additionalArguments = listOf("--package", target.pkg.name) + targetArgs + additionalArguments
            )
        }

        fun forProject(
            cargoProject: CargoProject,
            command: String,
            additionalArguments: List<String> = emptyList(),
            channel: RustChannel = RustChannel.DEFAULT
        ): CargoCommandLine {
            return CargoCommandLine(
                command,
                cargoProject.workingDirectory,
                additionalArguments,
                channel = channel
            )
        }
    }
}
