/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.util.ProcessingContext
import org.rust.lang.RsLanguage
import org.rust.lang.core.RsPsiPattern
import org.rust.lang.core.psi.RsElementTypes.COLONCOLON
import org.rust.lang.core.psi.RsElementTypes.IDENTIFIER
import org.rust.lang.core.psi.RsMacro
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.psi.unescapedText
import org.rust.lang.core.psiElement
import org.rust.lang.core.resolve.collectCompletionVariants
import org.rust.lang.core.resolve.createProcessor
import org.rust.lang.core.resolve.processMacroCallVariantsInScope
import org.rust.lang.core.resolve.processMacrosExportedByCrateName
import org.rust.lang.core.withPrevSiblingSkipping

/**
 * This completion provider is used only in the case of incomplete macro call outside of function or other code block.
 * ```
 * thread_lo/*caret*/
 * std::thread_lo/*caret*/
 * fn main() {}
 * ```
 */
object RsMacroCompletionProvider : RsCompletionProvider() {
    override fun addCompletions(
        parameters: CompletionParameters,
        _context: ProcessingContext,
        result: CompletionResultSet
    ) {
        val position = parameters.position
        val rsElement = position.ancestorStrict<RsElement>() ?: return
        val mod = rsElement.ancestorOrSelf<RsMod>()

        val leftSiblings = position.leftSiblings
            .filter { it !is PsiWhiteSpace && it !is PsiComment && it !is PsiErrorElement }
            .take(4).toList()
        val is2segmentPath = leftSiblings.getOrNull(0)?.elementType == COLONCOLON &&
            leftSiblings.getOrNull(1)?.elementType == IDENTIFIER && (
            leftSiblings.getOrNull(2)?.elementType != COLONCOLON ||
                leftSiblings.getOrNull(3)?.elementType != IDENTIFIER)
        val context = RsCompletionContext(isSimplePath = !is2segmentPath)

        collectCompletionVariants(result, context) { originalProcessor ->
            val processor = createProcessor(originalProcessor.name) { entry ->
                val macro = entry.element
                val hide = mod != null && macro is RsMacro && isHidden(macro, mod)
                if (!hide) originalProcessor(entry) else false
            }

            if (is2segmentPath) {
                val firstSegmentText = leftSiblings[1].unescapedText
                processMacrosExportedByCrateName(rsElement, firstSegmentText, processor)
            } else {
                processMacroCallVariantsInScope(position, processor)
            }
        }
    }

    override val elementPattern: ElementPattern<PsiElement>
        get() {
            val incompleteItem = psiElement<RsItemElement>().withLastChild(RsPsiPattern.error)
            return psiElement(IDENTIFIER)
                .withLanguage(RsLanguage)
                .andNot(psiElement().withPrevSiblingSkipping(RsPsiPattern.whitespace, incompleteItem))
                .withParent(psiElement<RsMod>())
        }

    private fun isHidden(macro: RsMacro, mod: RsMod): Boolean =
        macro.queryAttributes.isDocHidden && macro.containingMod != mod
}
