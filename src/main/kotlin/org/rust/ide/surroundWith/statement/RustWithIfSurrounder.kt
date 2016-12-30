package org.rust.ide.surroundWith.statement

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustBlockElement
import org.rust.lang.core.psi.RustIfExprElement
import org.rust.lang.core.psi.RustPsiFactory

class RustWithIfSurrounder : RustStatementsSurrounderBase() {
    override fun getTemplateDescription(): String = "if { }"

    override fun createTemplate(project: Project): Pair<PsiElement, RustBlockElement> {
        val i = RustPsiFactory(project).createExpression("if a {\n}") as RustIfExprElement
        return i to i.block!!
    }

    override val shouldRemoveExpr = true

    override fun getExprForRemove(expression: PsiElement): PsiElement =
        (expression as RustIfExprElement).condition.expr
}
