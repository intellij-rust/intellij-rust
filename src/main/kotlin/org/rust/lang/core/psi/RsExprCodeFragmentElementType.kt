/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi

import com.intellij.psi.tree.IElementType

class RsExprCodeFragmentElementType : RsCodeFragmentElementTypeBase("RS_EXPR_CODE_FRAGMENT") {
    override val elementType: IElementType
        get() = RsElementTypes.EXPR_CODE_FRAGMENT
}
