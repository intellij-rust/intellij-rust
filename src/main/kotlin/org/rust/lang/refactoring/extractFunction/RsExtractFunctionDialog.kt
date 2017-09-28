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

private fun extractConfig(
    config: RsExtractFunctionConfig,
    functionName: NameSuggestionsField,
    visibilityBox: ComboBox<String>
): RsExtractFunctionConfig {
    config.name = functionName.enteredName
    config.visibilityLevelPublic = visibilityBox.selectedItem == "Public"
    return config
}

private fun genSignature(config: RsExtractFunctionConfig): String {
    var signature = "fn ${config.name}()"
    if (config.returnType != null) {
        signature += " -> ${config.returnType}"
    }
    if (config.visibilityLevelPublic) {
        signature = "pub " + signature
    }
    signature += "{\n${config.elements.joinToString(separator = "\n", transform = { it.text })}"
    if (config.returnBindingName != null) {
        signature += "\n${config.returnBindingName}"
    }
    return signature + "\n}"
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
    val signature = genSignature(config)
    val signatureComponent = RsSignatureComponent(signature, project)
    signatureComponent.minimumSize = Dimension(300, 30)

    visibilityBox.addActionListener {
        val config = extractConfig(config, functionNameField, visibilityBox)
        val signature = genSignature(config)
        signatureComponent.setSignature(signature)
    }
    functionNameField.addDataChangedListener {
        val config = extractConfig(config, functionNameField, visibilityBox)
        val signature = genSignature(config)
        signatureComponent.setSignature(signature)
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
        val config = extractConfig(config, functionNameField, visibilityBox)
        RsExtractFunctionHandlerAction(project, file, config).execute()
        true
    }.show()
}
