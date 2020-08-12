/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.newProject.ui

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBTextField
import com.intellij.ui.layout.panel
import org.rust.ide.newProject.state.RsUserTemplate
import org.rust.ide.newProject.state.RsUserTemplatesState
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.event.DocumentEvent

class AddUserTemplateDialog : DialogWrapper(null) {
    private val repoUrlField: JBTextField = JBTextField().apply {
        preferredSize = Dimension(400, 0)
        document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(event: DocumentEvent) = suggestName(event)
        })
    }

    private val nameField: JBTextField = JBTextField()

    init {
        title = "Add a custom template"
        setOKButtonText("Add")
        init()
    }

    override fun getPreferredFocusedComponent(): JComponent? = repoUrlField

    override fun createCenterPanel(): JComponent? = panel {
        row("Template URL:") {
            repoUrlField(comment = "A git repository URL to generate from")
        }
        row("Name:") {
            nameField()
        }
    }

    override fun doOKAction() {
        // TODO: Find a better way to handle dialog form validation
        if (nameField.text.isBlank()) return
        if (RsUserTemplatesState.instance.templates.any { it.name == nameField.text }) return

        RsUserTemplatesState.instance.templates.add(
            RsUserTemplate(nameField.text, repoUrlField.text)
        )

        super.doOKAction()
    }

    private fun suggestName(event: DocumentEvent) {
        if (nameField.text.isNotBlank()) return
        if (event.length != repoUrlField.text.length) return
        if (KNOWN_URL_PREFIXES.none { repoUrlField.text.startsWith(it) }) return

        nameField.text = repoUrlField.text
            .removeSuffix("/")
            .removeSuffix(".git")
            .substringAfterLast("/")
    }

    companion object {
        private val KNOWN_URL_PREFIXES = listOf("http://", "https://")
    }
}
