/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsPatConst
import org.rust.lang.core.psi.RsPatRange

val RsPatRange.isInclusive: Boolean
    get() = dotdotdot != null || dotdoteq != null

val RsPatRange.op: PsiElement?
    get() = dotdot ?: dotdotdot ?: dotdoteq

val RsPatRange.start: RsPatConst?
    get() {
        val op = op ?: return null
        return patConstList.firstOrNull()?.takeIf { it.endOffset <= op.startOffset }
    }

val RsPatRange.end: RsPatConst?
    get() {
        val op = op ?: return null
        return patConstList.lastOrNull()?.takeIf { it.startOffset >= op.endOffset }
    }
