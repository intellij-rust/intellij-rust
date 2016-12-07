package org.rust.ide.surroundWith.statement

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustBlockElement
import org.rust.lang.core.psi.RustForExprElement
import org.rust.lang.core.psi.RustPsiFactory

class RustWithForSurrounder : RustStatementsSurrounderBase() {
    override fun getTemplateDescription(): String = "for { }"

    override fun createTemplate(project: Project): PsiElement =
        RustPsiFactory(project).createExpression("for a in b {\n}")

    override fun getCodeBlock(expression: PsiElement): RustBlockElement =
        (expression as RustForExprElement).block

    override val shouldRemoveExpr = true

    override fun getExprForRemove(expression: PsiElement): PsiElement =
        (expression as RustForExprElement).scopedForDecl
}
