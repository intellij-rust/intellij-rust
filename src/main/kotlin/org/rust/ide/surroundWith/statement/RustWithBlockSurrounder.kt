package org.rust.ide.surroundWith.statement

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.*

class RustWithBlockSurrounder : RustStatementsSurrounderBase() {
    override fun getTemplateDescription(): String = "{}"

    override fun createTemplate(project: Project): PsiElement =
        RustPsiFactory(project).createStatement("{\n}")

    override fun getCodeBlock(expression: PsiElement): RustBlockElement =
        checkNotNull(((expression as RustExprStmtElement).expr as RustBlockExprElement).block)
}
