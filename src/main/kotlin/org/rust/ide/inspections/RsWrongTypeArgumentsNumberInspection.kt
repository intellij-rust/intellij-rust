/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.psi.PsiElement
import org.rust.ide.inspections.fixes.AddTypeArguments
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
                if (!isPathValid(type.path)) return
                checkTypeArguments(holder, type)
            }

            override fun visitTraitRef(trait: RsTraitRef) {
                if (!isPathValid(trait.path)) return
                checkTypeArguments(holder, trait)
            }

            override fun visitCallExpr(o: RsCallExpr) = checkTypeArguments(holder, o)
            override fun visitMethodCall(o: RsMethodCall) = checkTypeArguments(holder, o)
        }

    // Don't apply generic declaration checks to Fn-traits and `Self`
    private fun isPathValid(path: RsPath?): Boolean = path?.valueParameterList == null && path?.cself == null

    private fun checkTypeArguments(holder: RsProblemsHolder, o: RsElement) {
        val (actualArguments, declaration, startElement, endElement) = findApplicableContext(o) ?: return
        val actualArgs = actualArguments?.typeReferenceList?.size ?: 0

        val typeParameters = declaration.typeParameterList
        val expectedRequiredParams = typeParameters?.typeParameterList?.filter { it.typeReference == null }?.size ?: 0
        val expectedTotalParams = typeParameters?.typeParameterList?.size ?: 0

        if (actualArgs == expectedTotalParams) return

        val errorText = when (o) {
            is RsBaseType, is RsTraitRef -> checkTypeReference(actualArgs, expectedRequiredParams, expectedTotalParams)
            is RsMethodCall, is RsCallExpr -> checkFunctionCall(actualArgs, expectedRequiredParams, expectedTotalParams)
            else -> null
        } ?: return

        val problemText = "Wrong number of type arguments: expected ${errorText}, found $actualArgs"
        val fixes = getFixes(o, actualArgs, expectedTotalParams)

        RsDiagnostic.WrongNumberOfTypeArguments(startElement, endElement, problemText, fixes).addToHolder(holder)
    }

    companion object {
        fun findApplicableContext(element: RsElement): Context? {
            val (typeArguments, referenceElement, identifier) = if (element is RsMethodCall) {
                Triple(element.typeArgumentList, element, element.identifier)
            } else {
                val path = when (element) {
                    is RsCallExpr -> (element.expr as? RsPathExpr)?.path
                    is RsBaseType -> element.path
                    is RsTraitRef -> element.path
                    else -> return null
                } ?: return null
                Triple(path.typeArgumentList, path, path.referenceNameElement)
            }

            val declaration = referenceElement.reference?.resolve() as? RsGenericDeclaration ?: return null
            val (startElement, endElement) = if (typeArguments != null) {
                typeArguments.lt to (typeArguments.gt ?: typeArguments.lastChild)
            } else {
                identifier to null
            }
            return Context(typeArguments, declaration, startElement, endElement)
        }
    }

    data class Context(
        val typeArgs: RsTypeArgumentList?,
        val declaration: RsGenericDeclaration,
        val startElement: PsiElement,
        val endElement: PsiElement?
    )
}

private fun checkTypeReference(actualArgs: Int, expectedRequiredParams: Int, expectedTotalParams: Int): String? {
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

private fun getFixes(element: RsElement, actualArgs: Int, expectedTotalParams: Int): List<LocalQuickFix> =
    when {
        actualArgs > expectedTotalParams -> listOf(RemoveTypeArguments(element, expectedTotalParams, actualArgs))
        actualArgs < expectedTotalParams -> listOf(AddTypeArguments(element))
        else -> emptyList()
    }
