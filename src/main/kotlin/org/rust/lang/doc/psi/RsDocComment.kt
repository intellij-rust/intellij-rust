/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.doc.psi

import com.intellij.psi.PsiDocCommentBase
import org.rust.lang.core.psi.ext.RsDocAndAttributeOwner
import org.rust.lang.core.psi.ext.RsElement

/**
 * Psi element for [Rust documentation comments](https://doc.rust-lang.org/reference/comments.html#doc-comments)
 */
interface RsDocComment : PsiDocCommentBase, RsElement {
    override fun getOwner(): RsDocAndAttributeOwner?

    val codeFences: List<RsDocCodeFence>

    val linkDefinitions: List<RsDocLinkDefinition>

    val linkReferenceMap: Map<String, RsDocLinkDefinition>
}
