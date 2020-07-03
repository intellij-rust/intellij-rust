/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.docs

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.ext.RsDocAndAttributeOwner
import org.rust.lang.doc.RsDocRenderMode
import org.rust.lang.doc.docElements
import org.rust.lang.doc.documentationAsHtml

class RsDocumentationProvider : RsDocumentationProviderBase() {

    @Suppress("UnstableApiUsage")
    override fun generateRenderedDoc(element: PsiElement): String? {
        if (element !is RsDocAndAttributeOwner) return null
        // Current API doesn't allow determining what comment should be rendered
        // if element have more than one doc comment
        // Fixed in 2020.2
        if (element.docElements().singleOrNull() == null) return null
        return element.documentationAsHtml(renderMode = RsDocRenderMode.INLINE_DOC_COMMENT)
    }
}
