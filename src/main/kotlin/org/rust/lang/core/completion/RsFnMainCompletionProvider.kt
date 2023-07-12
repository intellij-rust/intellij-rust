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
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.prevLeaf
import com.intellij.util.ProcessingContext
import org.rust.ide.icons.RsIcons
import org.rust.lang.core.psi.RsElementTypes
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.with
import org.rust.openapiext.moveCaretToOffset

object RsFnMainCompletionProvider : RsCompletionProvider() {

    override val elementPattern: ElementPattern<PsiElement>
        get() = psiElement()
            .with("Is incomplete function declaration") { it, _ ->
                val element = it.getOriginalOrSelf()
                when (val parent = element.parent) {
                    // fn/*caret*/
                    is RsFile -> element.prevLeaf { it !is PsiWhiteSpace } !is PsiErrorElement
                    // fn m/*caret*/
                    is RsFunction ->
                        parent.block == null
                            && parent.typeParameterList == null
                            && parent.valueParameterList == null
                            && parent.context is RsFile
                    else -> false
                }
            }

    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        val element = parameters.position
        if (!shouldAddCompletion(element)) return

        val hasFnKeyword = element.getPrevNonWhitespaceSibling()?.elementType == RsElementTypes.FN
        val fnPrefix = if (hasFnKeyword) "" else "fn "

        val lookup = LookupElementBuilder
            .create("${fnPrefix}main() {\n    \n}")
            .withPresentableText("${fnPrefix}main() { ... }")
            .withIcon(RsIcons.FUNCTION)
            .withInsertHandler { insertionContext, _ ->
                val function = insertionContext.getElementOfType<RsFunction>() ?: return@withInsertHandler
                insertionContext.editor.moveCaretToOffset(function, insertionContext.tailOffset - "\n}".length)
            }
        result.addElement(
            lookup.toRsLookupElement(RsLookupElementProperties(isFullLineCompletion = true))
        )
    }

    private fun shouldAddCompletion(element: PsiElement): Boolean {
        // inside root of binary crate
        val file = element.contextualFile.originalFile as? RsFile ?: return false
        val crate = file.containingCrate
        if (file != crate.rootMod) return false
        if (!crate.kind.canHaveMainFunction) return false

        // doesn't have main function
        return file.expandedItemsCached.named["main"].orEmpty().all { it !is RsFunction }
    }
}
