/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.debugger

import com.intellij.util.PlatformUtils
import com.intellij.util.ThrowableRunnable
import org.rust.RsTestBase
import java.io.File

class RsDebuggerToolchainServiceTest : RsTestBase() {

    private var lldbDir: File? = null

    override fun runTestRunnable(testRunnable: ThrowableRunnable<Throwable>) {
        @Suppress("UnstableApiUsage")
        if (PlatformUtils.isIdeaUltimate()) {
            super.runTestRunnable(testRunnable)
        }
    }

    override fun tearDown() {
        lldbDir?.deleteRecursively()
        lldbDir = null
        super.tearDown()
    }

    fun `test lldb loading and update`() {
        val toolchainService = RsDebuggerToolchainService.getInstance()
        assertEquals(DebuggerAvailability.NeedToDownload, toolchainService.lldbAvailability())

        downloadDebugger()

        val version = toolchainService.loadLLDBVersions()
        version[RsDebuggerToolchainService.LLDB_FRONTEND_PROPERTY_NAME] = "lldbfrontend-1234567890-mac-x64"
        toolchainService.saveLLDBVersions(version)

        assertEquals(DebuggerAvailability.NeedToUpdate, toolchainService.lldbAvailability(lldbDir?.absolutePath))

        downloadDebugger()
    }

    private fun downloadDebugger() {
        val toolchainService = RsDebuggerToolchainService.getInstance()
        val result = toolchainService.downloadDebugger()
        check(result is RsDebuggerToolchainService.DownloadResult.Ok) {
            val message = (result as? RsDebuggerToolchainService.DownloadResult.Failed)?.message.orEmpty()
            "Failed to load debugger\n$message"
        }
        lldbDir = result.lldbDir
        val lldbAvailability = toolchainService.lldbAvailability(result.lldbDir.absolutePath)
        check(lldbAvailability is DebuggerAvailability.Binaries) {
            "Unexpected lldb availability after downloading: $lldbAvailability"
        }

        fun checkFileExist(file: File) {
            check(file.exists()) { "Failed to find `${file.absoluteFile}`" }
        }
        checkFileExist(lldbAvailability.binaries.frontendFile)
        checkFileExist(lldbAvailability.binaries.frameworkFile)
    }
}
