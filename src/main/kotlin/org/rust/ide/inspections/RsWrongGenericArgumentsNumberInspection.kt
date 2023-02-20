/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.codeInspection.LocalQuickFix
import org.rust.ide.inspections.fixes.AddGenericArguments
import org.rust.ide.inspections.fixes.RemoveGenericArguments
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.utils.RsDiagnostic
import org.rust.lang.utils.addToHolder
import org.rust.openapiext.createSmartPointer

/**
 * Inspection that detects the E0107 error.
 */
class RsWrongGenericArgumentsNumberInspection : RsLocalInspectionTool() {
    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean): RsVisitor =
        object : RsWithMacrosInspectionVisitor() {
            override fun visitMethodCall(methodCall: RsMethodCall) = checkTypeArguments(holder, methodCall)
            override fun visitPath(path: RsPath) {
                if (!isPathValid(path)) return
                checkTypeArguments(holder, path)
            }
        }

    // Don't apply generic declaration checks to Fn-traits and `Self`
    private fun isPathValid(path: RsPath?): Boolean = path?.valueParameterList == null && path?.cself == null

    private fun checkTypeArguments(holder: RsProblemsHolder, element: RsMethodOrPath) {
        val (actualArguments, declaration) = getTypeArgumentsAndDeclaration(element) ?: return

        val actualTypeArgs = actualArguments?.typeArguments.orEmpty().size
        val actualConstArgs = actualArguments?.constArguments.orEmpty().size
        val actualArgs = actualTypeArgs + actualConstArgs

        val expectedTotalTypeParams = declaration.typeParameters.size
        val expectedTotalConstParams = declaration.constParameters.size
        val expectedTotalParams = expectedTotalTypeParams + expectedTotalConstParams

        if (actualArgs == expectedTotalParams) return

        val expectedRequiredParams = declaration.requiredGenericParameters.size
        val minRequiredParams = when (element.parent) {
            is RsPathType, is RsTraitRef -> 0
            else -> 1
        }

        val errorText = when {
            actualArgs > expectedTotalParams ->
                if (expectedRequiredParams != expectedTotalParams) "at most $expectedTotalParams" else "$expectedTotalParams"
            actualArgs in minRequiredParams until expectedRequiredParams ->
                if (expectedRequiredParams != expectedTotalParams) "at least $expectedRequiredParams" else "$expectedTotalParams"
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
        val fixes = getFixes(declaration, element, actualArgs, expectedTotalParams)

        RsDiagnostic.WrongNumberOfGenericArguments(element, problemText, fixes).addToHolder(holder)
    }
}

private fun getFixes(
    declaration: RsGenericDeclaration,
    element: RsElement,
    actualArgs: Int,
    expectedTotalParams: Int
): List<LocalQuickFix> = when {
    actualArgs > expectedTotalParams -> listOf(RemoveGenericArguments(expectedTotalParams, actualArgs))
    actualArgs < expectedTotalParams -> listOf(AddGenericArguments(declaration.createSmartPointer(), element))
    else -> emptyList()
}

fun getTypeArgumentsAndDeclaration(pathOrMethodCall: RsMethodOrPath): Pair<RsTypeArgumentList?, RsGenericDeclaration>? {
    val (arguments, resolved) = pathOrMethodCall.typeArgumentList to pathOrMethodCall.reference?.resolve()
    if (resolved !is RsGenericDeclaration) return null
    return arguments to resolved
}
