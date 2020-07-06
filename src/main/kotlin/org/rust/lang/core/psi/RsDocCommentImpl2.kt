/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi

import com.intellij.psi.PsiComment
import com.intellij.psi.impl.source.tree.LazyParseablePsiElement
import com.intellij.psi.tree.IElementType

class RsDocCommentImpl2(type: IElementType, text: CharSequence) : LazyParseablePsiElement(type, text), PsiComment {
    override fun getTokenType(): IElementType {
        return elementType
    }


}
