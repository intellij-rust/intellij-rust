/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.SdkModificator
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.Disposer
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.layout.panel
import org.rust.ide.sdk.RsSdkAdditionalDataPanel.Companion.validateSdkAdditionalDataPanel
import org.rust.openapiext.pathTextField
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JTextField
import javax.swing.event.DocumentEvent

class RsEditSdkDialog(
    project: Project,
    sdk: SdkModificator,
    nameValidator: (String) -> String?
) : DialogWrapper(project, true) {
    private val nameTextField: JTextField = JTextField()
    val name: String get() = nameTextField.text

    private val homePathTextField: TextFieldWithBrowseButton = pathTextField(
        RsSdkType.getInstance().homeChooserDescriptor,
        disposable,
        "Select Toolchain Path"
    ) { sdkAdditionalDataPanel.notifySdkHomeChanged(homePath) }
    val homePath: String? get() = homePathTextField.text.takeIf { it.isNotBlank() }

    private val sdkAdditionalDataPanel: RsSdkAdditionalDataPanel = RsSdkAdditionalDataPanel()
    var additionalData: RsSdkAdditionalData?
        get() = sdkAdditionalDataPanel.data
        private set(value) {
            sdkAdditionalDataPanel.data = value
        }

    init {
        title = "Edit Rust Toolchain"
        nameTextField.text = sdk.name
        homePathTextField.text = sdk.homePath
        additionalData = sdk.sdkAdditionalData as? RsSdkAdditionalData

        nameTextField.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                val nameError = nameValidator(name)
                setErrorText(nameError, nameTextField)
                isOKActionEnabled = nameError == null
            }
        })

        init()
    }

    override fun createCenterPanel(): JComponent = panel {
        row("Toolchain name:") { nameTextField() }
        row("Toolchain path:") { homePathTextField() }
        sdkAdditionalDataPanel.attachTo(this)
    }.apply { preferredSize = Dimension(600, height) }

    override fun getPreferredFocusedComponent(): JComponent = nameTextField

    override fun doValidateAll(): List<ValidationInfo> = listOfNotNull(
        validateToolchainPathField(homePathTextField),
        validateSdkAdditionalDataPanel(sdkAdditionalDataPanel)
    )

    override fun dispose() {
        Disposer.dispose(sdkAdditionalDataPanel)
        super.dispose()
    }

    companion object {
        private fun validateToolchainPathField(pathField: TextFieldWithBrowseButton): ValidationInfo? {
            if (pathField.text.isNotBlank()) return null
            return ValidationInfo("Toolchain path is empty", pathField)
        }
    }
}
