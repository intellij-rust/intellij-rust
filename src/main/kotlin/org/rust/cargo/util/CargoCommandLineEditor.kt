/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.util

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.ui.EditorTextField
import com.intellij.ui.ExpandableEditorSupport
import com.intellij.ui.TextAccessor
import com.intellij.ui.components.fields.ExpandableSupport
import com.intellij.util.Function
import com.intellij.util.textCompletion.TextFieldWithCompletion
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.workspace.CargoWorkspace
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class CargoCommandLineEditor(
    private val project: Project,
    private val workspaceGetter: () -> CargoWorkspace?
) : JPanel(BorderLayout()), TextAccessor {

    constructor(project: Project, workspace: CargoWorkspace?)
        : this(project, { workspace })

    private val textField = createTextField("")
    val preferredFocusedComponent: JComponent = textField

    init {
        ExpandableEditorSupportWithCustomPopup(textField, this::createTextField)
        add(textField, BorderLayout.CENTER)
    }

    override fun setText(text: String?) {
        textField.setText(text)
    }

    override fun getText(): String = textField.text

    fun setPreferredWidth(width: Int) {
        textField.setPreferredWidth(width)
    }

    fun attachLabel(label: JLabel) {
        label.labelFor = textField
    }

    private fun createTextField(value: String): TextFieldWithCompletion =
        TextFieldWithCompletion(
            project,
            CargoCommandCompletionProvider(project.cargoProjects, workspaceGetter),
            value, true, false, false
        )
}

private class ExpandableEditorSupportWithCustomPopup(
    field: EditorTextField,
    private val createPopup: (text: String) -> EditorTextField
) : ExpandableEditorSupport(field) {
    override fun prepare(field: EditorTextField, onShow: Function<in String, String>): Content {
        val popup = createPopup(onShow.`fun`(field.text))
        val background = field.background

        popup.background = background
        popup.setOneLineMode(false)
        popup.preferredSize = Dimension(field.width, 5 * field.height)
        popup.addSettingsProvider { editor ->
            initPopupEditor(editor, background)
            copyCaretPosition(editor, field.editor)
        }

        return object : ExpandableSupport.Content {
            override fun getContentComponent(): JComponent = popup
            override fun getFocusableComponent(): JComponent = popup
            override fun cancel(onHide: Function<in String, String>) {
                field.text = onHide.`fun`(popup.text)
                val editor = field.editor
                if (editor != null) copyCaretPosition(editor, popup.editor)
                if (editor is EditorEx) updateFieldFolding((editor as EditorEx?)!!)
            }
        }
    }

    companion object {
        private fun copyCaretPosition(destination: Editor, source: Editor?) {
            if (source == null) return  // unexpected
            try {
                destination.caretModel.caretsAndSelections = source.caretModel.caretsAndSelections
            } catch (ignored: IllegalArgumentException) {
            }
        }
    }
}
