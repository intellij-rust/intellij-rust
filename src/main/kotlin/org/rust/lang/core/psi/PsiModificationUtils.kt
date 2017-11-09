/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi

import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.elementType
import org.rust.lang.core.psi.ext.getNextNonCommentSibling


fun ensureTrailingComma(xs: List<RsElement>) {
    val last = xs.lastOrNull() ?: return
    if (last.getNextNonCommentSibling()?.elementType == RsElementTypes.COMMA) return
    val comma = RsPsiFactory(last.project).createComma()
    last.parent.addAfter(comma, last)
}
