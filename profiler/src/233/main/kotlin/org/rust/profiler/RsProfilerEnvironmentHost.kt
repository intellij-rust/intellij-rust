/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.profiler

import com.intellij.execution.ExecutionException
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PtyCommandLine
import com.intellij.execution.process.*
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.profiler.clion.ProfilerEnvironmentHost
import com.intellij.xdebugger.attach.WslAttachHost
import org.rust.RsBundle
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.runconfig.RsCapturingProcessHandler
import org.rust.cargo.runconfig.RsProcessHandler
import org.rust.cargo.toolchain.BacktraceMode
import org.rust.cargo.toolchain.RsLocalToolchain
import org.rust.cargo.toolchain.wsl.RsWslToolchain
import org.rust.openapiext.execute
import org.rust.stdext.toPath
import org.rust.stdext.toPathOrNull
import java.nio.file.Path

class RsProfilerEnvironmentHost : ProfilerEnvironmentHost {

    override fun shouldCheckFilePathExist(project: Project): Boolean {
        val toolchain = project.toolchain ?: return false
        return toolchain is RsLocalToolchain
    }

    override fun getPath(path: String, project: Project): Path =
        project.toolchain?.toLocalPath(path)?.toPathOrNull() ?: Path.of(path) // TODO: return null

    override fun getEnvPath(path: String?, project: Project): String {
        if (path == null) return "" // TODO: return null
        return project.toolchain?.toRemotePath(path).toString()
    }

    override fun getTempDirectory(project: Project): Path = FileUtil.getTempDirectory().toPath()

    override fun isRemote(project: Project): Boolean {
        val toolchain = project.toolchain ?: return false
        return toolchain !is RsLocalToolchain
    }

    override fun isWSL(project: Project): Boolean = project.toolchain is RsWslToolchain

    override fun getWSLVersion(project: Project): Int {
        val toolchain = project.toolchain as? RsWslToolchain ?: return -1
        return toolchain.wslPath.distribution.version
    }

    override fun createProcessHandler(
        commandLine: GeneralCommandLine,
        project: Project,
        colored: Boolean?,
        usePty: Boolean?,
        captureProcessOutput: Boolean?,
        splitLines: Boolean?, // TODO: take it into account
        withElevated: Boolean?
    ): BaseProcessHandler<*> {
        var tmpCommandLine = commandLine

        if (usePty == true) {
            tmpCommandLine = PtyCommandLine(commandLine)
                .withInitialColumns(PtyCommandLine.MAX_COLUMNS)
                .withConsoleMode(false)
        }

        val toolchain = project.toolchain as? RsWslToolchain
            ?: throw ExecutionException(RsBundle.message("dialog.message.current.rust.toolchain.not.on.wsl"))
        tmpCommandLine = toolchain.patchCommandLine(tmpCommandLine, withElevated ?: false)

        @Suppress("UnstableApiUsage")
        return when {
            withElevated == true -> ElevationService.getInstance().createProcessHandler(tmpCommandLine)
            colored == true -> RsProcessHandler(tmpCommandLine, colored)
            captureProcessOutput == true -> RsCapturingProcessHandler.startProcess(tmpCommandLine).unwrap()
            else -> RsProcessHandler(tmpCommandLine, false)
        }
    }

    override fun runProcess(
        handler: ProcessHandler,
        indicator: ProgressIndicator,
        timeout: Int,
        project: Project
    ): ProcessOutput = CapturingProcessRunner(handler).runProcess(indicator, timeout)

    override fun getProcessList(project: Project): List<ProcessInfo> =
        when (val toolchain = project.toolchain) {
            is RsLocalToolchain -> OSProcessUtil.getProcessList().toList()
            is RsWslToolchain -> WslAttachHost(toolchain.wslPath.distribution).processList
            else -> emptyList()
        }

    override fun sendSignal(pid: Int, signalName: String, project: Project): Int =
        when (val toolchain = project.toolchain) {
            is RsLocalToolchain -> {
                if (SystemInfo.isWindows) {
                    throw UnsupportedOperationException("Not supported for Windows OS, use winbreak instead")
                }
                UnixProcessManager.sendSignal(pid, signalName)
            }

            is RsWslToolchain -> {
                toolchain.createGeneralCommandLine(
                    Path.of("kill"),
                    Path.of("."),
                    null,
                    BacktraceMode.NO,
                    EnvironmentVariablesData.DEFAULT,
                    listOf("-s", signalName, pid.toString()),
                    emulateTerminal = false,
                    withSudo = false,
                    patchToRemote = true // ???
                ).execute(5000)?.exitCode ?: -1
            }

            else -> -1
        }
}
