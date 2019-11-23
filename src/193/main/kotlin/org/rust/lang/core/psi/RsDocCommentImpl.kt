/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi

import com.intellij.lang.psi.SimpleMultiLineTextEscaper
import com.intellij.psi.LiteralTextEscaper
import com.intellij.psi.impl.source.tree.PsiCommentImpl
import com.intellij.psi.tree.IElementType

class RsDocCommentImpl(type: IElementType, text: CharSequence) : RsDocCommentImplBase(type, text) {
    override fun createLiteralTextEscaper(): LiteralTextEscaper<PsiCommentImpl> =
        SimpleMultiLineTextEscaper(this)
}
