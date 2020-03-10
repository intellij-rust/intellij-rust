/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain

import com.intellij.execution.configurations.PtyCommandLine
import com.intellij.util.io.systemIndependentPath
import java.io.File
import java.nio.file.Path

class Evcxr(private val evcxrExecutable: Path) {
    fun createCommandLine(workingDirectory: File): PtyCommandLine =
        PtyCommandLine()
            .withInitialColumns(PtyCommandLine.MAX_COLUMNS)
            .withExePath(evcxrExecutable.systemIndependentPath)
            .withParameters(
                "--ide-mode",
                "--disable-readline",
                "--opt", "0"
            )
            .withWorkDirectory(workingDirectory)
            .withCharset(Charsets.UTF_8) as PtyCommandLine
}
