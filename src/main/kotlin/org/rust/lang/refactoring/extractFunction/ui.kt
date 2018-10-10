/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.refactoring.extractFunction

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.refactoring.ui.MethodSignatureComponent
import com.intellij.refactoring.ui.NameSuggestionsField
import com.intellij.ui.components.dialog
import com.intellij.ui.layout.panel
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.TestOnly
import org.rust.lang.RsFileType
import org.rust.openapiext.isUnitTestMode

private var MOCK: ExtractFunctionUi? = null

fun extractFunctionDialog(
    project: Project,
    config: RsExtractFunctionConfig,
    callback: () -> Unit

) {
    val extractFunctionUi = if (isUnitTestMode) {
        MOCK ?: error("You should set mock ui via `withMockExtractFunctionUi`")
    } else {
        DialogExtractFunctionUi(project)
    }
    extractFunctionUi.extract(config, callback)
}

@TestOnly
fun withMockExtractFunctionUi(mockUi: ExtractFunctionUi, action: () -> Unit) {
    MOCK = mockUi
    try {
        action()
    } finally {
        MOCK = null
    }
}

interface ExtractFunctionUi {
    fun extract(config: RsExtractFunctionConfig, callback: () -> Unit)
}

private class DialogExtractFunctionUi(
    private val project: Project
) : ExtractFunctionUi {

    override fun extract(config: RsExtractFunctionConfig, callback: () -> Unit) {
        val functionNameField = NameSuggestionsField(project)

        val visibilityBox = ComboBox<String>()
        with(visibilityBox) {
            addItem("Public")
            addItem("Private")
        }
        visibilityBox.selectedItem = "Private"
        val signatureComponent = RsSignatureComponent(config.signature, project)
        signatureComponent.minimumSize = JBUI.size(300, 30)

        visibilityBox.addActionListener {
            updateConfig(config, functionNameField, visibilityBox)
            signatureComponent.setSignature(config.signature)
        }
        functionNameField.addDataChangedListener {
            updateConfig(config, functionNameField, visibilityBox)
            signatureComponent.setSignature(config.signature)
        }

        val panel = panel {
            row("Name:") { functionNameField() }
            row("Visibility:") { visibilityBox() }
            row("Signature:") { signatureComponent() }
        }

        dialog(
            "Extract Function",
            panel,
            resizable = false,
            focusedComponent = functionNameField,
            okActionEnabled = true,
            project = project,
            parent = null,
            errorText = null,
            modality = DialogWrapper.IdeModalityType.IDE
        ) {
            updateConfig(config, functionNameField, visibilityBox)
            callback()
            emptyList()
        }.show()
    }

    private fun updateConfig(
        config: RsExtractFunctionConfig,
        functionName: NameSuggestionsField,
        visibilityBox: ComboBox<String>
    ) {
        config.name = functionName.enteredName
        config.visibilityLevelPublic = visibilityBox.selectedItem == "Public"
    }
}

private class RsSignatureComponent(
    signature: String, project: Project
) : MethodSignatureComponent(signature, project, RsFileType) {
    private val myFileName = "dummy." + RsFileType.defaultExtension

    override fun getFileName(): String = myFileName
}
