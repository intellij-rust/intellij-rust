/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project

import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.ComboBoxWithWidePopup
import com.intellij.openapi.ui.ComponentWithBrowseButton
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.ComboboxSpeedSearch
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.fields.ExtendableTextComponent
import com.intellij.ui.components.fields.ExtendableTextField
import org.rust.openapiext.pathAsPath
import org.rust.stdext.toPathOrNull
import java.nio.file.Path
import javax.swing.event.DocumentEvent
import javax.swing.plaf.basic.BasicComboBoxEditor

/**
 * A combobox with browse button for choosing a path to a toolchain, also capable of showing progress indicator.
 * To toggle progress indicator visibility use [setBusy] method.
 */
class RsToolchainPathChoosingComboBox(onTextChanged: () -> Unit = {})
    : ComponentWithBrowseButton<ComboBoxWithWidePopup<Path>>(ComboBoxWithWidePopup(), null) {
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

        pathTextField.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                onTextChanged()
            }
        })
    }

    fun setBusy(busy: Boolean) {
        if (busy) {
            pathTextField.addExtension(busyIconExtension)
        } else {
            pathTextField.removeExtension(busyIconExtension)
        }
        repaint()
    }
}
