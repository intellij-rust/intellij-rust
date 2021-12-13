/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.codeInspection.LocalQuickFix
import org.rust.ide.inspections.fixes.AddTypeArguments
import org.rust.ide.inspections.fixes.RemoveTypeArguments
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.RsGenericDeclaration
import org.rust.lang.core.psi.ext.constParameters
import org.rust.lang.core.psi.ext.typeParameters
import org.rust.lang.utils.RsDiagnostic
import org.rust.lang.utils.addToHolder

/**
 * Inspection that detects the E0107 error.
 */
class RsWrongGenericArgumentsNumberInspection : RsLocalInspectionTool() {
    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean): RsVisitor =
        object : RsVisitor() {
            override fun visitMethodCall(methodCall: RsMethodCall) = checkTypeArguments(holder, methodCall)
            override fun visitPath(path: RsPath) {
                if (!isPathValid(path)) return
                checkTypeArguments(holder, path)
            }
        }

    // Don't apply generic declaration checks to Fn-traits and `Self`
    private fun isPathValid(path: RsPath?): Boolean = path?.valueParameterList == null && path?.cself == null

    private fun checkTypeArguments(holder: RsProblemsHolder, element: RsElement) {
        val (actualArguments, declaration) = getTypeArgumentsAndDeclaration(element) ?: return

        val actualTypeArgs = actualArguments?.typeReferenceList?.size ?: 0
        val expectedTotalTypeParams = declaration.typeParameters.size
        val expectedRequiredTypeParams = declaration.typeParameters.count { it.typeReference == null }

        val actualConstArgs = actualArguments?.exprList?.size ?: 0
        val expectedTotalConstParams = declaration.constParameters.size

        val actualArgs = actualTypeArgs + actualConstArgs
        val expectedTotalParams = expectedTotalTypeParams + expectedTotalConstParams
        val maxExpectedRequiredParams = expectedRequiredTypeParams + expectedTotalConstParams
        val minExpectedRequiredParams = when (element.parent) {
            is RsBaseType, is RsTraitRef -> 0
            else -> 1
        }

        if (actualArgs == expectedTotalParams) return

        val errorText = when {
            actualArgs > expectedTotalParams ->
                if (maxExpectedRequiredParams != expectedTotalParams) "at most $expectedTotalParams" else "$expectedTotalParams"
            actualArgs in minExpectedRequiredParams until maxExpectedRequiredParams ->
                if (maxExpectedRequiredParams != expectedTotalParams) "at least $maxExpectedRequiredParams" else "$expectedTotalParams"
            else -> return
        }

        val haveTypeParams = expectedTotalTypeParams > 0 || actualTypeArgs > 0
        val haveConstParams = expectedTotalConstParams > 0 || actualConstArgs > 0
        val argumentName = when {
            haveTypeParams && !haveConstParams -> "type"
            !haveTypeParams && haveConstParams -> "const"
            else -> "generic"
        }

        val problemText = "Wrong number of $argumentName arguments: expected $errorText, found $actualArgs"
        val fixes = getFixes(element, actualTypeArgs, expectedTotalTypeParams)

        RsDiagnostic.WrongNumberOfTypeArguments(element, problemText, fixes).addToHolder(holder)
    }
}

private fun getFixes(element: RsElement, actualArgs: Int, expectedTotalParams: Int): List<LocalQuickFix> =
    when {
        actualArgs > expectedTotalParams -> listOf(RemoveTypeArguments(expectedTotalParams, actualArgs))
        actualArgs < expectedTotalParams -> listOf(AddTypeArguments(element))
        else -> emptyList()
    }

fun getTypeArgumentsAndDeclaration(pathOrMethodCall: RsElement): Pair<RsTypeArgumentList?, RsGenericDeclaration>? {
    val (arguments, resolved) = when (pathOrMethodCall) {
        is RsPath -> pathOrMethodCall.typeArgumentList to pathOrMethodCall.reference?.resolve()
        is RsMethodCall -> pathOrMethodCall.typeArgumentList to pathOrMethodCall.reference.resolve()
        else -> return null
    }
    if (resolved !is RsGenericDeclaration) return null
    return arguments to resolved
}
