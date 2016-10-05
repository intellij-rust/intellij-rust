package org.rust.ide.surroundWith.statement

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustBlockElement
import org.rust.lang.core.psi.RustBlockExprElement
import org.rust.lang.core.psi.RustElementFactory
import org.rust.lang.core.psi.RustExprStmtElement

class RustWithBlockSurrounder : RustStatementsSurrounderBase() {
    override fun getTemplateDescription(): String = "{}"

    override fun createTemplate(project: Project): PsiElement =
        checkNotNull(RustElementFactory.createStatement(project, "{\n}"))

    override fun getCodeBlock(expression: PsiElement): RustBlockElement =
        checkNotNull(((expression as RustExprStmtElement).expr as RustBlockExprElement).block)
}
