package org.rust.ide.surroundWith.statement

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustBlockElement
import org.rust.lang.core.psi.RustIfExprElement
import org.rust.lang.core.psi.RustPsiFactory

class RustWithIfSurrounder : RustStatementsSurrounderBase.BlockWithCondition() {
    override fun getTemplateDescription(): String = "if { }"

    override fun createTemplate(project: Project): Pair<PsiElement, RustBlockElement> {
        val i = RustPsiFactory(project).createExpression("if a {\n}") as RustIfExprElement
        return i to i.block!!
    }

    override fun conditionRange(expression: PsiElement): TextRange =
        (expression as RustIfExprElement).condition.textRange

}
