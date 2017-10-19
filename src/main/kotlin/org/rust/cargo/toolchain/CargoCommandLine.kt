/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain

import com.intellij.execution.configuration.EnvironmentVariablesData
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

    fun forProject(project: CargoProject): CargoCommandLine {
        return copy(additionalArguments = listOf("--manifest-path", project.manifest.toString()) + additionalArguments)
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
