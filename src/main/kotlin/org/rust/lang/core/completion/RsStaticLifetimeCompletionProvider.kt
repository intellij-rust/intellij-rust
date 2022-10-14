/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.ElementPattern
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import org.rust.lang.core.RsPsiPattern

object RsStaticLifetimeCompletionProvider : RsCompletionProvider() {

    override val elementPattern: ElementPattern<PsiElement> get() = RsPsiPattern.lifetimeIdentifier

    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        result.addElement(
            LookupElementBuilder
                .create("'static")
                .bold()
        )
    }
}
