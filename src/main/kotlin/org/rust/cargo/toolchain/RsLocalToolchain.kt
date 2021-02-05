/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import org.rust.cargo.runconfig.RsProcessHandler
import org.rust.cargo.util.hasExecutable
import org.rust.cargo.util.pathToExecutable
import org.rust.stdext.isExecutable
import java.io.File
import java.nio.file.Path

class RsLocalToolchainProvider : RsToolchainProvider {

    override fun isApplicable(homePath: Path): Boolean =
        !homePath.toString().startsWith(WSLDistribution.UNC_PREFIX)

    override fun getToolchain(homePath: Path): RsToolchain = RsLocalToolchain(homePath)
}

open class RsLocalToolchain(location: Path) : RsToolchain(location) {
    override val fileSeparator: String get() = File.separator

    override fun <T : GeneralCommandLine> patchCommandLine(commandLine: T): T = commandLine

    override fun startProcess(commandLine: GeneralCommandLine): ProcessHandler = RsProcessHandler(commandLine)

    override fun toLocalPath(remotePath: String): String = remotePath

    override fun toRemotePath(localPath: String): String = localPath

    override fun expandUserHome(remotePath: String): String = FileUtil.expandUserHome(remotePath)

    override fun getExecutableName(toolName: String): String = if (SystemInfo.isWindows) "$toolName.exe" else toolName

    override fun pathToExecutable(toolName: String): Path = location.pathToExecutable(toolName)

    override fun hasExecutable(exec: String): Boolean = location.hasExecutable(exec)

    override fun hasCargoExecutable(exec: String): Boolean = pathToCargoExecutable(exec).isExecutable()
}
