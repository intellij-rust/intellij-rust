/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.psi.PsiElement
import com.intellij.psi.util.findDescendantOfType

inline fun <reified T : PsiElement> PsiElement.descendantOfType(noinline predicate: (T) -> Boolean): T? {
    return findDescendantOfType({true}, predicate)
}
