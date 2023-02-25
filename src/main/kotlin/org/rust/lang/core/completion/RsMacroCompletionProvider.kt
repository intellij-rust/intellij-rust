/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionUtil.DUMMY_IDENTIFIER_TRIMMED
import com.intellij.openapi.util.Key
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
import org.rust.lang.core.psi.RsExpressionCodeFragment
import org.rust.lang.core.psi.RsModItem
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.psiElement
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
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        val position = parameters.position
        val rsElement = position.ancestorStrict<RsElement>() ?: return
        val mod = rsElement.ancestorOrSelf<RsMod>() ?: return
        if (mod is RsModItem && mod.identifier == position) return

        val leftSiblings = position.leftSiblings
            .filter { it !is PsiWhiteSpace && it !is PsiComment && it !is PsiErrorElement }
            .takeWhile { it.elementType in listOf(IDENTIFIER, COLONCOLON) }
            .toList()
        if (leftSiblings.size > MAXIMUM_SUPPORTED_SEGMENTS * 2) return
        val leftSiblingsText = leftSiblings.asReversed().joinToString("") { it.text }
        if (leftSiblings.isEmpty() && position.text == DUMMY_IDENTIFIER_TRIMMED) return

        // convert to macro call so that only macros are suggested
        val text = leftSiblingsText + position.text + "!()"
        val fragment = RsExpressionCodeFragment(position.project, text, mod)
        fragment.putUserData(FORCE_OUT_OF_SCOPE_COMPLETION, true)

        val offset = leftSiblingsText.length + (parameters.offset - position.startOffset)
        val element = fragment.findElementAt(offset) ?: return
        rerunCompletion(parameters.withPosition(element, offset), result)
    }

    override val elementPattern: ElementPattern<PsiElement>
        get() {
            val incompleteItem = psiElement<RsItemElement>().withLastChild(RsPsiPattern.error)
            return psiElement(IDENTIFIER)
                .withLanguage(RsLanguage)
                .andNot(psiElement().withPrevSiblingSkipping(RsPsiPattern.whitespace, incompleteItem))
                .withParent(psiElement<RsMod>())
        }

    private const val MAXIMUM_SUPPORTED_SEGMENTS: Int = 10
}

val FORCE_OUT_OF_SCOPE_COMPLETION: Key<Boolean> = Key.create("FORCE_OUT_OF_SCOPE_COMPLETION")
