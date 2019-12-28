/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.openapiext

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Disposer
import com.intellij.util.io.systemIndependentPath
import java.nio.file.Path

private val LOG = Logger.getInstance("org.rust.openapiext.CommandLineExt")

@Suppress("FunctionName")
fun GeneralCommandLine(path: Path, vararg args: String) = GeneralCommandLine(path.systemIndependentPath, *args)

fun GeneralCommandLine.withWorkDirectory(path: Path?) = withWorkDirectory(path?.systemIndependentPath)

private const val WINDOWS_PATH_REGEX = """^[A-Z]:.*$"""
const val WINDOWS_WSL_PATH_REGEX = """^\\\\wsl\$\\(\w+)(\\.+)$"""
const val WSL = "wsl"

fun GeneralCommandLine.adjustForWsl(): Boolean {
    val normalizedExePath = exePath.replace("/", "\\")
    WINDOWS_WSL_PATH_REGEX.toRegex().find(normalizedExePath)?.also {
        val distribution = it.groupValues[1]
        val unixPath = it.groupValues[2].replace("\\", "/")

        parametersList.parameters.forEachIndexed { index, parameter ->
            val isWindowsPath =
                WINDOWS_PATH_REGEX.toRegex().containsMatchIn(parameter)
            if (isWindowsPath) {
                val parameterEscaped = parameter.replace("\\", "\\\\")
                val transformedPathBytes = Runtime.getRuntime()
                    .exec("$WSL wslpath $parameterEscaped")
                    .inputStream
                    .readAllBytes()
                val transformedBytes = String(transformedPathBytes).trim()
                parametersList.set(index, transformedBytes)
            }
        }
        exePath = WSL
        parametersList.prependAll("-d", distribution, unixPath)
        return true
    }
    return false
}

const val UNIX_PATH_REGEX = """/[\w_\-/.]+"""

fun ProcessOutput.withUnixPathsTransformed(): ProcessOutput {
    val transformed = ProcessOutput(exitCode)
    if (isCancelled) transformed.setCancelled()
    if (isTimeout) transformed.setTimeout()

    val transform: (MatchResult) -> String = { path ->
        val bytes = Runtime.getRuntime().exec("$WSL wslpath -w ${path.value}").inputStream.readAllBytes()
        String(bytes).trim().replace("\\", "\\\\")
    }
    transformed.appendStdout(stdout.replace(UNIX_PATH_REGEX.toRegex(), transform))
    transformed.appendStderr(stderr.replace(UNIX_PATH_REGEX.toRegex(), transform))
    return transformed
}

fun GeneralCommandLine.execute(timeoutInMilliseconds: Int? = 1000): ProcessOutput? {
    val isAdjusted = adjustForWsl()
    val output = try {
        val handler = CapturingProcessHandler(this)
        LOG.info("Executing `$commandLineString`")
        handler.runProcessWithGlobalProgress(timeoutInMilliseconds)
    } catch (e: ExecutionException) {
        LOG.warn("Failed to run executable", e)
        return null
    }

    if (!output.isSuccess) {
        LOG.warn(errorMessage(this, output))
    }

    return if (isAdjusted) output.withUnixPathsTransformed() else output
}

@Throws(ExecutionException::class)
fun GeneralCommandLine.execute(
    owner: Disposable,
    ignoreExitCode: Boolean = true,
    stdIn: ByteArray? = null,
    listener: ProcessListener? = null
): ProcessOutput {
    val isAdjusted = adjustForWsl()
    val handler = CapturingProcessHandler(this)
    val cargoKiller = Disposable {
        // Don't attempt a graceful termination, Cargo can be SIGKILLed safely.
        // https://github.com/rust-lang/cargo/issues/3566
        handler.destroyProcess()
    }

    val alreadyDisposed = runReadAction {
        if (Disposer.isDisposed(owner)) {
            true
        } else {
            Disposer.register(owner, cargoKiller)
            false
        }
    }

    if (alreadyDisposed) {
        // On the one hand, this seems fishy,
        // on the other hand, this is isomorphic
        // to the scenario where cargoKiller triggers.
        if (ignoreExitCode) {
            return ProcessOutput().apply { setCancelled() }
        } else {
            throw ExecutionException("Command failed to start")
        }
    }

    listener?.let { handler.addProcessListener(it) }

    if (stdIn != null) {
        handler.processInput?.use { it.write(stdIn) }
    }

    val output = try {
        handler.runProcessWithGlobalProgress(null)
    } finally {
        Disposer.dispose(cargoKiller)
    }
    if (!ignoreExitCode && output.exitCode != 0) {
        throw ExecutionException(errorMessage(this, output))
    }
    return if (isAdjusted) output.withUnixPathsTransformed() else output
}

private fun errorMessage(commandLine: GeneralCommandLine, output: ProcessOutput): String = """
        Execution failed (exit code ${output.exitCode}).
        ${commandLine.commandLineString}
        stdout : ${output.stdout}
        stderr : ${output.stderr}
    """.trimIndent()

private fun CapturingProcessHandler.runProcessWithGlobalProgress(timeoutInMilliseconds: Int? = null): ProcessOutput {
    return runProcess(ProgressManager.getGlobalProgressIndicator(), timeoutInMilliseconds)
}

private fun CapturingProcessHandler.runProcess(
    indicator: ProgressIndicator?,
    timeoutInMilliseconds: Int? = null
): ProcessOutput {
    return when {
        indicator != null && timeoutInMilliseconds != null ->
            runProcessWithProgressIndicator(indicator, timeoutInMilliseconds)

        indicator != null -> runProcessWithProgressIndicator(indicator)
        timeoutInMilliseconds != null -> runProcess(timeoutInMilliseconds)
        else -> runProcess()
    }
}

val ProcessOutput.isSuccess: Boolean get() = !isTimeout && !isCancelled && exitCode == 0
