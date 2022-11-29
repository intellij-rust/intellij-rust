/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.template.impl.LiveTemplateCompletionContributor
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
    // BACKCOMPAT 2022.3: `LiveTemplateCompletionContributor` throws an exception on 2022.3 when invoked
    //  with a light virtual file. See https://github.com/intellij-rust/intellij-rust/issues/9822
    //  Here we exclude `LiveTemplateCompletionContributor` from completion. This approach works since
    //  `LiveTemplateCompletionContributor` is the first in the completion list
    val liveTemplateContributor = CompletionContributor.forParameters(parameters)
        .firstOrNull { it is LiveTemplateCompletionContributor }

    CompletionService.getCompletionService().getVariantsFromContributors(parameters, liveTemplateContributor) {
        result.addElement(it.lookupElement)
    }
}
