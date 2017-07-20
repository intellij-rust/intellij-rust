/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PatternCondition
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import org.rust.lang.RsLanguage
import org.rust.lang.core.psi.RsElementTypes.IDENTIFIER
import org.rust.lang.core.psi.RsMacroCall
import org.rust.lang.core.psi.RsPath
import org.rust.lang.core.psi.RsPathExpr
import org.rust.lang.core.psi.ext.RsMod
import org.rust.lang.core.resolve.processMacroCallVariants

object RsMacroDefinitionCompletionProvider : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext?, result: CompletionResultSet) {
        val element = parameters.position
        processMacroCallVariants(element) { entry ->
            entry.element?.let { el ->
                result.addElement(createLookupElement(el, entry.name))
            }
            false
        }
    }

    val elementPattern: ElementPattern<PsiElement> get() = psiElement()
        .withElementType(IDENTIFIER)
        .withParent(psiElement().with(object : PatternCondition<PsiElement?>("MacroParent") {
            override fun accepts(t: PsiElement, context: ProcessingContext?): Boolean {
                return t is RsMod || (t is RsPath && t.parent is RsPathExpr) || t is RsMacroCall
            }
        }))
        .withLanguage(RsLanguage)
}
