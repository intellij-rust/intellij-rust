/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain.tools

import com.intellij.execution.configurations.PtyCommandLine
import org.rust.cargo.toolchain.RsToolchainBase
import java.io.File

fun RsToolchainBase.evcxr(): Evcxr? = if (hasCargoExecutable(Evcxr.NAME)) Evcxr(this) else null

class Evcxr(toolchain: RsToolchainBase) : CargoBinary(NAME, toolchain) {

    fun createCommandLine(workingDirectory: File): PtyCommandLine {
        val commandLine = createBaseCommandLine(
            "--ide-mode",
            "--disable-readline",
            "--opt", "0",
            workingDirectory = workingDirectory.toPath()
        )

        return PtyCommandLine(commandLine).withInitialColumns(PtyCommandLine.MAX_COLUMNS)
    }

    companion object {
        const val NAME: String = "evcxr"
    }
}
