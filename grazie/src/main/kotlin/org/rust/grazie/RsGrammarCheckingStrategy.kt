/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

// BACKCOMPAT: 2021.1
@file:Suppress("DEPRECATION")

package org.rust.grazie

import com.intellij.grazie.grammar.strategy.GrammarCheckingStrategy
import com.intellij.grazie.grammar.strategy.GrammarCheckingStrategy.TextDomain
import com.intellij.grazie.grammar.strategy.StrategyUtils
import com.intellij.grazie.grammar.strategy.impl.RuleGroup
import com.intellij.grazie.utils.LinkedSet
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.elementTypeOrNull
import org.rust.lang.doc.psi.RsDocComment

class RsGrammarCheckingStrategy : GrammarCheckingStrategy {

    override fun isMyContextRoot(element: PsiElement): Boolean =
        getContextRootTextDomain(element) != TextDomain.NON_TEXT

    override fun isTypoAccepted(
        parent: PsiElement,
        roots: List<PsiElement>,
        typoRange: IntRange,
        ruleRange: IntRange
    ): Boolean {
        val docCommentRoots = roots.filterIsInstance<RsDocComment>()
        if (docCommentRoots.isEmpty()) return true

        val injectedLanguageManager = InjectedLanguageManager.getInstance(parent.project)
        return docCommentRoots.flatMap { it.codeFences }.none {
            it.textRange.intersects(typoRange.first, typoRange.last) &&
                !injectedLanguageManager.getInjectedPsiFiles(it).isNullOrEmpty()
        }
    }

    override fun getIgnoredRuleGroup(root: PsiElement, child: PsiElement): RuleGroup = RuleGroup.LITERALS

    override fun getStealthyRanges(root: PsiElement, text: CharSequence): LinkedSet<IntRange> {
        val parent = root.parent
        return if (parent is RsLitExpr) {
            val valueTextRange = (parent.kind as? RsLiteralKind.String)?.offsets?.value ?: return linkedSetOf()
            linkedSetOf(0 until valueTextRange.startOffset, valueTextRange.endOffset until text.length)
        } else {
            StrategyUtils.indentIndexes(text, setOf(' ', '/', '!'))
        }
    }

    override fun getContextRootTextDomain(root: PsiElement): TextDomain {
        return when (root.elementTypeOrNull) {
            in RS_ALL_STRING_LITERALS -> TextDomain.LITERALS
            in RS_DOC_COMMENTS -> TextDomain.DOCS
            in RS_REGULAR_COMMENTS -> TextDomain.COMMENTS
            else -> TextDomain.NON_TEXT
        }
    }

    override fun getRootsChain(root: PsiElement): List<PsiElement> {
        return if (root.elementTypeOrNull in RS_REGULAR_COMMENTS) {
            StrategyUtils.getNotSoDistantSiblingsOfTypes(this, root, RS_REGULAR_COMMENTS_SET).toList()
        } else {
            super.getRootsChain(root)
        }
    }

    companion object {
        private val RS_REGULAR_COMMENTS_SET = RS_REGULAR_COMMENTS.types.toSet()
    }
}
