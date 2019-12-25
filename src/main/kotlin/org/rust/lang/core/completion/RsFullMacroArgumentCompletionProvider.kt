/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.openapiext.Testmark
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import org.rust.lang.RsLanguage
import org.rust.lang.core.macros.findExpansionElements
import org.rust.lang.core.psi.RsElementTypes.MACRO_ARGUMENT
import org.rust.lang.core.psi.RsMacroCall
import org.rust.lang.core.psi.ext.startOffset

/**
 * Provides completion inside a macro argument (e.g. `foo!(/*caret*/)`) if the macro IS expanded
 * successfully, i.e. [RsMacroCall.expansion] != null. If macro is not expanded successfully,
 * [RsPartialMacroArgumentCompletionProvider] is used.
 */
object RsFullMacroArgumentCompletionProvider : RsCompletionProvider() {
    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        val position = parameters.position
        val dstElement = position.findExpansionElements()?.firstOrNull() ?: return
        val dstOffset = dstElement.startOffset + (parameters.offset - position.startOffset)
        Testmarks.touched.hit()
        rerunCompletion(parameters.withPosition(dstElement, dstOffset), result)
    }

    override val elementPattern: ElementPattern<PsiElement>
        get() = psiElement()
            .withLanguage(RsLanguage)
            .inside(psiElement(MACRO_ARGUMENT))

    object Testmarks {
        val touched = Testmark("RsFullMacroArgumentCompletionProvider.touched")
    }
}
