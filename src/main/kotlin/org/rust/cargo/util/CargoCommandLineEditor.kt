/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.util

import com.intellij.openapi.project.Project
import com.intellij.ui.TextAccessor
import com.intellij.util.textCompletion.TextFieldWithCompletion
import org.rust.cargo.project.workspace.CargoWorkspace
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class CargoCommandLineEditor(
    project: Project,
    val workspace: CargoWorkspace?
) : JPanel(BorderLayout()), TextAccessor {

    private val textField = TextFieldWithCompletion(project, CargoCommandCompletionProvider(workspace),
        "", true, false, false
    )
    val preferredFocusedComponent: JComponent = textField


    init {
        add(textField, BorderLayout.CENTER)
    }

    override fun setText(text: String?) {
        textField.text = text
    }

    override fun getText(): String = textField.text

    fun setPreferredWidth(width: Int) {
        textField.setPreferredWidth(width)
    }

    fun attachLabel(label: JLabel) {
        label.labelFor = textField
    }
}
