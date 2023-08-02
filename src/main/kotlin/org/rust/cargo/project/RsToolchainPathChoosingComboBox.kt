/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project

import com.intellij.openapi.application.AppUIExecutor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.ComboBoxWithWidePopup
import com.intellij.openapi.ui.ComponentWithBrowseButton
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.ComboboxSpeedSearch
import com.intellij.ui.components.fields.ExtendableTextComponent
import com.intellij.ui.components.fields.ExtendableTextField
import org.rust.openapiext.addTextChangeListener
import org.rust.openapiext.pathAsPath
import org.rust.stdext.toPathOrNull
import java.nio.file.Path
import javax.swing.plaf.basic.BasicComboBoxEditor

/**
 * A combobox with browse button for choosing a path to a toolchain, also capable of showing progress indicator.
 * To toggle progress indicator visibility use [setBusy] method.
 */
class RsToolchainPathChoosingComboBox(onTextChanged: () -> Unit = {}) : ComponentWithBrowseButton<ComboBoxWithWidePopup<Path>>(ComboBoxWithWidePopup(), null) {
    private val editor: BasicComboBoxEditor = object : BasicComboBoxEditor() {
        override fun createEditorComponent(): ExtendableTextField = ExtendableTextField()
    }

    private val pathTextField: ExtendableTextField
        get() = childComponent.editor.editorComponent as ExtendableTextField

    private val busyIconExtension: ExtendableTextComponent.Extension =
        ExtendableTextComponent.Extension { AnimatedIcon.Default.INSTANCE }

    var selectedPath: Path?
        get() = pathTextField.text?.toPathOrNull()
        set(value) {
            pathTextField.text = value?.toString().orEmpty()
        }

    init {
        ComboboxSpeedSearch(childComponent)
        childComponent.editor = editor
        childComponent.isEditable = true

        addActionListener {
            // Select directory with Cargo binary
            val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
            FileChooser.chooseFile(descriptor, null, null) { file ->
                childComponent.selectedItem = file.pathAsPath
            }
        }

        pathTextField.addTextChangeListener { onTextChanged() }
    }

    private fun setBusy(busy: Boolean) {
        if (busy) {
            pathTextField.addExtension(busyIconExtension)
        } else {
            pathTextField.removeExtension(busyIconExtension)
        }
        repaint()
    }

    /**
     * Obtains a list of toolchains on a pool using [toolchainObtainer], then fills the combobox and calls [callback] on the EDT.
     */
    @Suppress("UnstableApiUsage", "MemberVisibilityCanBePrivate")
    fun addToolchainsAsync(toolchainObtainer: () -> List<Path>, callback: () -> Unit) {
        setBusy(true)
        ApplicationManager.getApplication().executeOnPooledThread {
            var toolchains = emptyList<Path>()
            try {
                toolchains = toolchainObtainer()
            } finally {
                val executor = AppUIExecutor.onUiThread(ModalityState.any()).expireWith(this)
                executor.execute {
                    setBusy(false)
                    val oldSelectedPath = selectedPath
                    childComponent.removeAllItems()
                    toolchains.forEach(childComponent::addItem)
                    selectedPath = oldSelectedPath
                    callback()
                }
            }
        }
    }

    fun addToolchainsAsync(toolchainObtainer: () -> List<Path>) {
        addToolchainsAsync(toolchainObtainer) {}
    }
}
