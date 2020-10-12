/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain.tools

import com.intellij.execution.configurations.PtyCommandLine
import org.rust.cargo.toolchain.RsToolchain
import org.rust.openapiext.GeneralCommandLine
import java.io.File
import java.nio.file.Path

fun RsToolchain.evcxr(): Evcxr? = if (hasCargoExecutable(Evcxr.NAME)) Evcxr(this) else null

class Evcxr(toolchain: RsToolchain) {
    private val executable: Path = toolchain.pathToCargoExecutable(NAME)

    fun createCommandLine(workingDirectory: File): PtyCommandLine {
        val commandLine = GeneralCommandLine(executable)
            .withParameters(
                "--ide-mode",
                "--disable-readline",
                "--opt", "0"
            )
            .withWorkDirectory(workingDirectory)
            .withCharset(Charsets.UTF_8)

        return PtyCommandLine(commandLine).withInitialColumns(PtyCommandLine.MAX_COLUMNS)
    }

    companion object {
        const val NAME: String = "evcxr"
    }
}
