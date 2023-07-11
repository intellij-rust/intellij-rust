/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.suggested

import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiCodeFragment
import com.intellij.refactoring.suggested.*
import org.rust.ide.utils.import.createVirtualImportContext
import org.rust.lang.core.psi.RsExpressionCodeFragment
import org.rust.lang.core.psi.ext.RsElement

class RsSuggestedRefactoringUI : SuggestedRefactoringUI() {
    override fun createSignaturePresentationBuilder(
        signature: SuggestedRefactoringSupport.Signature,
        otherSignature: SuggestedRefactoringSupport.Signature,
        isOldSignature: Boolean
    ): SignaturePresentationBuilder = RsSignaturePresentationBuilder(signature, otherSignature, isOldSignature)

    override fun extractNewParameterData(data: SuggestedChangeSignatureData): List<NewParameterData> {
        val declaration = data.declaration as? RsElement ?: return emptyList()
        val importContext = declaration.createVirtualImportContext()

        return data.newSignature.parameters
            .filter { data.oldSignature.parameterById(it.id) == null }
            .map {
                @NlsSafe val name = it.name
                NewParameterData(
                    name,
                    RsExpressionCodeFragment(
                        importContext.project,
                        "",
                        context = importContext,
                        importTarget = importContext
                    ),
                    false
                )
            }
    }

    override fun extractValue(fragment: PsiCodeFragment): SuggestedRefactoringExecution.NewParameterValue.Expression? =
        (fragment as RsExpressionCodeFragment).expr?.let { SuggestedRefactoringExecution.NewParameterValue.Expression(it) }
}
