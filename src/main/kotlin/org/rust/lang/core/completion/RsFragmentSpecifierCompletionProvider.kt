/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import org.rust.lang.core.macros.decl.FragmentKind
import org.rust.lang.core.psi.RsMacroBinding

object RsFragmentSpecifierCompletionProvider : RsCompletionProvider() {
    override val elementPattern: ElementPattern<PsiElement>
        get() = PlatformPatterns.psiElement().withParent(RsMacroBinding::class.java)

    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        FragmentKind.kinds.forEach {
            result.addElement(LookupElementBuilder.create(it).bold().withPriority(FRAGMENT_SPECIFIER_PRIORITY))
        }
    }
}
