/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.suggested

import com.intellij.refactoring.suggested.SignatureChangePresentationModel.TextFragment.Leaf
import com.intellij.refactoring.suggested.SignaturePresentationBuilder
import com.intellij.refactoring.suggested.SuggestedRefactoringSupport

class RsSignaturePresentationBuilder(
    signature: SuggestedRefactoringSupport.Signature,
    otherSignature: SuggestedRefactoringSupport.Signature,
    isOldSignature: Boolean
) : SignaturePresentationBuilder(signature, otherSignature, isOldSignature) {
    override fun buildPresentation() {
        val additionalData = signature.additionalData as? RsSignatureAdditionalData ?: return

        val name = signature.name
        fragments += Leaf(name, effect(signature.name, otherSignature.name))

        if (additionalData.isFunction) {
            buildParameterList { fragments, parameter, correspondingParameter ->
                fragments += leaf(parameter.name, correspondingParameter?.name ?: parameter.name)
                fragments += Leaf(": ")
                fragments += leaf(parameter.type, correspondingParameter?.type ?: parameter.type)
            }
        }
    }
}
