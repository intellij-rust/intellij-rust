/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain.binaries

import com.intellij.execution.configurations.GeneralCommandLine
import org.rust.cargo.toolchain.RsToolchain
import org.rust.cargo.util.splitOnDoubleDash
import org.rust.openapiext.GeneralCommandLine
import java.io.File
import java.nio.file.Path

fun RsToolchain.wasmPack(): WasmPack? {
    if (!hasCargoExecutable(WasmPack.NAME)) return null
    return WasmPack(pathToCargoExecutable(WasmPack.NAME))
}

class WasmPack(private val wasmPackExecutable: Path) {
    fun createCommandLine(workingDirectory: File, command: String, args: List<String>): GeneralCommandLine {
        val (pre, post) = splitOnDoubleDash(args)
            .let { (pre, post) -> pre.toMutableList() to post.toMutableList() }

        val buildableCommands = setOf("build", "test")
        val forceColorsOption = "--color=always"
        if (command in buildableCommands && forceColorsOption !in post) {
            post.add(forceColorsOption)
        }

        val allArgs = if (post.isEmpty()) pre else pre + "--" + post

        return GeneralCommandLine(wasmPackExecutable)
            .withWorkDirectory(workingDirectory)
            .withParameters(command, *allArgs.toTypedArray())
            .withRedirectErrorStream(true)
    }

    companion object {
        const val NAME: String = "wasm-pack"
    }
}
