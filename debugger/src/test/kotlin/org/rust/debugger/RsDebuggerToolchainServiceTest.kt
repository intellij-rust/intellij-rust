/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.debugger

import com.intellij.openapi.util.SystemInfo
import com.intellij.util.PlatformUtils
import org.rust.RsTestBase
import org.rust.debugger.RsDebuggerToolchainService.LLDBStatus
import java.io.File

class RsDebuggerToolchainServiceTest : RsTestBase() {

    private var lldbDir: File? = null

    override fun runTest() {
        if (PlatformUtils.isIdeaUltimate() && !SystemInfo.isWindows) {
            super.runTest()
        }
    }

    override fun tearDown() {
        lldbDir?.deleteRecursively()
        lldbDir = null
        super.tearDown()
    }

    fun `test lldb loading and update`() {
        val toolchainService = RsDebuggerToolchainService.getInstance()
        assertEquals(LLDBStatus.NeedToDownload, toolchainService.getLLDBStatus())

        downloadDebugger()

        val version = toolchainService.loadLLDBVersions()
        version[RsDebuggerToolchainService.LLDB_FRONTEND_PROPERTY_NAME] = "lldbfrontend-1234567890-mac-x64"
        toolchainService.saveLLDBVersions(version)

        assertEquals(LLDBStatus.NeedToUpdate, toolchainService.getLLDBStatus(lldbDir?.absolutePath))

        downloadDebugger()
    }

    private fun downloadDebugger() {
        val toolchainService = RsDebuggerToolchainService.getInstance()
        val result = toolchainService.downloadDebugger()
        check(result is RsDebuggerToolchainService.DownloadResult.Ok) { "Failed to load debugger" }
        lldbDir = result.lldbDir
        val lldbStatus = toolchainService.getLLDBStatus(result.lldbDir.absolutePath)
        check(lldbStatus is LLDBStatus.Binaries) {
            "Unexpected lldb status after downloading: $lldbStatus"
        }

        fun checkFileExist(file: File) {
            check(file.exists()) { "Failed to find `${file.absoluteFile}`" }
        }
        checkFileExist(lldbStatus.frontendFile)
        checkFileExist(lldbStatus.frameworkFile)
    }
}
