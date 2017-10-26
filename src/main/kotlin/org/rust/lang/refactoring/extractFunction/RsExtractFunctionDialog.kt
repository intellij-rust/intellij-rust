/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.refactoring.extractFunction

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.psi.PsiFile
import com.intellij.refactoring.ui.NameSuggestionsField
import com.intellij.ui.components.dialog
import com.intellij.ui.layout.panel
import java.awt.Dimension

private fun updateConfig(
    config: RsExtractFunctionConfig,
    functionName: NameSuggestionsField,
    visibilityBox: ComboBox<String>
) {
    config.name = functionName.enteredName
    config.visibilityLevelPublic = visibilityBox.selectedItem == "Public"
}

fun extractFunctionDialog(
    project: Project,
    file: PsiFile,
    config: RsExtractFunctionConfig
) {
    val functionNameField = NameSuggestionsField(project)

    val visibilityBox = ComboBox<String>()
    with(visibilityBox) {
        addItem("Public")
        addItem("Private")
    }
    visibilityBox.selectedItem = "Private"
    val signatureComponent = RsSignatureComponent(config.signature, project)
    signatureComponent.minimumSize = Dimension(300, 30)

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
        RsExtractFunctionHandlerAction(project, file, config).execute()
        true
    }.show()
}
