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

class Grcov(private val toolchain: RustToolchain) {
    fun createCommandLine(workingDirectory: File, coverageFilePath: Path): GeneralCommandLine =
        toolchain.createGeneralCommandLine(RustToolchain.GRCOV, ProjectLayout.target, "-t", "lcov", "--branch", "--ignore-not-existing", "-o", coverageFilePath.toString(), workingDirectory = workingDirectory.toPath())
}
