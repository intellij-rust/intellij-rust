/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsBreakExpr
import org.rust.lang.core.psi.RsContExpr
import org.rust.lang.core.psi.RsLabel

interface RsLabelReferenceOwner : RsElement {
    /**
     * Returns `break` in case of [RsBreakExpr] and `continue` in case of [RsContExpr]
     */
    val operator: PsiElement
    val label: RsLabel?
}
