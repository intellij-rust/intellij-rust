package org.rust.ide.surroundWith.statement

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustBlockElement
import org.rust.lang.core.psi.RustForExprElement
import org.rust.lang.core.psi.RustPsiFactory

class RustWithForSurrounder : RustStatementsSurrounderBase() {
    override fun getTemplateDescription(): String = "for { }"

    override fun createTemplate(project: Project): Pair<PsiElement, RustBlockElement> {
        val f = RustPsiFactory(project).createExpression("for a in b {\n}") as RustForExprElement
        return f to f.block
    }

    override val shouldRemoveExpr = true

    override fun getExprForRemove(expression: PsiElement): PsiElement =
        (expression as RustForExprElement).scopedForDecl
}
