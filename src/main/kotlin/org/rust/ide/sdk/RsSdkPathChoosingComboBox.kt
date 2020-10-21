package org.rust.ide.sdk

import com.intellij.openapi.application.AppUIExecutor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.ComboBoxWithWidePopup
import com.intellij.openapi.ui.ComponentWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.ComboboxSpeedSearch
import com.intellij.ui.components.fields.ExtendableTextComponent
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.util.PathUtil
import org.rust.ide.sdk.RsSdkPathChoosingComboBox.Companion.addToolchainsAsync
import javax.swing.plaf.basic.BasicComboBoxEditor

/**
 * A combobox with browse button for choosing a path to SDK, also capable of showing progress indicator.
 * To toggle progress indicator visibility use [setBusy] method.
 *
 * To fill this box in async mode use [addToolchainsAsync]
 */
class RsSdkPathChoosingComboBox(sdks: List<Sdk> = emptyList(), suggestedFile: VirtualFile? = null) :
    ComponentWithBrowseButton<ComboBoxWithWidePopup<Sdk>>(ComboBoxWithWidePopup(sdks.toTypedArray()), null) {
    private val busyIconExtension: ExtendableTextComponent.Extension =
        ExtendableTextComponent.Extension { AnimatedIcon.Default.INSTANCE }

    private val editor: BasicComboBoxEditor = object : BasicComboBoxEditor() {
        override fun createEditorComponent(): ExtendableTextField =
            ExtendableTextField().apply { isEditable = false }
    }

    val selectedSdk: Sdk?
        get() = childComponent.selectedItem as? Sdk

    val items: List<Sdk>
        get() = (0 until childComponent.itemCount).map { childComponent.getItemAt(it) }

    init {
        childComponent.apply {
            renderer = RsSdkListCellRenderer()
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

    companion object {
        /**
         * Obtains a list of sdk on a pool using [sdkObtainer], then fills [sdkComboBox] on the EDT.
         */
        @Suppress("UnstableApiUsage")
        fun addToolchainsAsync(sdkComboBox: RsSdkPathChoosingComboBox, sdkObtainer: () -> List<Sdk>) {
            ApplicationManager.getApplication().executeOnPooledThread {
                val executor = AppUIExecutor.onUiThread(ModalityState.any())
                executor.execute { sdkComboBox.setBusy(true) }
                var sdks = emptyList<Sdk>()
                try {
                    sdks = sdkObtainer()
                } finally {
                    executor.execute {
                        sdkComboBox.setBusy(false)
                        sdks.forEach(sdkComboBox.childComponent::addItem)
                    }
                }
            }
        }

        fun validateSdkComboBox(field: RsSdkPathChoosingComboBox): ValidationInfo? =
            if (field.selectedSdk == null) ValidationInfo("Toolchain field is empty", field) else null
    }
}
