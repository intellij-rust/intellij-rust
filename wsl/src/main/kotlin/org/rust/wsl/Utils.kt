package org.rust.wsl

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessRunner
import com.intellij.execution.process.ProcessOutput
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.WSLUtil
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.remote.RemoteSdkException
import com.intellij.util.PathMappingSettings
import com.intellij.wsl.WSLCredentialsHolder
import com.intellij.wsl.WSLCredentialsType
import org.rust.ide.sdk.remote.RsRemotePathMapper
import org.rust.ide.sdk.remote.RsRemoteSdkAdditionalDataBase
import org.rust.openapiext.runUnderProgress
import org.rust.remote.PathMappings
import org.rust.remote.patchRemoteCommandLineIfNeeded
import org.rust.stdext.Result
import java.io.File


val Sdk.distribution: Result<WSLDistribution, String>?
    get() = (sdkAdditionalData as? RsRemoteSdkAdditionalDataBase)?.getDistribution()

val Sdk.isWsl: Boolean
    get() = (sdkAdditionalData as? RsRemoteSdkAdditionalDataBase)?.isWsl == true

val RsRemoteSdkAdditionalDataBase.wslCredentials: WSLCredentialsHolder?
    get() = connectionCredentials().credentials as? WSLCredentialsHolder

fun RsRemoteSdkAdditionalDataBase.getDistribution(): Result<WSLDistribution, String> =
    wslCredentials?.distribution?.let { Result.Success<WSLDistribution, String>(it) }
        ?: Result.Failure("Unknown distribution ${wslCredentials?.distributionId}")

fun WSLDistribution.toRemotePath(localPath: String): String =
    localPath.split(File.pathSeparatorChar).joinToString(":") { getWslPath(it) ?: it }

val WSLDistribution.rootMappings: PathMappings
    get() = File.listRoots().map { PathMappingSettings.PathMapping(it.path, getWslPath(it.path)) }

val RsRemoteSdkAdditionalDataBase.isWsl: Boolean
    get() = remoteConnectionType == WSLCredentialsType.getInstance()

fun startWslProcess(
    project: Project?,
    commandLine: GeneralCommandLine,
    sdkAdditionalData: RsRemoteSdkAdditionalDataBase,
    sudo: Boolean,
    remoteWorkDir: String? = null,
    patchExe: Boolean = true
): RsWslProcessHandler = startWslProcessImpl(
    project,
    commandLine,
    sdkAdditionalData,
    sudo,
    remoteWorkDir,
    patchExe,
    closeStdin = !isWsl1(project, sdkAdditionalData)
)

fun isWsl1(project: Project?, sdkAdditionalData: RsRemoteSdkAdditionalDataBase) =
    getWslOutput(project, "Obtaining WSL version...", sdkAdditionalData, "uname -v")
        .successOrNull
        ?.contains("Microsoft")
        ?: true

data class Wsl2IPs(val winIp: String, val linIp: String)

fun getWsl2WindowsIp(project: Project?, sdkAdditionalData: RsRemoteSdkAdditionalDataBase): Result<Wsl2IPs, String> {
    val distribution = sdkAdditionalData.getDistribution().successOrNull
        ?: return Result.Failure("No distribution")
    val helper = distribution.getWslPath(RsHelpersLocator.getHelperPath("rust/_jb_wsl_get_ip.py"))
        ?: return Result.Failure("Can't find helper")
    return when (val output = getWslOutput(project, "Obtaining WSL IP...", sdkAdditionalData, "${sdkAdditionalData.interpreterPath} '${helper}'", true)) {
        is Result.Success -> output.result.split(":").let { Result.Success<Wsl2IPs, String>(Wsl2IPs(it[0].trim(), it[1].trim())) }
        is Result.Failure -> Result.Failure("Can't obtain ip: ${output.error.second}")
    }
}

private val ProcessOutput.result: Result<String, Pair<Int, String>>
    get() = when (exitCode) {
        0 -> Result.Success(stdout)
        else -> Result.Failure(Pair(exitCode, stderr))
    }

private fun getWslOutput(
    project: Project?,
    title: String,
    sdkAdditionalData: RsRemoteSdkAdditionalDataBase,
    command: String,
    patchExe: Boolean = false
): Result<String, Pair<Int, String>> =
    ProgressManager.getInstance().runUnderProgress(title) {
        val wslProcess = startWslProcessImpl(
            project = project,
            commandLine = GeneralCommandLine(command.split(" ")),
            sdkData = sdkAdditionalData,
            closeStdin = true,
            patchExe = patchExe,
            sudo = false
        )
        CapturingProcessRunner(wslProcess).runProcess().result
    }

private fun startWslProcessImpl(
    project: Project?,
    commandLine: GeneralCommandLine,
    sdkData: RsRemoteSdkAdditionalDataBase,
    sudo: Boolean,
    remoteWorkDir: String? = null,
    patchExe: Boolean = true,
    closeStdin: Boolean
): RsWslProcessHandler {
    val distribution = sdkData.getDistribution().let {
        when (it) {
            is Result.Success -> it.result
            is Result.Failure -> throw ExecutionException(it.error)
        }
    }
    val mapper = RsRemotePathMapper.fromSettings(sdkData.pathMappings, RsRemotePathMapper.RsPathMappingType.USER_DEFINED)
    ProgressManager.getInstance().runUnderProgress("Preparing command to run it on WSL...") {
        try {
            commandLine.patchRemoteCommandLineIfNeeded(sdkData, RsWslSocketProvider(project, sdkData), mapper)
        } catch (e: RemoteSdkException) {
            Logger.getInstance(RsWslProcessHandler::class.java).warn(e)
        }
    }

    if (patchExe) {
        commandLine.exePath = sdkData.interpreterPath
    }

    for (group in commandLine.parametersList.paramsGroups) {
        val params = ArrayList(group.parameters)
        group.parametersList.clearAll()
        group.parametersList.addAll(params.map { distribution.toRemotePath(it) })
    }

    commandLine.environment.forEach { (k, v) -> commandLine.environment[k] = distribution.toRemotePath(v) }
    commandLine.workDirectory?.let {
        if (it.path.startsWith("/")) {
            commandLine.workDirectory = File(distribution.getWindowsPath(it.path) ?: it.path)
        }
    }
    val effectiveRemoteWorkDir = remoteWorkDir
        ?: commandLine.workDirectory?.toString()?.let { distribution.toRemotePath(it) }

    distribution.patchCommandLine(commandLine, project, effectiveRemoteWorkDir, sudo)

    val processHandler = RsWslProcessHandler(commandLine, distribution, sdkData.pathMappings)
    if (closeStdin) {
        WSLUtil.addInputCloseListener(processHandler)
    }

    return distribution.patchProcessHandler(commandLine, processHandler)
}
