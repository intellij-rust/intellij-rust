package org.rust.ide.surroundWith.expression

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustAssertMacroElement
import org.rust.lang.core.psi.RustElementFactory
import org.rust.lang.core.psi.RustExprElement
import org.rust.lang.core.psi.RustMacroExprElement
import org.rust.lang.core.psi.impl.RustExprStmtElementImpl
import org.rust.lang.core.types.RustBooleanType
import org.rust.lang.core.types.util.resolvedType

class RustWithAssertSurrounder : RustExpressionSurrounderBase<RustExprStmtElementImpl>() {
    override fun getTemplateDescription(): String = "assert!(expr);"

    override fun createTemplate(project: Project): RustExprStmtElementImpl =
        RustElementFactory.createStatement(project, "assert!(a);") as RustExprStmtElementImpl

    override fun getWrappedExpression(expression: RustExprStmtElementImpl): RustExprElement =
        ((expression.expr as RustMacroExprElement).macro as RustAssertMacroElement).assertMacroArgs!!.expr

    override fun isApplicable(expression: RustExprElement): Boolean =
        expression.resolvedType == RustBooleanType

    override fun getSelectionRange(expression: PsiElement): TextRange {
        val offset = expression.textRange.endOffset
        return TextRange.from(offset, 0)
    }
}

