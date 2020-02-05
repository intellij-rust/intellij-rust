/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core

import com.intellij.lang.DefaultASTFactoryImpl
import com.intellij.psi.impl.source.tree.LeafElement
import com.intellij.psi.impl.source.tree.PsiCommentImpl
import com.intellij.psi.tree.IElementType
import org.rust.lang.core.psi.RS_DOC_COMMENTS
import org.rust.lang.core.psi.RsDocCommentImpl

class RsASTFactory : DefaultASTFactoryImpl() {
    override fun createComment(type: IElementType, text: CharSequence): LeafElement {
        return if (type in RS_DOC_COMMENTS) RsDocCommentImpl(type, text) else PsiCommentImpl(type, text)
    }
}

