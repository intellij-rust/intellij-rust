/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */
package org.rust.ide.refactoring.introduceField

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapiext.isUnitTestMode
import com.intellij.refactoring.ui.NameSuggestionsField
import com.intellij.ui.components.dialog
import com.intellij.ui.layout.panel
import org.jetbrains.annotations.TestOnly
import org.rust.lang.RsFileType
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.RsStructItem
import org.rust.lang.core.types.ty.Ty

fun showIntroduceFieldChooser(struct: RsStructItem,
                              callback: (fields: ParameterInfo?) -> Unit) {
    val impl = if (isUnitTestMode) {
        MOCK!!
    } else {
        DialogIntroduceVariableUi(struct.project)
    }
    callback(impl.introduceField(struct))
}

private class DialogIntroduceVariableUi(
    private val project: Project
) : IntroduceFieldUi {
    override fun introduceField(struct: RsStructItem): ParameterInfo? {
        val panel = panel {
            row("Name:") { NameSuggestionsField(emptyArray(), project, RsFileType) }
        }

        // empty -> allow names
        // non-empty -> decide based on tuple/block
        val dialog = dialog(
            "Introduce field",
            panel,
            resizable = true,
//            focusedComponent = functionNameField.focusableComponent,
            okActionEnabled = false,
            project = project,
            parent = null,
            errorText = null,
            modality = DialogWrapper.IdeModalityType.IDE
        ) {
//            updateConfig(config, functionNameField, visibilityBox)
//            callback()
            emptyList()
        }

        /*
        val functionNameField = NameSuggestionsField(emptyArray(), project, RsFileType)
        functionNameField.minimumSize = JBUI.size(300, 30)

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

        val parameterPanel = ExtractFunctionParameterTablePanel(project, ::isValidRustVariableIdentifier, config) {
            signatureComponent.setSignature(config.signature)
        }

        val panel = panel {
            row("Name:") { functionNameField(CCFlags.grow) }
            row("Visibility:") { visibilityBox() }
            row("Parameters:") { parameterPanel() }
            row("Signature:") { signatureComponent(CCFlags.grow) }
        }

        val extractDialog = dialog(
            "Extract Function",
            panel,
            resizable = true,
            focusedComponent = functionNameField.focusableComponent,
            okActionEnabled = false,
            project = project,
            parent = null,
            errorText = null,
            modality = DialogWrapper.IdeModalityType.IDE
        ) {
            updateConfig(config, functionNameField, visibilityBox)
            callback()
            emptyList()
        }

        functionNameField.addDataChangedListener {
            updateConfig(config, functionNameField, visibilityBox)
            signatureComponent.setSignature(config.signature)
            extractDialog.isOKActionEnabled = isValidRustVariableIdentifier(config.name)
        }
        extractDialog.show()*/
        dialog.show()
        return null
    }
}

data class ParameterInfo(val fields: List<RsPsiFactory.BlockField>)

interface IntroduceFieldUi {
    fun introduceField(struct: RsStructItem): ParameterInfo?
}

var MOCK: IntroduceFieldUi? = null
@TestOnly
fun withMockIntroduceFieldChooser(mock: IntroduceFieldUi, f: () -> Unit) {
    MOCK = mock
    try {
        f()
    } finally {
        MOCK = null
    }
}
