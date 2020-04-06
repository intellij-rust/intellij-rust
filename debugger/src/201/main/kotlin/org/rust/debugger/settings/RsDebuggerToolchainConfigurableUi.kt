/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.debugger.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.ConfigurableUi
import com.intellij.ui.components.Link
import com.intellij.ui.layout.panel
import org.rust.debugger.RsDebuggerToolchainService
import org.rust.debugger.RsDebuggerToolchainService.LLDBStatus
import org.rust.openapiext.pathToDirectoryTextField
import javax.swing.JComponent
import javax.swing.JLabel

class RsDebuggerToolchainConfigurableUi : ConfigurableUi<RsDebuggerSettings>, Disposable {

    init {
        ApplicationManager.getApplication().invokeLater(::update)
    }

    private val downloadLink: JLabel = Link("Download") {
        RsDebuggerToolchainService.getInstance().downloadDebugger(
            onSuccess = { lldbPathField.text = it.absolutePath },
            onFailure = {}
        )
    } as JLabel

    private val lldbPathField = pathToDirectoryTextField(
        this,
        RsDebuggerSettings.getInstance().lldbPath.orEmpty(),
        onTextChanged = ::update
    ).apply {
        text = RsDebuggerSettings.getInstance().lldbPath.orEmpty()
        isEditable = false
    }

    override fun isModified(settings: RsDebuggerSettings): Boolean {
        return settings.lldbPath != lldbPathField.text
    }

    override fun reset(settings: RsDebuggerSettings) {
        lldbPathField.text = settings.lldbPath.orEmpty()
    }

    override fun apply(settings: RsDebuggerSettings) {
        settings.lldbPath = lldbPathField.text
    }

    override fun getComponent(): JComponent {
        return panel {
            row("LLDB path:") { lldbPathField() }
            row("") { downloadLink() }
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
