/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.openapiext

import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.CompositeElement


/**
 * Iterates all children of the `PsiElement` and invokes `action` for each one.
 */
inline fun PsiElement.forEachChild(action: (PsiElement) -> Unit) {
    var psiChild: PsiElement? = firstChild

    while (psiChild != null) {
        if (psiChild.node is CompositeElement) {
            action(psiChild)
        }
        psiChild = psiChild.nextSibling
    }
}
