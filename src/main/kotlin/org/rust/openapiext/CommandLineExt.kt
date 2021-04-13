/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.openapiext

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ElevationService
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Disposer
import com.intellij.util.io.systemIndependentPath
import org.rust.cargo.runconfig.RsCapturingProcessHandler
import java.nio.file.Path

private val LOG: Logger = Logger.getInstance("org.rust.openapiext.CommandLineExt")

@Suppress("FunctionName", "UnstableApiUsage")
fun GeneralCommandLine(path: Path, withSudo: Boolean = false, vararg args: String) =
    object : GeneralCommandLine(path.systemIndependentPath, *args) {
        override fun createProcess(): Process = if (withSudo) {
            ElevationService.getInstance().createProcess(this)
        } else {
            super.createProcess()
        }
    }

fun GeneralCommandLine.withWorkDirectory(path: Path?) = withWorkDirectory(path?.systemIndependentPath)

fun GeneralCommandLine.execute(timeoutInMilliseconds: Int?): ProcessOutput? {
    val output = try {
        val handler = RsCapturingProcessHandler(this)
        LOG.info("Executing `$commandLineString`")
        handler.runProcessWithGlobalProgress(timeoutInMilliseconds)
    } catch (e: ExecutionException) {
        LOG.warn("Failed to run executable", e)
        return null
    }

    if (!output.isSuccess) {
        LOG.warn(errorMessage(this, output))
    }

    return output
}

@Throws(ExecutionException::class)
fun GeneralCommandLine.execute(
    owner: Disposable,
    ignoreExitCode: Boolean = true,
    stdIn: ByteArray? = null,
    listener: ProcessListener? = null
): ProcessOutput {

    val handler = RsCapturingProcessHandler(this) // The OS process is started here

    val cargoKiller = Disposable {
        // Don't attempt a graceful termination, Cargo can be SIGKILLed safely.
        // https://github.com/rust-lang/cargo/issues/3566
        if (!handler.isProcessTerminated) {
            handler.process.destroyForcibly() // Send SIGKILL
            handler.destroyProcess()
        }
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
        Disposer.dispose(cargoKiller) // Kill the process

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
        handler.processInput.use { it.write(stdIn) }
    }

    val output = try {
        handler.runProcessWithGlobalProgress(null)
    } finally {
        Disposer.dispose(cargoKiller)
    }
    if (!ignoreExitCode && output.exitCode != 0) {
        throw ExecutionException(errorMessage(this, output))
    }
    return output
}

private fun errorMessage(commandLine: GeneralCommandLine, output: ProcessOutput): String = """
        |Execution failed (exit code ${output.exitCode}).
        |${commandLine.commandLineString}
        |stdout : ${output.stdout}
        |stderr : ${output.stderr}
    """.trimMargin()

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
