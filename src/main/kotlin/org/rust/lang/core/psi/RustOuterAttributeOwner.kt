package org.rust.lang.core.psi

import com.intellij.psi.PsiComment
import com.intellij.psi.util.PsiTreeUtil


/**
 * An element with attached outer attributes and documentation comments.
 * Such elements should use left edge binder to properly wrap preceding comments.
 *
 * Fun fact: in Rust, documentation comments are a syntactic sugar for attribute syntax.
 *
 * ```
 * /// docs
 * fn foo() {}
 * ```
 *
 * is equivalent to
 *
 * ```
 * #[doc="docs"]
 * fn foo() {}
 * ```
 */
interface RustOuterAttributeOwner : RustCompositeElement {
    val outerAttrList: List<RustOuterAttr>
}

val RustOuterAttributeOwner.outerDocComments: List<PsiComment>
    get() = PsiTreeUtil.findChildrenOfType(this, PsiComment::class.java).filter {
        it.tokenType == RustTokenElementTypes.OUTER_DOC_COMMENT
    }

