/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.debugger.settings

import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.Link
import com.intellij.ui.dsl.builder.Panel
import org.rust.debugger.RsDebuggerBundle
import org.rust.debugger.RsDebuggerToolchainService
import org.rust.debugger.RsDebuggerToolchainService.LLDBStatus
import org.rust.openapiext.fullWidthCell
import org.rust.openapiext.pathToDirectoryTextField
import javax.swing.AbstractButton
import javax.swing.JComponent
import javax.swing.JLabel

class RsDebuggerToolchainConfigurableUi : RsDebuggerUiComponent() {

    private val downloadLink: JComponent = Link(RsDebuggerBundle.message("settings.rust.debugger.toolchain.download.label")) {
        val result = RsDebuggerToolchainService.getInstance().downloadDebugger()
        if (result is RsDebuggerToolchainService.DownloadResult.Ok) {
            lldbPathField.text = result.lldbDir.absolutePath
        }
    }

    private val lldbPathField = pathToDirectoryTextField(
        this,
        RsDebuggerBundle.message("settings.rust.debugger.toolchain.select.lldb.directory.dialog.title"),
        onTextChanged = ::update
    ).apply {
        isEditable = false
    }

    private val downloadAutomaticallyCheckBox: JBCheckBox =
        JBCheckBox(RsDebuggerBundle.message("settings.rust.debugger.toolchain.download.debugger.automatically.checkbox"), RsDebuggerSettings.getInstance().downloadAutomatically)

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

    override fun buildUi(panel: Panel) {
        lldbPathField.text = RsDebuggerSettings.getInstance().lldbPath.orEmpty()
        update()
        with(panel) {
            row(RsDebuggerBundle.message("settings.rust.debugger.toolchain.lldb.path.label")) { fullWidthCell(lldbPathField) }
            row("") { cell(downloadLink) }
            row { cell(downloadAutomaticallyCheckBox) }
        }
    }

    private fun update() {
        @Suppress("MoveVariableDeclarationIntoWhen")
        val status = RsDebuggerToolchainService.getInstance().getLLDBStatus(lldbPathField.text)
        val (text, isVisible) = when (status) {
            LLDBStatus.Unavailable,
            LLDBStatus.Bundled -> error("Unreachable")
            LLDBStatus.NeedToDownload -> RsDebuggerBundle.message("settings.rust.debugger.toolchain.download.label") to true
            LLDBStatus.NeedToUpdate -> RsDebuggerBundle.message("settings.rust.debugger.toolchain.update.label") to true
            is LLDBStatus.Binaries -> "" to false
        }
        when (downloadLink) {
            is JLabel -> downloadLink.text = text
            is AbstractButton -> downloadLink.text = text
        }

        downloadLink.isVisible = isVisible
    }
}
