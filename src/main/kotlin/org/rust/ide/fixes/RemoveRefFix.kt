/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.rust.RsBundle
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.RsUnaryExpr
import org.rust.lang.core.psi.ext.UnaryOperator
import org.rust.lang.core.psi.ext.operatorType


/**
 * Fix that converts the given reference to owned value.
 * @param expr An element, that represents a reference from which the first
 * symbol '&' must be removed.
 */
class RemoveRefFix private constructor(
    expr: RsUnaryExpr
) : RsQuickFixBase<RsUnaryExpr>(expr) {
    @IntentionName
    private val _text: String = when (val operatorType = expr.operatorType) {
        UnaryOperator.REF -> RsBundle.message("intention.name.remove", "&")
        UnaryOperator.REF_MUT -> RsBundle.message("intention.name.remove", "&mut")
        else -> error("Illegal operator type: expected REF or REF_MUT, got $operatorType")
    }

    override fun getText() = _text
    override fun getFamilyName() = RsBundle.message("intention.family.name.remove.reference")

    override fun invoke(project: Project, editor: Editor?, element: RsUnaryExpr) {
        element.expr?.let { element.replace(it) }
    }

    companion object {
        fun createIfCompatible(expr: RsExpr): RemoveRefFix? {
            return if(expr is RsUnaryExpr && expr.operatorType in listOf(UnaryOperator.REF, UnaryOperator.REF_MUT)) {
                RemoveRefFix(expr)
            } else {
                null
            }
        }
    }
}
