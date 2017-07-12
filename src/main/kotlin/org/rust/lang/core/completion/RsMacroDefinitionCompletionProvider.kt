/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PatternCondition
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import org.rust.lang.RsLanguage
import org.rust.lang.core.psi.RsElementTypes.IDENTIFIER
import org.rust.lang.core.psi.RsPath
import org.rust.lang.core.psi.RsPathExpr
import org.rust.lang.core.psi.ext.RsCompositeElement
import org.rust.lang.core.psi.ext.RsMod
import org.rust.lang.core.resolve.RsResolveProcessor
import org.rust.lang.core.resolve.processMacroDeclarations

object RsMacroDefinitionCompletionProvider : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext?, result: CompletionResultSet) {
        val element = parameters.position
        val suggestions = collectCompletionVariants { processMacroReferenceVariants(element, it) }.toList()
        result.addAllElements(suggestions)
    }

    val elementPattern: ElementPattern<PsiElement> get() {
        return psiElement()
            .withElementType(IDENTIFIER)
            .withParent(psiElement().with(object : PatternCondition<PsiElement?>("MacroParent") {
                override fun accepts(t: PsiElement, context: ProcessingContext?): Boolean {
                    return t is RsMod || (t is RsPath && t.parent is RsPathExpr)
                }
            }))
            .withLanguage(RsLanguage)
    }
}

fun processMacroReferenceVariants(referenceElement: PsiElement, processor: RsResolveProcessor): Boolean {
    walkUp(referenceElement, { false }) { cameFrom, scope ->
        if (processMacroDeclarations(scope, cameFrom, processor)) return@walkUp true
        false
    }
    return false
}

fun walkUp(
    start: PsiElement,
    stopAfter: (RsCompositeElement) -> Boolean,
    processor: (cameFrom: PsiElement, scope: RsCompositeElement) -> Boolean
): Boolean {

    var cameFrom: PsiElement = start
    var scope = start.context as RsCompositeElement?
    while (scope != null) {
        if (processor(cameFrom, scope)) return true
        if (stopAfter(scope)) break
        cameFrom = scope
        scope = scope.context as RsCompositeElement?
    }

    return false
}

fun collectCompletionVariants(f: (RsResolveProcessor) -> Unit): Array<LookupElement> {
    val result = mutableListOf<LookupElement>()
    f { e ->
        val lookupElement = e.element?.createLookupElement(e.name)
        if (lookupElement != null) {
            result += lookupElement
        }
        false
    }
    return result.toTypedArray()
}
