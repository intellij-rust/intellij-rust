/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.patterns.PsiElementPattern
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import org.rust.ide.icons.RsIcons
import org.rust.lang.core.psi.RsElementTypes
import org.rust.lang.core.psi.RsPath
import org.rust.lang.core.psi.RsVisRestriction
import org.rust.lang.core.psi.ext.qualifier
import org.rust.lang.core.psiElement
import org.rust.lang.core.with

/**
 * Provides completion inside visibility restriction:
 * `pub(<here>)`
 */
object RsVisRestrictionCompletionProvider : RsCompletionProvider() {
    override val elementPattern: PsiElementPattern.Capture<PsiElement>
        get() = psiElement(RsElementTypes.IDENTIFIER)
            .withParent(
                psiElement<RsPath>()
                    .with("hasOneSegment") { item, _ ->
                        item.qualifier == null && item.typeQual == null && !item.hasColonColon
                    }
            )
            .withSuperParent(2,
                psiElement<RsVisRestriction>()
                    .with("hasNoIn") { item, _ -> item.`in` == null }
            )

    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        for (name in listOf("crate", "super", "self")) {
            result.addElement(
                LookupElementBuilder
                    .create(name)
                    .withIcon(RsIcons.MODULE)
                    .bold()
                    .toKeywordElement()
            )
        }
        result.addElement(LookupElementBuilder.create("in ").withPresentableText("in"))
    }
}
