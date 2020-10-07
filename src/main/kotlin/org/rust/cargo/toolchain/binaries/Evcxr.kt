/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain.binaries

import com.intellij.execution.configurations.PtyCommandLine
import org.rust.openapiext.GeneralCommandLine
import java.io.File
import java.nio.file.Path

class Evcxr(private val evcxrExecutable: Path) {
    fun createCommandLine(workingDirectory: File): PtyCommandLine {
        val commandLine = GeneralCommandLine(evcxrExecutable)
            .withParameters(
                "--ide-mode",
                "--disable-readline",
                "--opt", "0"
            )
            .withWorkDirectory(workingDirectory)
            .withCharset(Charsets.UTF_8)

        return PtyCommandLine(commandLine).withInitialColumns(PtyCommandLine.MAX_COLUMNS)
    }
}
