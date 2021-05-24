/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.suggested

import com.intellij.psi.PsiCodeFragment
import com.intellij.refactoring.suggested.*
import org.rust.lang.core.psi.RsExpressionCodeFragment
import org.rust.lang.core.psi.ext.RsElement

class RsSuggestedRefactoringUI : SuggestedRefactoringUI() {
    override fun createSignaturePresentationBuilder(
        signature: SuggestedRefactoringSupport.Signature,
        otherSignature: SuggestedRefactoringSupport.Signature,
        isOldSignature: Boolean
    ): SignaturePresentationBuilder = RsSignaturePresentationBuilder(signature, otherSignature, isOldSignature)

    override fun extractNewParameterData(data: SuggestedChangeSignatureData): List<NewParameterData> = emptyList()

    override fun extractValue(fragment: PsiCodeFragment): SuggestedRefactoringExecution.NewParameterValue.Expression? =
        (fragment as RsExpressionCodeFragment).expr?.let { SuggestedRefactoringExecution.NewParameterValue.Expression(it) }
}
