/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.rust.ide.inspections.fixes.RemoveTypeParameter
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.RsGenericDeclaration

/** Inspection that detects E0243/E0244/E0087/E0089/E0035/E0036 errors. */
class RsWrongTypeParametersNumberInspection : RsLocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : RsVisitor() {

            override fun visitBaseType(type: RsBaseType) {
                // Don't apply generic declaration checks to Fn-traits and `Self`
                if (type.path?.valueParameterList != null) return
                if (type.path?.cself != null) return
                checkMethod(holder, type)
            }

            override fun visitCallExpr(callExpr: RsCallExpr) = checkMethod(holder, callExpr)

            override fun visitMethodCall(methodCall: RsMethodCall) = checkMethod(holder, methodCall)
        }

    private fun checkMethod(holder: ProblemsHolder, element: RsElement) {
        val (actualArguments, declaration) = when (element) {
            is RsMethodCall ->
                element.typeArgumentList to element.reference.resolve()

            is RsCallExpr ->
                (element.expr as? RsPathExpr)?.path?.typeArgumentList to
                    (element.expr as? RsPathExpr)?.path?.reference?.resolve()

            is RsBaseType ->
                element.path?.typeArgumentList to element.path?.reference?.resolve()

            else -> return
        }
        if (declaration !is RsGenericDeclaration) return
        val argumentsCount = actualArguments?.typeReferenceList?.size ?: 0

        val expectedRequiredParams = declaration.typeParameterList?.typeParameterList
            ?.filter { it.typeReference == null }?.size ?: 0
        val expectedTotalParams = declaration.typeParameterList?.typeParameterList?.size ?: 0

        val data = when (element) {
            is RsBaseType -> checkBaseType(argumentsCount, expectedRequiredParams, expectedTotalParams)
            is RsMethodCall -> checkMethodCall(argumentsCount, expectedRequiredParams, expectedTotalParams)
            is RsCallExpr -> checkCallExpr(argumentsCount, expectedRequiredParams, expectedTotalParams)
            else -> null
        } ?: return

        val problemText = "Wrong number of type parameters: expected ${data.expectedText}," +
            " found $argumentsCount [${data.code}]"
        if (data.fix) {
            holder.registerProblem(element, problemText, RemoveTypeParameter())
        } else {
            holder.registerProblem(element, problemText)
        }
    }

    data class ProblemData(val expectedText: String, val code: String, val fix: Boolean)

    private fun checkBaseType(actualArgs: Int, expectedRequiredParams: Int, expectedTotalParams: Int): ProblemData? {
        val (code, expectedText) = when {
            actualArgs < expectedRequiredParams ->
                ("E0243" to if (expectedRequiredParams != expectedTotalParams) {
                    "at least $expectedRequiredParams"
                } else {
                    "$expectedTotalParams"
                })
            actualArgs > expectedTotalParams ->
                ("E0244" to if (expectedRequiredParams != expectedTotalParams) {
                    "at most $expectedTotalParams"
                } else {
                    "$expectedTotalParams"
                })
            else -> null
        } ?: return null
        return ProblemData(expectedText, code, expectedTotalParams == 0)
    }

    private fun checkMethodCall(actualArgs: Int, expectedRequiredParams: Int, expectedTotalParams: Int): ProblemData? {
        val (code, expectedText) = when {
            actualArgs != 0 && expectedTotalParams == 0 ->
                ("E0035" to if (expectedRequiredParams != expectedTotalParams) {
                    "at most $expectedRequiredParams"
                } else {
                    "$expectedTotalParams"
                })
            actualArgs > expectedTotalParams ->
                ("E0036" to if (expectedRequiredParams != expectedTotalParams) {
                    "at most $expectedTotalParams"
                } else {
                    "$expectedTotalParams"
                })
            else -> null
        } ?: return null
        return ProblemData(expectedText, code, expectedTotalParams == 0)
    }

    private fun checkCallExpr(actualArgs: Int, expectedRequiredParams: Int, expectedTotalParams: Int): ProblemData? {
        val (code, expectedText) = when {
            actualArgs > expectedTotalParams ->
                ("E0087" to if (expectedRequiredParams != expectedTotalParams) {
                    "at most $expectedTotalParams"
                } else {
                    "$expectedTotalParams"
                })
            else -> null
        } ?: return null
        return ProblemData(expectedText, code, expectedTotalParams == 0)
    }
}
