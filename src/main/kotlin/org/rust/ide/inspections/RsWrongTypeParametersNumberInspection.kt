/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.codeInspection.ProblemsHolder
import org.rust.ide.inspections.fixes.RemoveTypeParameter
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsCompositeElement
import org.rust.lang.core.psi.ext.RsGenericDeclaration

/**
 * Inspection that detects E0243/E0244/E0087/E0089/E0035/E0036 errors.
 */
class RsWrongTypeParametersNumberInspection : RsLocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : RsVisitor() {
            override fun visitBaseType(type: RsBaseType) {
                // Don't apply generic declaration checks to Fn-traits and `Self`
                if (type.path?.valueParameterList != null) return
                if (type.path?.cself != null) return
                checkMethod(holder, type)
            }

            override fun visitCallExpr(o: RsCallExpr) = checkMethod(holder, o)
            override fun visitMethodCallExpr(o: RsMethodCallExpr) = checkMethod(holder, o)
        }

    private fun checkMethod(holder: ProblemsHolder, o: RsCompositeElement) {
        val actualArgs = when (o) {
            is RsMethodCallExpr -> o.typeArgumentList?.typeReferenceList?.size ?: 0
            is RsCallExpr -> (o.expr as RsPathExpr?)?.path?.typeArgumentList?.typeReferenceList?.size ?: 0
            is RsBaseType -> o.path?.typeArgumentList?.typeReferenceList?.size ?: 0
            else -> 0
        }
        val paramsDecl = when (o) {
            is RsMethodCallExpr -> o.reference.resolve() as? RsGenericDeclaration?
            is RsCallExpr -> (o.expr as RsPathExpr?)?.path?.reference?.resolve() as? RsGenericDeclaration?
            is RsBaseType -> o.path?.reference?.resolve() as? RsGenericDeclaration?
            else -> null
        } ?: return

        val expectedRequiredParams = paramsDecl.typeParameterList?.typeParameterList?.filter { it.eq == null }?.size ?: 0
        val expectedTotalParams = paramsDecl.typeParameterList?.typeParameterList?.size ?: 0

        val (code, expectedText) = when {
            actualArgs < expectedRequiredParams && o is RsBaseType ->
                ("E0243" to if (expectedRequiredParams != expectedTotalParams) "at least $expectedRequiredParams" else "$expectedTotalParams")
            actualArgs > expectedTotalParams && o is RsBaseType ->
                ("E0244" to if (expectedRequiredParams != expectedTotalParams) "at most $expectedTotalParams" else "$expectedTotalParams")
            actualArgs < expectedRequiredParams && o is RsCallExpr ->
                ("E0089" to if (expectedRequiredParams != expectedTotalParams) "at least $expectedRequiredParams" else "$expectedTotalParams")
            actualArgs > expectedTotalParams && o is RsCallExpr ->
                ("E0087" to if (expectedRequiredParams != expectedTotalParams) "at most $expectedTotalParams" else "$expectedTotalParams")
            actualArgs != 0 && expectedTotalParams == 0 && o is RsMethodCallExpr ->
                ("E0035" to if (expectedRequiredParams != expectedTotalParams) "at most $expectedRequiredParams" else "$expectedTotalParams")
            actualArgs < expectedRequiredParams && o is RsMethodCallExpr ->
                ("E0036" to if (expectedRequiredParams != expectedTotalParams) "at least $expectedRequiredParams" else "$expectedTotalParams")
            actualArgs > expectedTotalParams && o is RsMethodCallExpr ->
                ("E0036" to if (expectedRequiredParams != expectedTotalParams) "at most $expectedTotalParams" else "$expectedTotalParams")
            else -> null
        } ?: return
        if (expectedTotalParams == 0) {
            holder.registerProblem(o, "Wrong number of type parameters: expected $expectedText, found $actualArgs [$code]", RemoveTypeParameter(o))
        } else {
            holder.registerProblem(o, "Wrong number of type parameters: expected $expectedText, found $actualArgs [$code]")
        }
    }
}
