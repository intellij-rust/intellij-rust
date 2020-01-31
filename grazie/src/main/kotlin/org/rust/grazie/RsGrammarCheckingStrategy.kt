/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.grazie

import com.intellij.grazie.grammar.strategy.GrammarCheckingStrategy
import com.intellij.grazie.grammar.strategy.StrategyUtils
import com.intellij.grazie.grammar.strategy.impl.RuleGroup
import com.intellij.grazie.utils.LinkedSet
import com.intellij.psi.PsiElement
import org.rust.ide.injected.findDoctestInjectableRanges
import org.rust.lang.core.psi.RsDocCommentImpl
import org.rust.lang.core.psi.RsLitExpr
import org.rust.lang.core.psi.ext.stubKind
import org.rust.lang.core.stubs.RsStubLiteralKind

class RsGrammarCheckingStrategy : GrammarCheckingStrategy {
    override fun isMyContextRoot(element: PsiElement): Boolean =
        element is RsDocCommentImpl || element is RsLitExpr && element.stubKind is RsStubLiteralKind.String

    override fun isTypoAccepted(root: PsiElement, typoRange: IntRange, ruleRange: IntRange): Boolean {
        if (root !is RsDocCommentImpl) return true

        return findDoctestInjectableRanges(root)
            .flatten()
            .none { it.intersects(typoRange.first, typoRange.last) }
    }

    override fun getIgnoredRuleGroup(root: PsiElement, child: PsiElement): RuleGroup? = RuleGroup.LITERALS

    override fun getStealthyRanges(root: PsiElement, text: CharSequence): LinkedSet<IntRange> =
        StrategyUtils.indentIndexes(text, setOf(' ', '/', '!'))
}
