/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.debugger.settings

import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.EnumComboBoxModel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.COLUMNS_SHORT
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.columns
import org.rust.debugger.DebuggerKind
import org.rust.debugger.DebuggerAvailability
import org.rust.debugger.RsDebuggerBundle
import org.rust.debugger.RsDebuggerToolchainService
import javax.swing.ComboBoxModel
import javax.swing.JEditorPane

class RsDebuggerToolchainConfigurableUi : RsDebuggerUiComponent() {

    private val debuggerKindCombobox = ComboBox(createDebuggerKindComboBoxModel())
    private val downloadAutomaticallyCheckBox: JBCheckBox =
        JBCheckBox(RsDebuggerBundle.message("settings.rust.debugger.toolchain.download.debugger.automatically.checkbox"), RsDebuggerSettings.getInstance().downloadAutomatically)

    private var comment: JEditorPane? = null

    override fun isModified(settings: RsDebuggerSettings): Boolean {
        return settings.debuggerKind != debuggerKindCombobox.item ||
            settings.downloadAutomatically != downloadAutomaticallyCheckBox.isSelected
    }

    override fun reset(settings: RsDebuggerSettings) {
        debuggerKindCombobox.item = settings.debuggerKind
        downloadAutomaticallyCheckBox.isSelected = settings.downloadAutomatically
    }

    override fun apply(settings: RsDebuggerSettings) {
        settings.debuggerKind = debuggerKindCombobox.item
        settings.downloadAutomatically = downloadAutomaticallyCheckBox.isSelected
    }

    override fun buildUi(panel: Panel) {
        with(panel) {
            row(RsDebuggerBundle.message("settings.rust.debugger.toolchain.debugger.label")) {
                comment = cell(debuggerKindCombobox)
                    .columns(COLUMNS_SHORT)
                    // Exact text will be set in `update` call not to duplicate the code
                    .comment("") { downloadDebugger() }
                    .comment
            }
            row { cell(downloadAutomaticallyCheckBox) }
        }
        update()
    }

    private fun createDebuggerKindComboBoxModel(): ComboBoxModel<DebuggerKind> {
        val model = EnumComboBoxModel(DebuggerKind::class.java)
        model.setSelectedItem(RsDebuggerSettings.getInstance().debuggerKind)
        return model
    }

    private fun downloadDebugger() {
        val result = RsDebuggerToolchainService.getInstance().downloadDebugger()
        if (result is RsDebuggerToolchainService.DownloadResult.Ok) {
            RsDebuggerSettings.getInstance().lldbPath = result.lldbDir.absolutePath
            update()
        }
    }

    private fun update() {
        @Suppress("MoveVariableDeclarationIntoWhen")
        val availability = RsDebuggerToolchainService.getInstance().lldbAvailability()
        val text = when (availability) {
            DebuggerAvailability.NeedToDownload -> RsDebuggerBundle.message("settings.rust.debugger.toolchain.download.comment")
            DebuggerAvailability.NeedToUpdate -> RsDebuggerBundle.message("settings.rust.debugger.toolchain.update.comment")
            else -> null
        }

        comment?.text = text
        comment?.isVisible = text != null
    }
}
