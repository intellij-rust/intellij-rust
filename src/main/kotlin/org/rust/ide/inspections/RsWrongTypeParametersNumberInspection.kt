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

        val data = when(o) {
            is RsBaseType -> checkBaseType(actualArgs, expectedRequiredParams, expectedTotalParams)
            is RsMethodCallExpr -> checkMethodCallExpr(actualArgs, expectedRequiredParams, expectedTotalParams)
            is RsCallExpr -> checkCallExpr(actualArgs, expectedRequiredParams, expectedTotalParams)
            else -> null
        } ?: return
        val problemText = "Wrong number of type parameters: expected ${data.expectedText}, found $actualArgs [${data.code}]"
        if (data.fix) {
            holder.registerProblem(o, problemText, RemoveTypeParameter(o))
        } else {
            holder.registerProblem(o, problemText)
        }
    }

    data class ProblemData(val expectedText: String, val code: String, val fix: Boolean)

    private fun checkBaseType(actualArgs: Int, expectedRequiredParams: Int, expectedTotalParams: Int): ProblemData? {
        val (code, expectedText) = when {
            actualArgs < expectedRequiredParams ->
                ("E0243" to if (expectedRequiredParams != expectedTotalParams) "at least $expectedRequiredParams" else "$expectedTotalParams")
            actualArgs > expectedTotalParams ->
                ("E0244" to if (expectedRequiredParams != expectedTotalParams) "at most $expectedTotalParams" else "$expectedTotalParams")
            else -> null
        } ?: return null
        return ProblemData(expectedText, code, expectedTotalParams == 0)
    }

    private fun checkMethodCallExpr(actualArgs: Int, expectedRequiredParams: Int, expectedTotalParams: Int): ProblemData? {
        val (code, expectedText) = when {
            actualArgs != 0 && expectedTotalParams == 0 ->
                ("E0035" to if (expectedRequiredParams != expectedTotalParams) "at most $expectedRequiredParams" else "$expectedTotalParams")
            actualArgs > expectedTotalParams ->
                ("E0036" to if (expectedRequiredParams != expectedTotalParams) "at most $expectedTotalParams" else "$expectedTotalParams")
            else -> null
        } ?: return null
        return ProblemData(expectedText, code, expectedTotalParams == 0)

    }

    private fun checkCallExpr(actualArgs: Int, expectedRequiredParams: Int, expectedTotalParams: Int): ProblemData? {
        val (code, expectedText) = when {
            actualArgs > expectedTotalParams ->
                ("E0087" to if (expectedRequiredParams != expectedTotalParams) "at most $expectedTotalParams" else "$expectedTotalParams")
            else -> null
        } ?: return null
        return ProblemData(expectedText, code, expectedTotalParams == 0)

    }
}
