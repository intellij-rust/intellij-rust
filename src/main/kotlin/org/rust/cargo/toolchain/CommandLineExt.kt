/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.diagnostic.Logger

private val LOG = Logger.getInstance("org.rust.cargo.toolchain.CommandLineExt")

fun GeneralCommandLine.exec(timeoutInMilliseconds: Int? = null): ProcessOutput {
    val handler = CapturingProcessHandler(this)

    LOG.info("Executing `$commandLineString`")
    val output = if (timeoutInMilliseconds != null)
        handler.runProcess(timeoutInMilliseconds)
    else
        handler.runProcess()

    if (output.exitCode != 0) {
        LOG.warn("Failed to execute `$commandLineString`" +
            "\ncode  : ${output.exitCode}" +
            "\nstdout:\n${output.stdout}" +
            "\nstderr:\n${output.stderr}")
    }

    return output
}

val ProcessOutput.isSuccess: Boolean get() = !isTimeout && !isCancelled && exitCode == 0
