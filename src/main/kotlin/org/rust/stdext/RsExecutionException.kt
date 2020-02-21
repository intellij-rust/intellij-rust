/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.stdext

import com.intellij.execution.ExecutionException
import com.intellij.execution.process.ProcessOutput

class RsExecutionException(
    message: String,
    val command: String,
    val args: List<String> = emptyList(),
    val stdout: String = "",
    val stderr: String = "",
    val exitCode: Int = 0
) : ExecutionException(message) {

    constructor(
        message: String,
        command: String,
        args: List<String>,
        output: ProcessOutput
    ) : this(
        message,
        command,
        args,
        output.stdout,
        output.stderr,
        output.exitCode
    )

    override fun toString(): String = buildString {
        appendln("The following command was executed:\n")
        append(command)
        if (args.isNotEmpty()) append(" ")
        args.joinTo(this, " ")
        append("\n\n")
        appendln("The exit code: $exitCode")
        appendln("The error output of the command:\n")
        appendln(stdout)
        appendln(stderr)
        append(message)
    }
}
