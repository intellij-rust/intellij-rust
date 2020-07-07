/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.doc.psi.impl

import com.intellij.psi.impl.source.tree.LazyParseablePsiElement
import com.intellij.psi.tree.IElementType
import org.rust.lang.core.psi.ext.childrenOfType
import org.rust.lang.doc.psi.RsDocCodeFence
import org.rust.lang.doc.psi.RsDocComment

class RsDocCommentImpl(type: IElementType, text: CharSequence) : LazyParseablePsiElement(type, text), RsDocComment {
    override fun getTokenType(): IElementType = elementType

    override val codeFences: List<RsDocCodeFence>
        get() = childrenOfType()
}
