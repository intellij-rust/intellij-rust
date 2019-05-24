/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.codeInsight.completion.CompletionUtil
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.ext.ancestors

fun <T: PsiElement> T.safeGetOriginalOrSelf(): T {
    return CompletionUtil.getOriginalElement(this)
        ?.takeIf { areAncestorTypesEquals(it, this) }
        ?: this
}

private fun areAncestorTypesEquals(psi1: PsiElement, psi2: PsiElement): Boolean =
    psi1.ancestors.zip(psi2.ancestors).all { (a, b) -> a.javaClass == b.javaClass }
