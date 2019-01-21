/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.util.SystemInfo
import org.rust.RsTestBase
import java.nio.file.Paths

abstract class ToolCommandLineTestBase : RsTestBase() {

    protected val toolchain get() = RustToolchain(Paths.get("/usr/bin"))
    protected val wd = Paths.get("/my-crate")

    private val drive = Paths.get("/").toAbsolutePath().toString().toUnixSlashes()

    protected fun checkCommandLine(cmd: GeneralCommandLine, expected: String, expectedWin: String) {
        val cleaned = (if (SystemInfo.isWindows) expectedWin else expected).trimIndent()
        val actual = cmd.debug().trim()
        assertEquals(cleaned, actual)
    }

    private fun GeneralCommandLine.debug(): String {
        val env = environment.entries.sortedBy { it.key }

        var result = buildString {
            append("cmd: $commandLineString")
            append("\n")
            append("env: ${env.joinToString { (key, value) -> "$key=$value" }}")
        }

        if (SystemInfo.isWindows) {
            result = result.toUnixSlashes().replace(drive, "C:/")
        }

        return result
    }



    private fun String.toUnixSlashes(): String = replace("\\", "/")
}
