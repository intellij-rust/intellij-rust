/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.refactoring.extractFunction

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.psi.PsiFile
import com.intellij.refactoring.ui.NameSuggestionsField
import com.intellij.ui.components.dialog
import java.awt.Dimension
import java.awt.GridLayout
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JPanel

fun extractFunctionDialog(
    project: Project,
    file: PsiFile,
    config: RsExtractFunctionConfig
) {
    val contentPanel = JPanel(GridLayout(2, 2))
    val functionNameLabel = JLabel()
    val functionNameField = NameSuggestionsField(project)
    val visibilityLabel = JLabel()
    val visibilityBox = JComboBox<String>()

    contentPanel.size = Dimension(522, 396)
    functionNameLabel.text = "Name:"

    visibilityLabel.text = "Visibility:"
    with(visibilityBox) {
        addItem("Public")
        addItem("Private")
    }
    visibilityBox.selectedItem = "Private"

    with(contentPanel) {
        add(visibilityLabel)
        add(functionNameLabel)
        add(visibilityBox)
        add(functionNameField)
    }

    dialog(
        "Extract Function",
        contentPanel,
        resizable = false,
        focusedComponent = functionNameField,
        okActionEnabled = true,
        project = project,
        parent = null,
        errorText = null,
        modality = DialogWrapper.IdeModalityType.IDE
    ) {
        config.name = functionNameField.enteredName
        config.visibilityLevelPublic = visibilityBox.selectedItem == "Public"
        RsExtractFunctionHandlerAction(
            project,
            file,
            config
        ).execute()
        true
    }.show()
}
