/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionService
import com.intellij.codeInsight.completion.CompletionUtil
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.ext.ancestors

fun <T: PsiElement> T.getOriginalOrSelf(): T = CompletionUtil.getOriginalOrSelf(this)

fun <T: PsiElement> T.safeGetOriginalElement(): T? {
    return CompletionUtil.getOriginalElement(this)
        ?.takeIf { areAncestorTypesEquals(it, this) }
}

fun <T: PsiElement> T.safeGetOriginalOrSelf(): T {
    return safeGetOriginalElement() ?: this
}

private fun areAncestorTypesEquals(psi1: PsiElement, psi2: PsiElement): Boolean =
    psi1.ancestors.zip(psi2.ancestors).all { (a, b) -> a.javaClass == b.javaClass }

fun rerunCompletion(parameters: CompletionParameters, result: CompletionResultSet) {
    CompletionService.getCompletionService().getVariantsFromContributors(parameters, null) {
        result.addElement(it.lookupElement)
    }
}
