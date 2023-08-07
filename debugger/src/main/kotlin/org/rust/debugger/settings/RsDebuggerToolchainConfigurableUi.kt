/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.debugger.settings

import com.intellij.openapi.observable.util.whenItemSelected
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.COLUMNS_SHORT
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.columns
import org.rust.debugger.DebuggerAvailability
import org.rust.debugger.DebuggerKind
import org.rust.debugger.RsDebuggerBundle
import org.rust.debugger.RsDebuggerToolchainService
import java.util.*
import javax.swing.ComboBoxModel
import javax.swing.DefaultComboBoxModel
import javax.swing.JEditorPane

class RsDebuggerToolchainConfigurableUi : RsDebuggerUiComponent() {

    private val debuggerKindCombobox = ComboBox(createDebuggerKindComboBoxModel())
    private val downloadAutomaticallyCheckBox: JBCheckBox =
        JBCheckBox(RsDebuggerBundle.message("settings.rust.debugger.toolchain.download.debugger.automatically.checkbox"), RsDebuggerSettings.getInstance().downloadAutomatically)

    private var comment: JEditorPane? = null

    private val currentDebuggerKind: DebuggerKind get() = debuggerKindCombobox.item

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
                    .applyToComponent {
                        whenItemSelected {
                            update()
                        }
                    }
                    .comment
            }
            row { cell(downloadAutomaticallyCheckBox) }
        }
        update()
    }

    private fun createDebuggerKindComboBoxModel(): ComboBoxModel<DebuggerKind> {
        val toolchainService = RsDebuggerToolchainService.getInstance()
        val availableKinds = mutableListOf<DebuggerKind>()
        if (toolchainService.lldbAvailability() != DebuggerAvailability.Unavailable) {
            availableKinds += DebuggerKind.LLDB
        }
        if (toolchainService.gdbAvailability() != DebuggerAvailability.Unavailable) {
            availableKinds += DebuggerKind.GDB
        }

        val model = DefaultComboBoxModel(Vector(availableKinds))
        model.selectedItem = RsDebuggerSettings.getInstance().debuggerKind
        return model
    }

    private fun downloadDebugger() {
        val result = RsDebuggerToolchainService.getInstance().downloadDebugger(debuggerKind = currentDebuggerKind)
        if (result is RsDebuggerToolchainService.DownloadResult.Ok) {
            update()
        }
    }

    private fun update() {
        val availability = RsDebuggerToolchainService.getInstance().debuggerAvailability(currentDebuggerKind)
        val text = when (availability) {
            DebuggerAvailability.NeedToDownload -> RsDebuggerBundle.message("settings.rust.debugger.toolchain.download.comment")
            DebuggerAvailability.NeedToUpdate -> RsDebuggerBundle.message("settings.rust.debugger.toolchain.update.comment")
            else -> null
        }

        comment?.text = text
        comment?.isVisible = text != null
    }
}
