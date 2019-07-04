/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.rust.lang.core.psi.RsMacro
import org.rust.lang.core.psi.RsMacroBinding
import org.rust.lang.core.psi.RsMacroBindingGroup
import org.rust.lang.core.psi.ext.fragmentSpecifier

sealed class Matcher {
    data class Literal(val value: PsiElement) : Matcher()

    data class Fragment(val kind: FragmentKind) : Matcher()

    data class Optional(val matcher: Matcher) : Matcher()

    data class Choice(val matchers: MutableList<Matcher>) : Matcher()

    data class Sequence(val matchers: MutableList<Matcher>) : Matcher()

    data class Repeat(val matchers: MutableList<Matcher>, val separator: PsiElement?) : Matcher()

    class InvalidPatternException : Exception()

    companion object {
        fun buildFor(macro: RsMacro): Matcher? {
            val body = macro.macroBody ?: return null

            val matchers = mutableListOf<Matcher>()

            for (case in body.macroCaseList) {
                val subMatchers = mutableListOf<Matcher>()
                val contents = case.macroPattern.macroPatternContents
                val macroPattern = MacroPattern.valueOf(contents)

                for (psi in macroPattern.pattern) {
                    try {
                        addNewMatcher(psi, subMatchers)
                    } catch (e: InvalidPatternException) {
                        return null
                    }
                }
                if (subMatchers.isEmpty()) {
                    continue
                } else if (subMatchers.size == 1) {
                    matchers.add(subMatchers.single())
                } else {
                    matchers.add(Sequence(subMatchers))
                }
            }

            return Choice(matchers)
        }

        private fun addNewMatcher(psi: PsiElement, matchers: MutableList<Matcher>) {
            when (psi) {
                is LeafPsiElement -> {
                    matchers.add(Literal(psi))
                }
                is RsMacroBinding -> {
                    val specifier = psi.fragmentSpecifier ?: throw InvalidPatternException()
                    val kind = FragmentKind.fromString(specifier) ?: throw InvalidPatternException()
                    val matcher = Fragment(kind)
                    matchers.add(matcher)
                }
                is RsMacroBindingGroup -> {
                    val subMatchers = mutableListOf<Matcher>()
                    val contents = psi.macroPatternContents ?: throw InvalidPatternException()
                    val macroPattern = MacroPattern.valueOf(contents)
                    for (subPsi in macroPattern.pattern) {
                        addNewMatcher(subPsi, subMatchers)
                    }
                    val separator = psi.macroBindingGroupSeparator?.firstChild
                    val matcher = when {
                        psi.mul != null -> Optional(Repeat(subMatchers, separator))
                        psi.plus != null -> Repeat(subMatchers, separator) // at least once
                        psi.q != null -> Optional(Sequence(subMatchers)) // at most once
                        else -> throw InvalidPatternException()
                    }
                    matchers.add(matcher)
                }
                else -> throw InvalidPatternException()
            }
        }
    }
}
