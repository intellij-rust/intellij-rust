/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.util.io.systemIndependentPath
import org.rust.openapiext.execute
import java.nio.file.Path

val WSL_ROOT_REGEX = """\\\\wsl\$\\([^\\]+)\\""".toRegex()

private val WINDOWS_PATH_REGEX = """^[a-z]:\\(?:[^\\/:*?"<>|\r\n]+\\)*[^\\/:*?"<>|\r\n]*${'$'}""".toRegex(RegexOption.IGNORE_CASE)

class WslRustToolchain(location: Path) : RustToolchain(location) {
    private val distribution = WSL_ROOT_REGEX.find(location.root.toString())!!.groupValues[1]

    override fun createBaseCommandLine(path: Path, vararg arguments: String, workingDirectory: Path?): GeneralCommandLine {
        val translatedArguments = arguments.map { argument ->
            if (WINDOWS_PATH_REGEX.matches(argument)) {
                GeneralCommandLine("wsl", "wslpath", argument.replace("\\", "\\\\")).execute()?.stdout ?: argument
            } else argument
        }.toTypedArray()

        return super.createBaseCommandLine(path, *translatedArguments, workingDirectory = workingDirectory).apply {
            val path = "/" + location.root.relativize(path).systemIndependentPath
            exePath = "wsl"
            parametersList.addAt(0, "-d")
            parametersList.addAt(1, distribution)
            parametersList.addAt(2, path)
        }
    }

    override fun getSysroot(projectDirectory: Path): String? {
        val sysroot = super.getSysroot(projectDirectory) ?: return null
        return convertToWindowsPath(sysroot).trim()
    }

    private fun convertToWindowsPath(sourcePath: String): String {
        return GeneralCommandLine("wsl", "-d", distribution)
            .withParameters("wslpath", "-w", sourcePath)
            .execute()!!
            .stdout
            .trim()
    }
}

fun convertWslPathToWindowsPath(wslPath: String): String? =
    GeneralCommandLine("wsl", "wslpath", "-w", wslPath).execute()?.stdout?.trim()
