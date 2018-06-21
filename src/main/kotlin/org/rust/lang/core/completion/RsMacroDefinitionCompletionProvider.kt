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
import org.rust.lang.core.RsPsiPattern
import org.rust.lang.core.psi.RsElementTypes.IDENTIFIER
import org.rust.lang.core.psi.RsMacroCall
import org.rust.lang.core.psi.RsMacroDefinition
import org.rust.lang.core.psi.RsPath
import org.rust.lang.core.psi.RsPathExpr
import org.rust.lang.core.psi.ext.RsItemElement
import org.rust.lang.core.psi.ext.RsMod
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.lang.core.psi.ext.queryAttributes
import org.rust.lang.core.resolve.collectCompletionVariants
import org.rust.lang.core.resolve.processMacroCallVariants
import org.rust.lang.core.withPrevSiblingSkipping

object RsMacroDefinitionCompletionProvider : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext?, result: CompletionResultSet) {
        val mod = parameters.position.ancestorStrict<RsMod>()
        result.addAllElements(collectCompletionVariants { variantsCollector ->
            processMacroCallVariants(parameters.position) { entry ->
                val macro = entry.element
                val hide = mod != null && macro is RsMacroDefinition && isHidden(macro, mod)
                if (!hide) variantsCollector(entry) else false
            }
        }.asList())
    }

    val elementPattern: ElementPattern<PsiElement>
        get() {
            val incompleteItem = psiElement<RsItemElement>().withLastChild(RsPsiPattern.error)
            return psiElement(IDENTIFIER)
                .andNot(psiElement().withPrevSiblingSkipping(RsPsiPattern.whitespace, incompleteItem))
                .withParent(psiElement().with(object : PatternCondition<PsiElement?>("MacroParent") {
                    override fun accepts(t: PsiElement, context: ProcessingContext?): Boolean {
                        return t is RsMod || (t is RsPath && t.path == null && t.parent is RsPathExpr) || t is RsMacroCall
                    }
                }))
                .withLanguage(RsLanguage)
        }

    private fun isHidden(macro: RsMacroDefinition, mod: RsMod): Boolean =
        macro.queryAttributes.isDocHidden && macro.containingMod != mod
}
