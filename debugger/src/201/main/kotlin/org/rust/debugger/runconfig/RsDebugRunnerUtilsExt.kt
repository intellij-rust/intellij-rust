/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.debugger.runconfig

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import org.rust.debugger.RsDebuggerToolchainService
import org.rust.debugger.RsDebuggerToolchainService.LLDBStatus
import org.rust.debugger.settings.RsDebuggerSettings

// BACKCOMPAT: 2019.3. Merge with RsDebugRunnerUtils
object RsDebugRunnerUtilsExt {

    fun checkToolchainConfigured(project: Project): Boolean {
        val lldbStatus = RsDebuggerToolchainService.getInstance().getLLDBStatus()
        val (message, action) = when (lldbStatus) {
            LLDBStatus.Unavailable -> return false
            LLDBStatus.NeedToDownload -> "Debugger is not loaded yet" to "Download"
            LLDBStatus.NeedToUpdate -> "Debugger is outdated" to "Update"
            is LLDBStatus.Binaries -> return true
        }

        // TODO: add option to download/update debugger automatically
        val option = Messages.showDialog(
            project,
            message,
            RsDebugRunnerUtils.ERROR_MESSAGE_TITLE,
            arrayOf(action),
            Messages.OK,
            Messages.getErrorIcon()
        )
        if (option == Messages.OK) {
            RsDebuggerToolchainService.getInstance().downloadDebugger({
                RsDebuggerSettings.getInstance().lldbPath = it.absolutePath
            }, {})
        }
        return false
    }
}
