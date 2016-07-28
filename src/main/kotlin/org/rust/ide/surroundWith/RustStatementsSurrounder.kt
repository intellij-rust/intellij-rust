package org.rust.ide.surroundWith

import com.intellij.lang.surroundWith.Surrounder
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

abstract class RustStatementsSurrounder : Surrounder {
    abstract fun surroundStatements(
        project: Project,
        editor: Editor,
        container: PsiElement,
        statements: Array<out PsiElement>
    ): TextRange?

    override fun isApplicable(elements: Array<out PsiElement>): Boolean = elements.size > 0

    override fun surroundElements(project: Project, editor: Editor, elements: Array<out PsiElement>): TextRange? {
        require(elements.size > 0)
        val container = elements[0].parent ?: throw IllegalStateException()
        return surroundStatements(project, editor, container, elements)
    }
}
