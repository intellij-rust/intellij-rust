/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.codeInspection.LocalQuickFix
import org.rust.ide.inspections.fixes.RemoveTypeArguments
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.RsGenericDeclaration
import org.rust.lang.utils.RsDiagnostic
import org.rust.lang.utils.addToHolder

/**
 * Inspection that detects the E0107 error.
 */
class RsWrongTypeArgumentsNumberInspection : RsLocalInspectionTool() {
    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean) =
        object : RsVisitor() {
            override fun visitBaseType(type: RsBaseType) {
                // Don't apply generic declaration checks to Fn-traits and `Self`
                if (type.path?.valueParameterList != null) return
                if (type.path?.cself != null) return
                checkMethod(holder, type)
            }

            override fun visitCallExpr(o: RsCallExpr) = checkMethod(holder, o)
            override fun visitMethodCall(o: RsMethodCall) = checkMethod(holder, o)
        }

    private fun checkMethod(holder: RsProblemsHolder, o: RsElement) {
        val (actualArguments, declaration) = when (o) {
            is RsMethodCall -> o.typeArgumentList to o.reference.resolve()
            is RsCallExpr ->
                (o.expr as? RsPathExpr)?.path?.typeArgumentList to (o.expr as? RsPathExpr)?.path?.reference?.resolve()
            is RsBaseType -> o.path?.typeArgumentList to o.path?.reference?.resolve()
            else -> return
        }
        if (declaration !is RsGenericDeclaration) return
        val actualArgs = actualArguments?.typeReferenceList?.size ?: 0

        val expectedRequiredParams = declaration.typeParameterList?.typeParameterList?.filter { it.typeReference == null }?.size
            ?: 0
        val expectedTotalParams = declaration.typeParameterList?.typeParameterList?.size ?: 0

        if (actualArgs == expectedTotalParams) return

        val errorText = when (o) {
            is RsBaseType -> checkBaseType(actualArgs, expectedRequiredParams, expectedTotalParams)
            is RsMethodCall, is RsCallExpr -> checkFunctionCall(actualArgs, expectedRequiredParams, expectedTotalParams)
            else -> null
        } ?: return

        val problemText = "Wrong number of type arguments: expected ${errorText}, found $actualArgs"
        val fixes = getFixes(actualArgs, expectedTotalParams)

        RsDiagnostic.WrongNumberOfTypeArguments(o, problemText, fixes).addToHolder(holder)
    }
}

private fun checkBaseType(actualArgs: Int, expectedRequiredParams: Int, expectedTotalParams: Int): String? {
    return when {
        actualArgs > expectedTotalParams ->
            if (expectedRequiredParams != expectedTotalParams) "at most $expectedTotalParams" else "$expectedTotalParams"
        actualArgs < expectedRequiredParams ->
            if (expectedRequiredParams != expectedTotalParams) "at least $expectedRequiredParams" else "$expectedTotalParams"
        else -> null
    }
}

private fun checkFunctionCall(actualArgs: Int, expectedRequiredParams: Int, expectedTotalParams: Int): String? {
    return when {
        actualArgs > expectedTotalParams ->
            if (expectedRequiredParams != expectedTotalParams) "at most $expectedTotalParams" else "$expectedTotalParams"
        actualArgs in 1 until expectedTotalParams ->
            if (expectedRequiredParams != expectedTotalParams) "at least $expectedRequiredParams" else "$expectedTotalParams"
        else -> null
    }
}

private fun getFixes(actualArgs: Int, expectedTotalParams: Int): List<LocalQuickFix> =
    if (actualArgs > expectedTotalParams)
        listOf(RemoveTypeArguments(expectedTotalParams, actualArgs))
    else
        emptyList()
