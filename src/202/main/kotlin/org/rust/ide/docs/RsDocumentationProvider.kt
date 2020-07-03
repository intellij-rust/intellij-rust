/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.docs

import com.intellij.psi.PsiDocCommentBase
import org.rust.lang.core.psi.RsDocCommentImpl
import org.rust.lang.doc.RsDocRenderMode
import org.rust.lang.doc.documentationAsHtml

// BACKCOMPAT: 2020.1. Merge with `RsDocumentationProviderBase`
class RsDocumentationProvider : RsDocumentationProviderBase() {

    @Suppress("UnstableApiUsage")
    override fun generateRenderedDoc(comment: PsiDocCommentBase): String? {
        return (comment as? RsDocCommentImpl)?.documentationAsHtml(renderMode = RsDocRenderMode.INLINE_DOC_COMMENT)
    }
}
