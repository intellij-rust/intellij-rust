/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.suggested

import com.intellij.psi.PsiNamedElement
import com.intellij.refactoring.suggested.*
import org.rust.ide.refactoring.changeSignature.RsChangeSignatureHandler
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsMembers
import org.rust.lang.core.psi.RsPatBinding
import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.ext.isReferenceToConstant

class RsSuggestedRefactoringAvailability(
    support: RsSuggestedRefactoringSupport
) : SuggestedRefactoringAvailability(support) {
    override fun detectAvailableRefactoring(state: SuggestedRefactoringState): SuggestedRefactoringData? {
        val function = state.declaration as? RsFunction
        if (function != null && hasComplexChanges(function, state.oldSignature, state.newSignature)) {
            return SuggestedChangeSignatureData.create(state, function.name.orEmpty())
        }
        val namedElement = state.declaration as? PsiNamedElement ?: return null
        return SuggestedRenameData(namedElement, state.oldSignature.name)
    }

    override fun shouldSuppressRefactoringForDeclaration(state: SuggestedRefactoringState): Boolean {
        return when (state.declaration) {
            is RsFunction -> {
                val function = state.restoredDeclarationCopy() as? RsFunction ?: return false
                !RsChangeSignatureHandler.isChangeSignatureAvailable(function)
            }
            is RsPatBinding -> {
                val binding = state.restoredDeclarationCopy() as? RsPatBinding ?: return false
                binding.isReferenceToConstant
            }
            else -> false
        }
    }

    private fun hasComplexChanges(
        function: RsFunction,
        oldSignature: SuggestedRefactoringSupport.Signature,
        newSignature: SuggestedRefactoringSupport.Signature
    ): Boolean {
        // Condition order is important here.
        // hasTypeChanges cannot be called if parameters were removed or added
        if (hasParameterAddedRemovedOrReordered(oldSignature, newSignature)) return true

        // Type changes can only be observed by child method signatures
        // function.owner is not used here on purpose, to avoid using resolve
        if (function.parent is RsMembers &&
            function.parent?.parent is RsTraitItem &&
            hasTypeChanges(oldSignature, newSignature)) return true

        return hasNameChanges(oldSignature, newSignature)
    }
}


/**
 * Find if any parameter with a simple identifier was renamed.
 */
private fun hasNameChanges(
    oldSignature: SuggestedRefactoringSupport.Signature,
    newSignature: SuggestedRefactoringSupport.Signature
): Boolean {
    for (parameter in newSignature.parameters) {
        val oldParam = oldSignature.parameterById(parameter.id) ?: continue
        val oldData = oldParam.additionalData as? RsParameterAdditionalData ?: continue
        val newData = parameter.additionalData as? RsParameterAdditionalData ?: continue

        if (oldData.isPatIdent && newData.isPatIdent && oldParam.name != parameter.name) {
            return true
        }
    }

    return false
}
