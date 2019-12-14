/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.util.containers.map2Array
import org.intellij.lang.annotations.Language
import org.rust.openapiext.withWorkDirectory
import java.nio.file.Files
import java.nio.file.Path

class WslRustToolchain(location: Path) : RustToolchain(location) {
    private val distribution: String
    private val basePath: String

    init {
        @Language("RegExp") val toolchainPathRegex = """^\\\\wsl\$\\(\w+)\\(.+)$""".toRegex()
        val groups = toolchainPathRegex.find(location.toString())!!.groupValues
        distribution = groups[1]
        basePath = "/${groups[2].replace("\\", "/")}"
    }

    override fun getSysroot(projectDirectory: Path): String? {
        val original = super.getSysroot(projectDirectory)
        return executeBashCommand("wslpath -w $original")
    }

    override fun createGeneralCommandLine(tool: String, vararg arguments: String, workingDirectory: Path?, setup: GeneralCommandLine.() -> Unit): GeneralCommandLine {
        val transform: (String) -> String = {
            if (it.startsWith("C:"))
                windowsToLinuxPath(it.replace("\\", "\\\\"))
            else it
        }
        val generalCommandLine = GeneralCommandLine("wsl.exe")
            .withCharset(Charsets.UTF_8)
            .withWorkDirectory(workingDirectory)
            .withParameters("$basePath/$tool", *arguments.map2Array(transform))
        generalCommandLine.setup()
        return generalCommandLine
    }

    override fun hasExecutable(name: String) = Files.exists(location.resolve(name))

    override val isRustupAvailable: Boolean
        get() = hasExecutable(RUSTUP)

    override val presentableLocation: String
        get() = location.resolve(CARGO).toString()

    override fun processOutput(original: String): String {
        return transformWslPaths(original)
    }

    companion object {
        fun executeBashCommand(command: String): String {
            val process = Runtime.getRuntime().exec("""wsl $command""")
            val bytes = process.inputStream.readAllBytes()
            return String(bytes).trim()
        }

        fun wslPathToWindows(path: String): String {
            return executeBashCommand("wslpath -w $path")
        }

        fun windowsToLinuxPath(path: String): String {
            return executeBashCommand("wslpath $path")
        }

        fun transformWslPaths(wslOutput: String): String {
            return wslOutput.replace("""/mnt/[\w/ \d]+""".toRegex()) {
                wslPathToWindows(it.value).replace("\\", "\\\\")
            }
        }
    }
}
