/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.debugger.runconfig

import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.jetbrains.cidr.execution.debugger.CidrDebugProcess
import com.jetbrains.cidr.execution.debugger.backend.DebuggerCommandException
import com.jetbrains.cidr.execution.debugger.backend.DebuggerDriver
import com.jetbrains.cidr.execution.debugger.backend.gdb.GDBDriver
import com.jetbrains.cidr.execution.debugger.backend.lldb.LLDBDriver
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.runconfig.command.workingDirectory
import org.rust.debugger.*
import org.rust.debugger.settings.RsDebuggerSettings
import org.rust.ide.notifications.showBalloon
import java.nio.file.InvalidPathException

class RsDebugProcessConfigurationHelper(
    private val process: CidrDebugProcess,
    cargoProject: CargoProject?,
    private val isCrossLanguage: Boolean = false
) {
    private val settings = RsDebuggerSettings.getInstance()
    private val project = process.project
    private val toolchain = process.project.toolchain
    private val threadId = process.currentThreadId
    private val frameIndex = process.currentFrameIndex

    private val commitHash = cargoProject?.rustcInfo?.version?.commitHash

    private val sysroot: String? by lazy {
        cargoProject?.workingDirectory?.let { toolchain?.getSysroot(it) }
    }

    fun configure() {
        process.postCommand { driver ->
            try {
                driver.loadRustcSources()
                driver.loadPrettyPrinters()
            } catch (e: DebuggerCommandException) {
                process.printlnToConsole(e.message)
                LOG.warn(e)
            } catch (e: InvalidPathException) {
                LOG.warn(e)
            }
        }
    }

    private fun DebuggerDriver.loadRustcSources() {
        if (commitHash == null) return

        val sysroot = checkSysroot(sysroot, "Cannot load rustc sources") ?: return
        val sourceMapCommand = when (this) {
            is LLDBDriver -> "settings set target.source-map"
            is GDBDriver -> "set substitute-path"
            else -> return
        }
        val rustcHash = "/rustc/$commitHash/".systemDependentAndEscaped()
        val rustcSources = "$sysroot/lib/rustlib/src/rust/".systemDependentAndEscaped()
        val fullCommand = """$sourceMapCommand "$rustcHash" "$rustcSources" """
        executeConsoleCommand(threadId, frameIndex, fullCommand)
    }

    private fun DebuggerDriver.loadPrettyPrinters() {
        when (this) {
            is LLDBDriver -> loadPrettyPrinters()
            is GDBDriver -> loadPrettyPrinters()
        }
    }

    private fun LLDBDriver.loadPrettyPrinters() {
        when (settings.lldbRenderers) {
            LLDBRenderers.COMPILER -> {
                val sysroot = checkSysroot(sysroot, "Cannot load rustc renderers") ?: return
                val path = "$sysroot/lib/rustlib/etc/lldb_rust_formatters.py".systemDependentAndEscaped()
                executeConsoleCommand(threadId, frameIndex, """command script import "$path" """)
                executeConsoleCommand(threadId, frameIndex, """type summary add --no-value --python-function lldb_rust_formatters.print_val -x ".*" --category Rust""")
                executeConsoleCommand(threadId, frameIndex, """type category enable Rust""")
            }

            LLDBRenderers.BUNDLED -> {
                val path = PP_PATH.systemDependentAndEscaped()
                executeConsoleCommand(threadId, frameIndex, """command script import "$path/$LLDB_LOOKUP.py" """)

                // In case of cross-language projects, lldb pretty-printers should be enabled
                // only for specific std types (but should not be enabled for arbitrary struct/enums),
                // because `type synthetic add ... -x ".*"` overrides C++ STL pretty-printers
                val enabledTypes = if (isCrossLanguage) RUST_STD_TYPES else listOf(".*")
                for (type in enabledTypes) {
                    executeConsoleCommand(threadId, frameIndex, """type synthetic add -l $LLDB_LOOKUP.synthetic_lookup -x "$type" --category Rust""")
                    executeConsoleCommand(threadId, frameIndex, """type summary add -F $LLDB_LOOKUP.summary_lookup  -e -x -h "$type" --category Rust""")
                }

                executeConsoleCommand(threadId, frameIndex, """type category enable Rust""")
            }

            LLDBRenderers.NONE -> {
            }
        }
    }

    private fun GDBDriver.loadPrettyPrinters() {
        when (settings.gdbRenderers) {
            GDBRenderers.COMPILER -> {
                val sysroot = checkSysroot(sysroot, "Cannot load rustc renderers") ?: return
                val path = "$sysroot/lib/rustlib/etc".systemDependentAndEscaped()
                // Avoid multiline Python scripts due to https://youtrack.jetbrains.com/issue/CPP-9090
                val command = """python """ +
                    """sys.path.insert(0, "$path"); """ +
                    """import gdb_rust_pretty_printing; """ +
                    """gdb_rust_pretty_printing.register_printers(gdb); """
                executeConsoleCommand(threadId, frameIndex, command)
            }

            GDBRenderers.BUNDLED -> {
                val path = PP_PATH.systemDependentAndEscaped()
                val command = """python """ +
                    """sys.path.insert(0, "$path"); """ +
                    """import $GDB_LOOKUP; """ +
                    """$GDB_LOOKUP.register_printers(gdb); """
                executeConsoleCommand(threadId, frameIndex, command)
            }

            GDBRenderers.NONE -> {
            }
        }
    }

    private fun checkSysroot(sysroot: String?, message: String): String? {
        if (sysroot == null) {
            project.showBalloon(message, NotificationType.WARNING)
        }
        return sysroot
    }

    private fun String.systemDependentAndEscaped(): String =
        StringUtil.escapeStringCharacters(FileUtil.toSystemDependentName(this))

    companion object {
        private val LOG: Logger = Logger.getInstance(RsDebugProcessConfigurationHelper::class.java)

        /** Should be synchronized with `rust_types.py` */
        private val RUST_STD_TYPES: List<String> = listOf(
            "^(alloc::(\\w+::)+)String$",
            "^&str$",
            "^(std::ffi::(\\w+::)+)OsString$",
            "^(alloc::(\\w+::)+)Vec<.+>$",
            "^(alloc::(\\w+::)+)VecDeque<.+>$",
            "^(alloc::(\\w+::)+)BTreeSet<.+>$",
            "^(alloc::(\\w+::)+)BTreeMap<.+>$",
            "^(std::collections::(\\w+::)+)HashMap<.+>$",
            "^(std::collections::(\\w+::)+)HashSet<.+>$",
            "^(alloc::(\\w+::)+)Rc<.+>$",
            "^(alloc::(\\w+::)+)Arc<.+>$",
            "^(core::(\\w+::)+)Cell<.+>$",
            "^(core::(\\w+::)+)Ref<.+>$",
            "^(core::(\\w+::)+)RefMut<.+>$",
            "^(core::(\\w+::)+)RefCell<.+>$"
        )
    }
}
