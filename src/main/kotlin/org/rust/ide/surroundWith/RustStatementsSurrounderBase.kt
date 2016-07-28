package org.rust.ide.surroundWith

import com.intellij.lang.surroundWith.Surrounder
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

abstract class RustStatementsSurrounderBase : Surrounder {
    abstract fun surroundStatements(
        project: Project,
        editor: Editor,
        container: PsiElement,
        statements: Array<out PsiElement>
    ): TextRange?

    final override fun isApplicable(elements: Array<out PsiElement>): Boolean = elements.isNotEmpty()

    final override fun surroundElements(project: Project, editor: Editor, elements: Array<out PsiElement>): TextRange? {
        require(elements.isNotEmpty())
        val container = requireNotNull(elements[0].parent)
        return surroundStatements(project, editor, container, elements)
    }
}
