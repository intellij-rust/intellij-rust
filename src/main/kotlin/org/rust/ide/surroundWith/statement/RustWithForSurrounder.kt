package org.rust.ide.surroundWith.statement

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustBlockElement
import org.rust.lang.core.psi.RustElementFactory
import org.rust.lang.core.psi.RustForExprElement

class RustWithForSurrounder : RustStatementsSurrounderBase() {
    override fun getTemplateDescription(): String = "for { }"

    override fun createTemplate(project: Project): PsiElement =
        checkNotNull(RustElementFactory.createExpression(project, "for a in b {\n}"))

    override fun getCodeBlock(expression: PsiElement): RustBlockElement =
        (expression as RustForExprElement).block

    override val shouldRemoveExpr = true

    override fun getExprForRemove(expression: PsiElement): PsiElement =
        (expression as RustForExprElement).scopedForDecl
}
