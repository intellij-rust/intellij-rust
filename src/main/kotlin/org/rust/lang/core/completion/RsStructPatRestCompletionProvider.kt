/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.patterns.PsiElementPattern
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import org.rust.lang.core.psi.RsPatStruct
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.lang.core.psiElement

object RsStructPatRestCompletionProvider : RsCompletionProvider() {
    override val elementPattern: PsiElementPattern.Capture<PsiElement>
        get() =
            PlatformPatterns
                .psiElement()
                .withSuperParent(3, psiElement<RsPatStruct>())

    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        val pat = parameters.position.safeGetOriginalOrSelf().ancestorStrict<RsPatStruct>() ?: return
        if (pat.children.any { it.text == ".." }) return
        result.addElement(LookupElementBuilder.create(".."))
    }
}
