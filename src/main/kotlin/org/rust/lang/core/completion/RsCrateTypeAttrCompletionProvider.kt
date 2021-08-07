/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import org.rust.ide.inspections.lints.RsUnknownCrateTypesInspection
import org.rust.lang.core.RsPsiPattern
import org.rust.lang.core.psi.RsElementTypes.STRING_LITERAL

object RsCrateTypeAttrCompletionProvider : RsCompletionProvider() {
    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        result.addAllElements(RsUnknownCrateTypesInspection.KNOWN_CRATE_TYPES.map { LookupElementBuilder.create(it) })
    }

    override val elementPattern: ElementPattern<out PsiElement>
        get() = psiElement(STRING_LITERAL).withParent(RsPsiPattern.insideCrateTypeAttrValue)
}
