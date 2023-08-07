/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.ElementPattern
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import org.rust.lang.core.psi.RsLitExpr
import org.rust.lang.core.psi.RsLiteralKind
import org.rust.lang.core.psi.kind
import org.rust.lang.core.psiElement
import org.rust.lang.core.types.ty.TyFloat
import org.rust.lang.core.types.ty.TyInteger
import org.rust.lang.core.with

object RsLiteralSuffixCompletionProvider : RsCompletionProvider() {

    override val elementPattern: ElementPattern<PsiElement>
        get() = psiElement<PsiElement>()
            .withParent(psiElement<RsLitExpr>().with("isLiteralNumberWithoutExistingSuffix") { psi ->
                val suffix = when (val kind = psi.getOriginalOrSelf().kind) {
                    is RsLiteralKind.Integer -> kind.suffix
                    is RsLiteralKind.Float -> kind.suffix
                    else -> null
                } ?: return@with false
                !(TyInteger.NAMES + TyFloat.NAMES).any { suffix.contains(it) }
            })

    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        val literal = parameters.position.getOriginalOrSelf().text
        getValidSuffixes(literal).forEach { suffix ->
            tryToCompleteSuffix(suffix, literal, result)
        }
    }

    private fun getValidSuffixes(literal: String): List<String> {
        if (literal.contains(".") || literal.contains("e")) {
            return TyFloat.NAMES
        }
        if (literal.startsWith("0b") || literal.startsWith("0o")) {
            return TyInteger.NAMES
        }

        return TyInteger.NAMES + TyFloat.NAMES
    }

    /**
     * Consider `1i3/*caret*/` case
     * `i32` is a suffix we want to complete
     * We need to find a common part (`i3` in this case) and remove it from the suffix, otherwise we will get `1i3i32`
     */
    private fun tryToCompleteSuffix(suffix: String, literal: String, result: CompletionResultSet) {
        for (suffixPrefix in suffix.allNonEmptyPrefixes()) {
            if (literal.endsWith(suffixPrefix)) {
                result.addElement(LookupElementBuilder.create(literal + suffix.removePrefix(suffixPrefix)))
                break
            }
        }
    }


}

private fun String.allNonEmptyPrefixes(): Sequence<String> = sequence {
    val currentPrefix = StringBuilder()

    forEach { c ->
        currentPrefix.append(c)
        yield(currentPrefix.toString())
    }
}
