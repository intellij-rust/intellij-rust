/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.coverage

import com.intellij.execution.configurations.GeneralCommandLine
import org.rust.cargo.toolchain.RsToolchainBase
import org.rust.cargo.toolchain.tools.CargoBinary
import java.nio.file.Path

fun RsToolchainBase.grcov(): Grcov? = if (hasCargoExecutable(Grcov.NAME)) Grcov(this) else null

class Grcov(toolchain: RsToolchainBase) : CargoBinary(NAME, toolchain) {

    fun createCommandLine(workingDirectory: Path, coverageFilePath: Path): GeneralCommandLine {
        val parameters = mutableListOf(
            "./target/",
            "-t", "lcov",
            "--branch",
            "--ignore-not-existing",
            "-o", coverageFilePath.toString(),
            "-b", "./target/debug/"
        )
        return createBaseCommandLine(parameters, workingDirectory = workingDirectory)
    }

    companion object {
        const val NAME: String = "grcov"
    }
}
