/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.debugger.runconfig

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
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

        val option = if (!RsDebuggerSettings.getInstance().downloadAutomatically) {
            showDialog(project, message, action)
        } else {
            Messages.OK
        }

        if (option == Messages.OK) {
            val result = RsDebuggerToolchainService.getInstance().downloadDebugger(project);
            if (result is RsDebuggerToolchainService.DownloadResult.Ok) {
                RsDebuggerSettings.getInstance().lldbPath = result.lldbDir.absolutePath
                return true
            }
        }
        return false
    }

    private fun showDialog(project: Project, message: String, action: String): Int {
        return Messages.showDialog(
            project,
            message,
            RsDebugRunnerUtils.ERROR_MESSAGE_TITLE,
            arrayOf(action),
            Messages.OK,
            Messages.getErrorIcon(),
            object : DialogWrapper.DoNotAskOption.Adapter() {
                override fun rememberChoice(isSelected: Boolean, exitCode: Int) {
                    if (exitCode == Messages.OK) {
                        RsDebuggerSettings.getInstance().downloadAutomatically = isSelected
                    }
                }
            }
        )
    }
}
