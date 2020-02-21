package org.rust.ide.sdk.add

import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.ComboBoxWithWidePopup
import com.intellij.openapi.ui.ComponentWithBrowseButton
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.ComboboxSpeedSearch
import com.intellij.ui.components.fields.ExtendableTextComponent
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.util.PathUtil
import org.rust.ide.sdk.RsDetectedSdk
import org.rust.ide.sdk.RsSdkListCellRenderer
import org.rust.ide.sdk.RsSdkType
import javax.swing.plaf.basic.BasicComboBoxEditor

class RsSdkPathChoosingComboBox(sdks: List<Sdk> = emptyList(), suggestedFile: VirtualFile? = null) :
    ComponentWithBrowseButton<ComboBoxWithWidePopup<Sdk>>(ComboBoxWithWidePopup(sdks.toTypedArray()), null) {
    private val busyIconExtension: ExtendableTextComponent.Extension =
        ExtendableTextComponent.Extension { AnimatedIcon.Default.INSTANCE }

    private val editor: BasicComboBoxEditor = object : BasicComboBoxEditor() {
        override fun createEditorComponent(): ExtendableTextField =
            ExtendableTextField().apply { isEditable = false }
    }

    init {
        childComponent.apply {
            renderer = RsSdkListCellRenderer(null)
            ComboboxSpeedSearch(this)
        }
        addActionListener {
            val sdkType = RsSdkType.getInstance()
            val descriptor = sdkType.homeChooserDescriptor.apply {
                isForcedToUseIdeaFileChooser = true
            }
            FileChooser.chooseFiles(descriptor, null, suggestedFile) {
                val virtualFile = it.firstOrNull() ?: return@chooseFiles
                val path = PathUtil.toSystemDependentName(virtualFile.path)
                if (!sdkType.isValidSdkHome(path)) return@chooseFiles
                childComponent.selectedItem = items.find { sdk -> sdk.homePath == path }
                    ?: RsDetectedSdk(path).apply { childComponent.insertItemAt(this, 0) }
            }
        }
    }

    var selectedSdk: Sdk?
        get() = childComponent.selectedItem as? Sdk?
        set(value) {
            if (value in items) {
                childComponent.selectedItem = value
            }
        }

    val items: List<Sdk>
        get() = (0 until childComponent.itemCount).map { childComponent.getItemAt(it) }

    fun setBusy(busy: Boolean) {
        if (busy) {
            childComponent.isEditable = true
            childComponent.editor = editor
            (childComponent.editor.editorComponent as ExtendableTextField).addExtension(busyIconExtension)
        } else {
            (childComponent.editor.editorComponent as ExtendableTextField).removeExtension(busyIconExtension)
            childComponent.isEditable = false
        }
        repaint()
    }
}
