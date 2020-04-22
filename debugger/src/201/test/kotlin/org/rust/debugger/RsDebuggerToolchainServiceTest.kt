/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.debugger

import com.intellij.openapi.util.SystemInfo
import com.intellij.util.PlatformUtils
import com.intellij.util.ui.UIUtil
import org.rust.RsTestBase
import org.rust.debugger.RsDebuggerToolchainService.LLDBStatus
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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
        val latch = CountDownLatch(1)
        val toolchainService = RsDebuggerToolchainService.getInstance()

        assertEquals(LLDBStatus.NeedToDownload, toolchainService.getLLDBStatus())

        toolchainService.downloadDebugger({
            lldbDir = it
            latch.countDown()
        }, {
            latch.countDown()
        })

        while (!latch.await(50, TimeUnit.MILLISECONDS)) {
            UIUtil.dispatchAllInvocationEvents()
        }

        check(lldbDir != null) { "Failed to load debugger" }
        check(toolchainService.getLLDBStatus(lldbDir!!.absolutePath) is LLDBStatus.Binaries)
    }
}
