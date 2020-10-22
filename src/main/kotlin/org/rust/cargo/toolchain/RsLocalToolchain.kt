/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import org.rust.cargo.runconfig.RsKillableColoredProcessHandler
import org.rust.ide.sdk.remote.RsRemoteSdkUtils.isCustomSdkHomePath
import org.rust.stdext.toPath
import java.io.File
import java.nio.file.Path

object RsLocalToolchainProvider : RsToolchainProvider {
    override fun isApplicable(homePath: String): Boolean = !isCustomSdkHomePath(homePath)

    override fun getToolchain(homePath: String, toolchainName: String?): RsToolchain =
        RsLocalToolchain(homePath.toPath(), toolchainName)
}

open class RsLocalToolchain(location: Path, name: String?) : RsToolchain(location, name) {
    override val fileSeparator: String get() = File.separator

    override fun <T : GeneralCommandLine> patchCommandLine(commandLine: T): T = commandLine

    override fun startProcess(commandLine: GeneralCommandLine): ProcessHandler {
        return RsKillableColoredProcessHandler(commandLine)
    }

    override fun toLocalPath(remotePath: String): String = remotePath

    override fun toRemotePath(localPath: String): String = localPath

    override fun expandUserHome(remotePath: String): String = FileUtil.expandUserHome(remotePath)

    override fun getExecutableName(toolName: String): String =
        if (SystemInfo.isWindows) "$toolName.exe" else toolName
}
