/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import org.rust.lang.RsLanguage
import org.rust.lang.core.RsPsiPattern
import org.rust.lang.core.psi.RsEnumItem
import org.rust.lang.core.psi.RsMetaItem
import org.rust.lang.core.psi.RsPath
import org.rust.lang.core.psi.RsStructItem
import org.rust.lang.core.psi.ext.RsStructOrEnumItemElement
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.lang.core.psiElement
import org.rust.lang.core.types.ty.TyInteger

object RsReprCompletionProvider : RsCompletionProvider() {
    override val elementPattern: ElementPattern<out PsiElement>
        get() = PlatformPatterns.psiElement()
            .withLanguage(RsLanguage)
            .withParent(
                psiElement<RsPath>()
                    .withParent(
                        psiElement<RsMetaItem>()
                            .withSuperParent(
                                2,
                                RsPsiPattern.rootMetaItem("repr", psiElement<RsStructOrEnumItemElement>())
                            )
                    )
            )

    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        val owner = parameters.position.ancestorStrict<RsStructOrEnumItemElement>()
            ?: return

        fun Sequence<String>.addToResults() = forEach {
            val element = LookupElementBuilder.create(it).run {
                if (it.endsWith("()")) {
                    // If the attribute name ends in parentheses, put the caret in-between those parentheses.
                    this.withInsertHandler { ctx, _ ->
                        EditorModificationUtil.moveCaretRelatively(ctx.editor, -1)
                    }
                } else this
            }
            result.addElement(element.withPriority(DEFAULT_PRIORITY))
        }

        // Layouts common to struct, enum and union
        if (owner is RsStructItem || owner is RsEnumItem) {
            sequenceOf("C", "transparent", "align()").addToResults()
        }

        // Layouts specific to enum
        if (owner is RsEnumItem) {
            TyInteger.NAMES.asSequence().addToResults()
        }

        // Layouts specific to struct or union
        if (owner is RsStructItem) {
            sequenceOf("packed", "packed()", "simd").addToResults()
        }
    }
}
