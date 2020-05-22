/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.debugger.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.options.ConfigurableUi
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.Link
import com.intellij.ui.layout.panel
import org.rust.debugger.RsDebuggerToolchainService
import org.rust.debugger.RsDebuggerToolchainService.LLDBStatus
import org.rust.openapiext.pathToDirectoryTextField
import javax.swing.JComponent
import javax.swing.JLabel

class RsDebuggerToolchainConfigurableUi : ConfigurableUi<RsDebuggerSettings>, Disposable {

    private val downloadLink: JLabel = Link("Download") {
        val result = RsDebuggerToolchainService.getInstance().downloadDebugger()
        if (result is RsDebuggerToolchainService.DownloadResult.Ok) {
            lldbPathField.text = result.lldbDir.absolutePath
        }
    } as JLabel

    private val lldbPathField = pathToDirectoryTextField(
        this,
        "Select LLDB directory path",
        onTextChanged = ::update
    ).apply {
        isEditable = false
    }

    private val downloadAutomaticallyCheckBox: JBCheckBox =
        JBCheckBox("Download and update debugger automatically", RsDebuggerSettings.getInstance().downloadAutomatically)

    override fun isModified(settings: RsDebuggerSettings): Boolean {
        return settings.lldbPath != lldbPathField.text &&
               settings.downloadAutomatically != downloadAutomaticallyCheckBox.isSelected
    }

    override fun reset(settings: RsDebuggerSettings) {
        lldbPathField.text = settings.lldbPath.orEmpty()
        downloadAutomaticallyCheckBox.isSelected = settings.downloadAutomatically
    }

    override fun apply(settings: RsDebuggerSettings) {
        settings.lldbPath = lldbPathField.text
        settings.downloadAutomatically = downloadAutomaticallyCheckBox.isSelected
    }

    override fun getComponent(): JComponent {
        lldbPathField.text = RsDebuggerSettings.getInstance().lldbPath.orEmpty()
        update()
        return panel {
            row("LLDB path:") { lldbPathField() }
            row("") { downloadLink() }
            row { downloadAutomaticallyCheckBox() }
        }
    }

    override fun dispose() {}

    private fun update() {
        @Suppress("MoveVariableDeclarationIntoWhen")
        val status = RsDebuggerToolchainService.getInstance().getLLDBStatus(lldbPathField.text)
        val (text, isVisible) = when (status) {
            LLDBStatus.Unavailable -> error("Unreachable")
            LLDBStatus.NeedToDownload -> "Download" to true
            LLDBStatus.NeedToUpdate -> "Update" to true
            is LLDBStatus.Binaries -> "" to false
        }
        downloadLink.text = text
        downloadLink.isVisible = isVisible
    }
}
