/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.suggested

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.refactoring.suggested.SuggestedRefactoringState
import com.intellij.refactoring.suggested.SuggestedRefactoringStateChanges
import com.intellij.refactoring.suggested.SuggestedRefactoringSupport
import org.rust.lang.core.psi.RsElementTypes.COLON
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsPatIdent
import org.rust.lang.core.psi.ext.childrenWithLeaves
import org.rust.lang.core.psi.ext.elementType
import org.rust.lang.core.psi.ext.rawValueParameters

data class RsSignatureAdditionalData(val isFunction: Boolean) : SuggestedRefactoringSupport.SignatureAdditionalData
data class RsParameterAdditionalData(val isPatIdent: Boolean) : SuggestedRefactoringSupport.ParameterAdditionalData

class RsSuggestedRefactoringStateChanges(
    support: RsSuggestedRefactoringSupport
) : SuggestedRefactoringStateChanges(support) {
    override fun parameterMarkerRanges(anchor: PsiElement): List<TextRange?> {
        val function = anchor as? RsFunction ?: return emptyList()
        val parameterColons: List<PsiElement?> = function.rawValueParameters.map { parameter ->
            parameter.childrenWithLeaves.firstOrNull { it.elementType == COLON }
        }
        return parameterColons.mapNotNull { it?.textRange }
    }

    override fun signature(
        anchor: PsiElement,
        prevState: SuggestedRefactoringState?
    ): SuggestedRefactoringSupport.Signature? {
        val function = anchor as? RsFunction
        if (function != null) {
            val name = function.name ?: return null
            val returnType = function.retType?.text

            val functionParameters = function.rawValueParameters
            // Ignore signatures with invalid parameters
            if (functionParameters.any { it.pat == null || it.typeReference == null }) return null

            val parameters = functionParameters.map { param ->
                SuggestedRefactoringSupport.Parameter(
                    Any(),
                    param.pat?.text.orEmpty(),
                    param.typeReference?.text.orEmpty(),
                    RsParameterAdditionalData(param.pat is RsPatIdent)
                )
            }
            val signature = SuggestedRefactoringSupport.Signature.create(
                name, returnType, parameters, RsSignatureAdditionalData(true)
            ) ?: return null
            return if (prevState == null) {
                signature
            } else {
                matchParametersWithPrevState(signature, anchor, prevState)
            }
        } else {
            val name = (anchor as? PsiNamedElement)?.name ?: return null
            return SuggestedRefactoringSupport.Signature.create(
                name, null, emptyList(), RsSignatureAdditionalData(false)
            )
        }
    }
}
