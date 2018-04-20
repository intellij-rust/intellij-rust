/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.debugger.runconfig

import com.intellij.execution.filters.TextConsoleBuilder
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.xdebugger.XDebugSession
import com.jetbrains.cidr.execution.RunParameters
import com.jetbrains.cidr.execution.debugger.CidrLocalDebugProcess
import com.jetbrains.cidr.execution.debugger.backend.DebuggerCommandException
import com.jetbrains.cidr.execution.debugger.backend.gdb.GDBDriver
import com.jetbrains.cidr.execution.debugger.backend.lldb.LLDBDriver

class RsLocalDebugProcess(
    parameters: RunParameters,
    debugSession: XDebugSession,
    consoleBuilder: TextConsoleBuilder
) : CidrLocalDebugProcess(parameters, debugSession, consoleBuilder) {

    fun loadPrettyPrinters(sysroot: String) {
        val threadId = currentThreadId
        val frameIndex = currentFrameIndex
        postCommand { driver ->
            when (driver) {
                is LLDBDriver -> driver.loadPrettyPrinters(threadId, frameIndex, sysroot)
                is GDBDriver -> driver.loadPrettyPrinters(threadId, frameIndex, sysroot)
            }
        }
    }

    private fun LLDBDriver.loadPrettyPrinters(threadId: Long, frameIndex: Int, sysroot: String) {
        val path = "$sysroot/lib/rustlib/etc/lldb_rust_formatters.py".systemDependentAndEscaped()
        try {
            executeConsoleCommand(threadId, frameIndex, """command script import "$path"""")
            executeConsoleCommand(threadId, frameIndex, """type summary add --no-value --python-function lldb_rust_formatters.print_val -x ".*" --category Rust""")
            executeConsoleCommand(threadId, frameIndex, """type category enable Rust""")
        } catch (e: DebuggerCommandException) {
            printlnToConsole(e.message)
            LOG.warn(e)
        }
    }

    private fun GDBDriver.loadPrettyPrinters(threadId: Long, frameIndex: Int, sysroot: String) {
        val path = "$sysroot/lib/rustlib/etc".systemDependentAndEscaped()
        // Avoid multiline Python scripts due to https://youtrack.jetbrains.com/issue/CPP-9090
        val command = """python """ +
            """sys.path.insert(0, "$path"); """ +
            """import gdb_rust_pretty_printing; """ +
            """gdb_rust_pretty_printing.register_printers(gdb); """
        try {
            executeConsoleCommand(threadId, frameIndex, command)
        } catch (e: DebuggerCommandException) {
            printlnToConsole(e.message)
            LOG.warn(e)
        }
    }

    private fun String.systemDependentAndEscaped(): String =
        StringUtil.escapeStringCharacters(FileUtil.toSystemDependentName(this))

    companion object {
        private val LOG: Logger = Logger.getInstance(RsLocalDebugProcess::class.java)
    }
}
