/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.parentDotExpr

class RsCStringPointerInspection : RsLocalInspectionTool() {

    override fun getDisplayName(): String = "Unsafe CString pointer"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : RsVisitor() {
            override fun visitMethodCall(asPtrCall: RsMethodCall) {
                if (asPtrCall.referenceName != "as_ptr") return

                val unwrapCall = (asPtrCall.parentDotExpr.expr as? RsDotExpr)?.methodCall ?: return
                if (unwrapCall.referenceName != "unwrap") return

                val ctorExpr = unwrapCall.parentDotExpr.expr as? RsCallExpr ?: return
                val pathExpr = ctorExpr.expr
                if (pathExpr is RsPathExpr
                    && pathExpr.path.identifier?.text == "new"
                    && pathExpr.path.path?.identifier?.text == "CString") {
                    holder.registerProblem(asPtrCall.parentDotExpr, displayName)
                }
            }
        }
}
