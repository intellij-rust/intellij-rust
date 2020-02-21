/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk.add

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.text.StringUtil
import org.rust.stdext.isNotEmptyDirectory
import java.awt.Component
import java.io.File
import javax.swing.JComponent
import javax.swing.JPanel

abstract class RsAddSdkPanel : JPanel(), RsAddSdkView {
    override val actions: Map<RsAddSdkDialogFlowAction, Boolean>
        get() = mapOf(RsAddSdkDialogFlowAction.OK.enabled())

    override val component: Component
        get() = this

    override fun addStateListener(stateListener: RsAddSdkStateListener): Unit = Unit

    override fun previous(): Nothing = throw UnsupportedOperationException()

    override fun next(): Nothing = throw UnsupportedOperationException()

    override fun complete(): Unit = Unit

    abstract override val panelName: String
    open val sdk: Sdk? = null
    open val nameExtensionComponent: JComponent? = null
    open var newProjectPath: String? = null

    override fun getOrCreateSdk(): Sdk? = sdk

    override fun onSelected() {}

    override fun validateAll(): List<ValidationInfo> = emptyList()

    open fun addChangeListener(listener: Runnable) {}

    companion object {
        @JvmStatic
        protected fun validateEnvironmentDirectoryLocation(field: TextFieldWithBrowseButton): ValidationInfo? {
            val text = field.text
            val file = File(text)
            val message = when {
                StringUtil.isEmptyOrSpaces(text) -> "Environment location field is empty"
                file.exists() && !file.isDirectory -> "Environment location field path is not a directory"
                file.isNotEmptyDirectory -> "Environment location directory is not empty"
                else -> return null
            }
            return ValidationInfo(message, field)
        }

        @JvmStatic
        protected fun validateSdkComboBox(field: RsSdkPathChoosingComboBox): ValidationInfo? =
            if (field.selectedSdk == null) ValidationInfo("Toolchain field is empty", field) else null
    }
}

fun addToolchainsAsync(sdkComboBox: RsSdkPathChoosingComboBox, sdkObtainer: () -> List<Sdk>) {
    ApplicationManager.getApplication().executeOnPooledThread {
        ApplicationManager.getApplication().invokeLater({ sdkComboBox.setBusy(true) }, ModalityState.any())
        var sdks = emptyList<Sdk>()
        try {
            sdks = sdkObtainer()
        } finally {
            ApplicationManager.getApplication().invokeLater({
                sdkComboBox.setBusy(false)
                sdks.forEach {
                    sdkComboBox.childComponent.addItem(it)
                }
            }, ModalityState.any())
        }
    }
}
