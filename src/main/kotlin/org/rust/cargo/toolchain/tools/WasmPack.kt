/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain.tools

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PtyCommandLine
import org.rust.cargo.toolchain.RsToolchainBase
import org.rust.cargo.util.splitOnDoubleDash
import java.io.File

fun RsToolchainBase.wasmPack(): WasmPack? = if (hasCargoExecutable(WasmPack.NAME)) WasmPack(this) else null

class WasmPack(toolchain: RsToolchainBase) : CargoBinary(NAME, toolchain) {

    fun createCommandLine(
        workingDirectory: File,
        command: String,
        args: List<String>,
        emulateTerminal: Boolean
    ): GeneralCommandLine {
        val (pre, post) = splitOnDoubleDash(args)
            .let { (pre, post) -> pre.toMutableList() to post.toMutableList() }

        pre.add(0, command)

        val buildableCommands = setOf("build", "test")
        val forceColorsOption = "--color=always"
        if (command in buildableCommands && forceColorsOption !in post) {
            post.add(forceColorsOption)
        }

        val allArgs = if (post.isEmpty()) pre else pre + "--" + post

        var commandLine = createBaseCommandLine(allArgs, workingDirectory.toPath())
            .withRedirectErrorStream(true)

        if (emulateTerminal) {
            commandLine = PtyCommandLine(commandLine)
                .withInitialColumns(PtyCommandLine.MAX_COLUMNS)
                .withConsoleMode(false)
        }

        return commandLine
    }

    companion object {
        const val NAME: String = "wasm-pack"
    }
}
