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
import com.intellij.openapi.util.Disposer
import com.intellij.util.io.systemIndependentPath
import java.nio.file.Path

private val LOG = Logger.getInstance("org.rust.openapiext.CommandLineExt")

@Suppress("FunctionName")
fun GeneralCommandLine(path: Path, vararg args: String) = GeneralCommandLine(path.systemIndependentPath, *args)

fun GeneralCommandLine.withWorkDirectory(path: Path?) = withWorkDirectory(path?.systemIndependentPath)

fun GeneralCommandLine.execute(timeoutInMilliseconds: Int? = 1000): ProcessOutput? {
    val output = try {
        val handler = CapturingProcessHandler(this)

        LOG.info("Executing `$commandLineString`")
        if (timeoutInMilliseconds != null)
            handler.runProcess(timeoutInMilliseconds)
        else
            handler.runProcess()
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
    listener: ProcessListener? = null
): ProcessOutput {

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
    val output = try {
        handler.runProcess()
    } finally {
        Disposer.dispose(cargoKiller)
    }
    if (!ignoreExitCode && output.exitCode != 0) {
        throw ExecutionException(errorMessage(this, output))
    }
    return output
}

private fun errorMessage(commandLine: GeneralCommandLine, output: ProcessOutput): String = """
        Execution failed (exit code ${output.exitCode}).
        ${commandLine.commandLineString}
        stdout : ${output.stdout}
        stderr : ${output.stderr}
    """.trimIndent()

val ProcessOutput.isSuccess: Boolean get() = !isTimeout && !isCancelled && exitCode == 0
