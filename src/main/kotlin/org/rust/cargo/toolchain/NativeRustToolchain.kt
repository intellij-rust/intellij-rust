/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.util.SystemInfo
import org.rust.openapiext.GeneralCommandLine
import org.rust.openapiext.withWorkDirectory
import java.nio.file.Files
import java.nio.file.Path

data class NativeRustToolchain(override val location: Path) : RustToolchain(location) {
    override fun createGeneralCommandLine(tool: String, vararg arguments: String, workingDirectory: Path?, setup: GeneralCommandLine.() -> Unit): GeneralCommandLine {
        val generalCommandLine = GeneralCommandLine(pathToExecutable(tool))
            .withCharset(Charsets.UTF_8)
            .withWorkDirectory(workingDirectory)
            .withParameters(*arguments)
        generalCommandLine.setup()
        return generalCommandLine
    }

    override fun hasExecutable(name: String): Boolean =
        Files.isExecutable(pathToExecutable(name))

    private fun pathToExecutable(toolName: String): Path {
        val exeName = if (SystemInfo.isWindows) "$toolName.exe" else toolName
        return location.resolve(exeName).toAbsolutePath()
    }

    override val isRustupAvailable: Boolean
        get() = hasExecutable(RUSTUP)

    override val presentableLocation: String
        get() = pathToExecutable(CARGO).toString()
}
