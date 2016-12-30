package org.rust.ide.surroundWith.statement

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.*

class RustWithBlockSurrounder : RustStatementsSurrounderBase.SimpleBlock<RustBlockExprElement>() {
    override fun getTemplateDescription(): String = "{}"

    override fun createTemplate(project: Project): Pair<RustBlockExprElement, RustBlockElement> {
        val block = RustPsiFactory(project).createBlockExpr("\n")
        return block to block.block!!
    }
}
