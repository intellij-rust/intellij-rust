/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.console

import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope

class CargoConsoleView(project: Project, searchScope: GlobalSearchScope, viewer: Boolean, usePredefinedMessageFilter: Boolean)
    : ConsoleViewImpl(project, searchScope, viewer, usePredefinedMessageFilter) {
    private var hasErrors = false

    override fun doCreateConsoleEditor(): EditorEx {
        val editor = super.doCreateConsoleEditor()
        editor.document.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                hasErrors = hasErrors || "error:" in event.newFragment
            }
        })
        return editor
    }

    override fun scrollToEnd() {
        if (hasErrors) return
        super.scrollToEnd()
    }
}
