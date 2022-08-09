/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.openapiext

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
import org.rust.stdext.RsResult.Err
import org.rust.stdext.RsResult.Ok
import org.rust.stdext.unwrapOrElse
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
    LOG.info("Executing `$commandLineString`")
    val handler = RsCapturingProcessHandler.startProcess(this).unwrapOrElse {
        LOG.warn("Failed to run executable", it)
        return null
    }
    val output = handler.runProcessWithGlobalProgress(timeoutInMilliseconds)

    if (!output.isSuccess) {
        LOG.warn(RsProcessExecutionException.errorMessage(commandLineString, output))
    }

    return output
}

fun GeneralCommandLine.execute(
    owner: Disposable,
    stdIn: ByteArray? = null,
    runner: CapturingProcessHandler.() -> ProcessOutput = { runProcessWithGlobalProgress(timeoutInMilliseconds = null) },
    listener: ProcessListener? = null
): RsProcessResult<ProcessOutput> {
    LOG.info("Executing `$commandLineString`")

    val handler = RsCapturingProcessHandler.startProcess(this) // The OS process is started here
        .unwrapOrElse {
            LOG.warn("Failed to run executable", it)
            return Err(RsProcessExecutionException.Start(commandLineString, it))
        }

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
        val output = ProcessOutput().apply { setCancelled() }
        return Err(RsProcessExecutionException.Canceled(commandLineString, output, "Command failed to start"))
    }

    listener?.let { handler.addProcessListener(it) }

    val output = try {
        if (stdIn != null) {
            handler.processInput.use { it.write(stdIn) }
        }

        handler.runner()
    } finally {
        Disposer.dispose(cargoKiller)
    }

    return when {
        output.isCancelled -> Err(RsProcessExecutionException.Canceled(commandLineString, output))
        output.isTimeout -> Err(RsProcessExecutionException.Timeout(commandLineString, output))
        output.exitCode != 0 -> Err(RsProcessExecutionException.ProcessAborted(commandLineString, output))
        else -> Ok(output)
    }
}

private fun CapturingProcessHandler.runProcessWithGlobalProgress(timeoutInMilliseconds: Int? = null): ProcessOutput {
    return runProcess(ProgressManager.getGlobalProgressIndicator(), timeoutInMilliseconds)
}

fun CapturingProcessHandler.runProcess(
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
