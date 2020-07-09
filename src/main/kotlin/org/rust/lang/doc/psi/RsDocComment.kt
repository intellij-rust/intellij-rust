/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.doc.psi

import com.intellij.psi.PsiComment
import org.rust.lang.core.psi.ext.RsDocAndAttributeOwner

/**
 * Psi element for [Rust documentation comments](https://doc.rust-lang.org/reference/comments.html#doc-comments)
 */
interface RsDocComment : PsiComment {
    val owner: RsDocAndAttributeOwner?
        get() = parent as? RsDocAndAttributeOwner

    val codeFences: List<RsDocCodeFence>

    val linkDefinitions: List<RsDocLinkReferenceDef>

    val linkReferenceMap: Map<String, RsDocLinkReferenceDef>
}
