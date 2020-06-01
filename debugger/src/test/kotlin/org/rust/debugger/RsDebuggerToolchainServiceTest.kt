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

    fun `test lldb loading`() {
        val toolchainService = RsDebuggerToolchainService.getInstance()
        assertEquals(LLDBStatus.NeedToDownload, toolchainService.getLLDBStatus())

        val result = toolchainService.downloadDebugger()

        check(result is RsDebuggerToolchainService.DownloadResult.Ok) { "Failed to load debugger" }
        lldbDir = result.lldbDir
        check(toolchainService.getLLDBStatus(result.lldbDir.absolutePath) is LLDBStatus.Binaries)
    }
}
