/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.debugger

import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.PlatformUtils
import com.intellij.util.ThrowableRunnable
import org.rust.RsTestBase
import org.rust.setRegistryOptionEnabled
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists

class RsDebuggerToolchainServiceTest : RsTestBase() {

    private var debuggerDir: Path? = null

    override fun runTestRunnable(testRunnable: ThrowableRunnable<Throwable>) {
        @Suppress("UnstableApiUsage")
        if (PlatformUtils.isIdeaUltimate()) {
            super.runTestRunnable(testRunnable)
        }
    }

    override fun setUp() {
        super.setUp()
        setRegistryOptionEnabled(Registry.get("org.rust.debugger.gdb.setup.v2"), true, testRootDisposable)
    }

    override fun tearDown() {
        debuggerDir?.toFile()?.deleteRecursively()
        debuggerDir = null
        super.tearDown()
    }

    fun `test lldb loading and update`() {
        checkDebuggerLoadingAndUpdate(DebuggerKind.LLDB)
    }

    fun `test gdb loading and update`() {
        if (SystemInfo.isMac) return
        checkDebuggerLoadingAndUpdate(DebuggerKind.GDB)
    }

    private fun checkDebuggerLoadingAndUpdate(kind: DebuggerKind) {
        val toolchainService = RsDebuggerToolchainService.getInstance()
        assertEquals(DebuggerAvailability.NeedToDownload, toolchainService.debuggerAvailability(kind))

        downloadDebugger(kind)

        // Emulate an outdated version
        val version = toolchainService.loadDebuggerVersions(kind)
        val propertyName = version.stringPropertyNames().first()
        val value = version.getProperty(propertyName)
        version[propertyName] = "$value!"
        toolchainService.saveDebuggerVersions(kind, version)

        assertEquals(DebuggerAvailability.NeedToUpdate, toolchainService.debuggerAvailability(kind))

        downloadDebugger(kind)
    }

    private fun downloadDebugger(kind: DebuggerKind) {
        val result = RsDebuggerToolchainService.getInstance().downloadDebugger(null, kind)
        check(result is RsDebuggerToolchainService.DownloadResult.Ok) {
            val message = (result as? RsDebuggerToolchainService.DownloadResult.Failed)?.message.orEmpty()
            "Failed to load debugger\n$message"
        }
        debuggerDir = result.baseDir
        checkAvailability(kind)
    }

    private fun checkAvailability(kind: DebuggerKind) {
        val availability = RsDebuggerToolchainService.getInstance().debuggerAvailability(kind)
        check(availability is DebuggerAvailability.Binaries) {
            "Unexpected ${kind.name.lowercase()} availability after downloading: $availability"
        }

        val binaries = availability.binaries
        val expectedFiles = when (kind) {
            DebuggerKind.LLDB -> listOf((binaries as LLDBBinaries).frameworkFile, binaries.frontendFile)
            DebuggerKind.GDB -> listOf((binaries as GDBBinaries).gdbFile)
        }

        fun Path.checkExistence() {
            check(exists()) { "Failed to find `${absolutePathString()}`" }
        }

        for (expectedFile in expectedFiles) {
            expectedFile.checkExistence()
        }
    }
}
