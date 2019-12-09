/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain

import com.intellij.execution.configurations.GeneralCommandLine
import org.rust.openapiext.withWorkDirectory
import java.nio.file.Files
import java.nio.file.Path

class WslRustToolchain(location: Path) : RustToolchain(location) {
    private val distribution: String
    private val basePath: String

    init {
        val toolchainPathRegex = """^\\\\wsl\$\\(\w+)\\(.+)$""".toRegex()
        val groups = toolchainPathRegex.find(location.toString())!!.groupValues
        distribution = groups[1]
        basePath = "/${groups[2].replace("\\", "/")}"
    }

    override fun getSysroot(projectDirectory: Path): String? {
        val original = super.getSysroot(projectDirectory)
        val bytes = Runtime.getRuntime().exec("""bash.exe -c "wslpath -w $original"""")
            .inputStream.readAllBytes()
        return String(bytes).trim()
    }

    override fun createGeneralCommandLine(tool: String, vararg arguments: String, workingDirectory: Path?, setup: GeneralCommandLine.() -> Unit): GeneralCommandLine {
        val generalCommandLine = GeneralCommandLine("wsl.exe")
            .withCharset(Charsets.UTF_8)
            .withWorkDirectory(workingDirectory)
            .withParameters("$basePath/$tool", *arguments)
        generalCommandLine.setup()
        return generalCommandLine
    }

    override fun hasExecutable(name: String) = Files.exists(location.resolve(name))

    override val isRustupAvailable: Boolean
        get() = hasExecutable(RUSTUP)

    override val presentableLocation: String
        get() = location.resolve(CARGO).toString()
}
