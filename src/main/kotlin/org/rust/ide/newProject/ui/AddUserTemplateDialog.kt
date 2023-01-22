/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.newProject.ui

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.panel
import org.rust.RsBundle
import org.rust.ide.newProject.state.RsUserTemplate
import org.rust.ide.newProject.state.RsUserTemplatesState
import org.rust.openapiext.addTextChangeListener
import org.rust.openapiext.fullWidthCell
import org.rust.openapiext.trimmedText
import javax.swing.JComponent
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentEvent.EventType

class AddUserTemplateDialog : DialogWrapper(null) {
    private val repoUrlField: JBTextField = JBTextField().apply {
        addTextChangeListener(::suggestName)
    }

    private val nameField: JBTextField = JBTextField()

    init {
        title = RsBundle.message("dialog.create.project.custom.add.template.title")
        setOKButtonText(RsBundle.message("dialog.create.project.custom.add.template.action.add"))
        init()
    }

    override fun getPreferredFocusedComponent(): JComponent = repoUrlField

    override fun createCenterPanel(): JComponent = panel {
        row(RsBundle.message("dialog.create.project.custom.add.template.url")) {
            fullWidthCell(repoUrlField)
                .comment(RsBundle.message("dialog.create.project.custom.add.template.url.description"))
        }
        row(RsBundle.message("dialog.create.project.custom.add.template.name")) {
            fullWidthCell(nameField)
        }
    }

    override fun doOKAction() {
        // TODO: Find a better way to handle dialog form validation
        val name = nameField.trimmedText
        val repoUrl = repoUrlField.trimmedText

        if (name.isBlank()) return
        if (RsUserTemplatesState.getInstance().templates.any { it.name == name }) return

        RsUserTemplatesState.getInstance().templates.add(
            RsUserTemplate(name, repoUrl)
        )

        super.doOKAction()
    }

    private fun suggestName(event: DocumentEvent) {
        // Suggest name only if the whole URL was inserted
        if (event.type == EventType.INSERT && event.length == event.document.length) {
            if (nameField.text.isNotBlank()) return

            val repoUrl = repoUrlField.trimmedText
            if (KNOWN_URL_PREFIXES.none { repoUrl.startsWith(it) }) return

            nameField.text = repoUrl
                .removeSuffix("/")
                .removeSuffix(".git")
                .substringAfterLast("/")
        }
    }

    companion object {
        private val KNOWN_URL_PREFIXES = listOf("http://", "https://")
    }
}
