/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain

import com.intellij.execution.configurations.GeneralCommandLine
import org.rust.cargo.CargoConstants.ProjectLayout
import org.rust.openapiext.GeneralCommandLine
import java.io.File
import java.nio.file.Path

class Grcov(private val grcovExecutable: Path) {
    fun createCommandLine(workingDirectory: File, coverageFilePath: Path): GeneralCommandLine =
        GeneralCommandLine(grcovExecutable)
            .withWorkDirectory(workingDirectory)
            .withParameters(
                ProjectLayout.target,
                "-t", "lcov",
                "--branch",
                "--ignore-not-existing",
                "-o", coverageFilePath.toString()
            )
            .withCharset(Charsets.UTF_8)
}
