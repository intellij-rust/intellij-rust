/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.search

import com.intellij.find.findUsages.FindUsagesHandler
import com.intellij.psi.PsiElement

class RsFindUsagesHandler(
    psiElement: PsiElement,
    private val secondaryElements: List<PsiElement>
) : FindUsagesHandler(psiElement) {
    override fun getSecondaryElements(): Array<PsiElement> = secondaryElements.toTypedArray()
}
