/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.patterns.PsiElementPattern
import com.intellij.psi.PsiElement

inline fun <reified I : PsiElement> psiElement(): PsiElementPattern.Capture<I> {
    return psiElement(I::class.java)
}

inline fun <reified I : PsiElement> PsiElementPattern.Capture<PsiElement>.withSuperParent(level: Int): PsiElementPattern.Capture<PsiElement> {
    return this.withSuperParent(level, I::class.java)
}

infix inline fun <reified I : PsiElement> ElementPattern<I>.or(pattern: ElementPattern<I>): PsiElementPattern.Capture<PsiElement> {
    return psiElement().andOr(this, pattern)
}

