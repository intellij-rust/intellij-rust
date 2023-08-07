/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.coverage

import com.intellij.execution.configurations.GeneralCommandLine
import org.rust.cargo.toolchain.RsToolchainBase
import org.rust.cargo.toolchain.tools.CargoBinary
import org.rust.ide.experiments.RsExperiments.SOURCE_BASED_COVERAGE
import org.rust.openapiext.isFeatureEnabled
import java.nio.file.Path

fun RsToolchainBase.grcov(): Grcov? = if (hasCargoExecutable(Grcov.NAME)) Grcov(this) else null

class Grcov(toolchain: RsToolchainBase) : CargoBinary(NAME, toolchain) {

    // Parameters are copied from here - https://github.com/mozilla/grcov#grcov-with-travis
    fun createCommandLine(workingDirectory: Path, coverageFilePath: Path): GeneralCommandLine {
        val parameters = mutableListOf(
            ".",
            "-s", ".",
            "-t", "lcov",
            "--branch",
            "--ignore-not-existing",
            "--ignore", "/*",
            "-o", coverageFilePath.toString()
        )
        if (isFeatureEnabled(SOURCE_BASED_COVERAGE)) {
            parameters += "--binary-path"
            parameters += "./target/debug/deps/"
        } else {
            parameters += "--llvm"
        }
        return createBaseCommandLine(parameters, workingDirectory = workingDirectory)
    }

    companion object {
        const val NAME: String = "grcov"
    }
}
