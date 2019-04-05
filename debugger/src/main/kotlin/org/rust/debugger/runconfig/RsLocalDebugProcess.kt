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
import org.rust.debugger.*
import java.nio.file.InvalidPathException

class RsLocalDebugProcess(
    parameters: RunParameters,
    debugSession: XDebugSession,
    consoleBuilder: TextConsoleBuilder
) : CidrLocalDebugProcess(parameters, debugSession, consoleBuilder) {

    fun loadPrettyPrinters(sysroot: String?, lldbRenderers: LLDBRenderers, gdbRenderers: GDBRenderers) {
        postCommand { driver ->
            when (driver) {
                is LLDBDriver -> driver.loadPrettyPrinters(currentThreadId, currentFrameIndex, sysroot, lldbRenderers)
                is GDBDriver -> driver.loadPrettyPrinters(currentThreadId, currentFrameIndex, sysroot, gdbRenderers)
            }
        }
    }

    fun loadRustcSources(sysroot: String, commitHash: String) {
        postCommand { driver ->
            val sourceMapCommand = when (driver) {
                is LLDBDriver -> "settings set target.source-map"
                is GDBDriver -> "set substitute-path"
                else -> return@postCommand
            }
            val rustcHash = "/rustc/$commitHash/".systemDependentAndEscaped()
            val rustcSources = "$sysroot/lib/rustlib/src/rust/".systemDependentAndEscaped()
            val fullCommand = """$sourceMapCommand "$rustcHash" "$rustcSources" """
            driver.executeConsoleCommand(currentThreadId, currentFrameIndex, fullCommand)
        }
    }

    private fun LLDBDriver.loadPrettyPrinters(threadId: Long, frameIndex: Int, sysroot: String?, renderers: LLDBRenderers) {
        when (renderers) {
            LLDBRenderers.COMPILER -> {
                if (sysroot == null) return
                val path = "$sysroot/lib/rustlib/etc/lldb_rust_formatters.py".systemDependentAndEscaped()
                try {
                    executeConsoleCommand(threadId, frameIndex, """command script import "$path" """)
                    executeConsoleCommand(threadId, frameIndex, """type summary add --no-value --python-function lldb_rust_formatters.print_val -x ".*" --category Rust""")
                    executeConsoleCommand(threadId, frameIndex, """type category enable Rust""")
                } catch (e: DebuggerCommandException) {
                    printlnToConsole(e.message)
                    LOG.warn(e)
                }
            }

            LLDBRenderers.BUNDLED -> {
                val path = PP_PATH.systemDependentAndEscaped()
                try {
                    executeConsoleCommand(threadId, frameIndex, """command script import "$path/$LLDB_LOOKUP.py" """)
                    executeConsoleCommand(threadId, frameIndex, """type synthetic add -l $LLDB_LOOKUP.synthetic_lookup -x ".*" --category Rust""")
                    executeConsoleCommand(threadId, frameIndex, """type summary add -F $LLDB_LOOKUP.summary_lookup  -e -x -h ".*" --category Rust""")
                    executeConsoleCommand(threadId, frameIndex, """type category enable Rust""")
                } catch (e: DebuggerCommandException) {
                    printlnToConsole(e.message)
                    LOG.warn(e)
                } catch (e: InvalidPathException) {
                    LOG.warn(e)
                }
            }

            LLDBRenderers.NONE -> {
            }
        }
    }

    private fun GDBDriver.loadPrettyPrinters(threadId: Long, frameIndex: Int, sysroot: String?, renderers: GDBRenderers) {
        when (renderers) {
            GDBRenderers.COMPILER -> {
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

            GDBRenderers.BUNDLED -> {
                val path = PP_PATH.systemDependentAndEscaped()
                val command = """python """ +
                    """sys.path.insert(0, "$path"); """ +
                    """import $GDB_LOOKUP; """ +
                    """$GDB_LOOKUP.register_printers(gdb); """
                try {
                    executeConsoleCommand(threadId, frameIndex, command)
                } catch (e: DebuggerCommandException) {
                    printlnToConsole(e.message)
                    LOG.warn(e)
                }
            }

            GDBRenderers.NONE -> {
            }
        }
    }

    private fun String.systemDependentAndEscaped(): String =
        StringUtil.escapeStringCharacters(FileUtil.toSystemDependentName(this))

    companion object {
        private val LOG: Logger = Logger.getInstance(RsLocalDebugProcess::class.java)
    }
}
