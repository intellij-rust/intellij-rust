/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain

import com.intellij.execution.configuration.EnvironmentVariablesData
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.runconfig.command.workingDirectory
import org.rust.lang.core.psi.ext.CargoContext
import java.nio.file.Path

data class CargoCommandLine(
    val command: String, // Can't be `enum` because of custom subcommands
    val workingDirectory: Path, // workingDirectory is important: it specifies the Cargo project for command line
    val additionalArguments: List<String> = emptyList(),
    val backtraceMode: BacktraceMode = BacktraceMode.DEFAULT,
    val channel: RustChannel = RustChannel.DEFAULT,
    val environmentVariables: EnvironmentVariablesData = EnvironmentVariablesData.DEFAULT,
    val nocapture: Boolean = true
) {

    companion object {
        fun forCargoContext(
            ctx: CargoContext,
            command: String,
            additionalArguments: List<String> = emptyList()
        ): CargoCommandLine {
            val targetSpec = when (ctx.target.kind) {
                CargoWorkspace.TargetKind.BIN -> listOf("--bin", ctx.target.name)
                CargoWorkspace.TargetKind.TEST -> listOf("--test", ctx.target.name)
                CargoWorkspace.TargetKind.EXAMPLE -> listOf("--example", ctx.target.name)
                CargoWorkspace.TargetKind.BENCH -> listOf("--bench", ctx.target.name)
                CargoWorkspace.TargetKind.LIB -> listOf("--lib")
                CargoWorkspace.TargetKind.UNKNOWN -> emptyList()
            }

            return CargoCommandLine(
                command,
                ctx.project.workingDirectory,
                listOf("--package", ctx.pkg.name) + targetSpec + additionalArguments
            )
        }
    }

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
}
